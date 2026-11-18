# Explanation: questions and answers

Questions asked while working on this framework, with the answers.

---

## Q1. When the debugger reaches `private final Map<DataKey<?>, Object> store = new HashMap<>();`, why does it then jump to `public TestInitializeHooks(TestSession session)`?

This is normal Java behaviour combined with Cucumber's dependency injection (PicoContainer).

### Field initializers belong to the constructor

`TestSession` (`src/main/java/com/agenticqa/core/TestSession.java`) has no constructor written in it, so Java
creates a default one. The compiler moves every field initializer into that constructor, in the order the fields
appear in the file. In effect it becomes:

```java
public TestSession() {
    super();
    this.settings = EnvConfig.get();          // line 40
    this.store    = new HashMap<>();          // line 41
    this.pageComponents = new HashMap<>();    // line 42
    // context, page, healer have no initializer, so they stay null
    this.scenarioName = "scenario";           // line 47
}
```

So when the debugger stops on lines 40-47, you are actually inside `new TestSession()`.

### PicoContainer builds `TestSession` because `TestInitializeHooks` needs it

`TestInitializeHooks` asks for a `TestSession` in its constructor:

```java
public TestInitializeHooks(TestSession session) {    // TestInitializeHooks.java:27
    this.session = session;
}
```

To create `TestInitializeHooks`, PicoContainer has to create a `TestSession` first:

1. It needs a `TestInitializeHooks` object and sees that the constructor takes a `TestSession`.
2. It creates the `TestSession` with `new TestSession()`, which runs the field initializers (lines 40 → 42 → 47).
3. That constructor returns, and PicoContainer calls `new TestInitializeHooks(session)` with the new object. The debugger then
   lands on `TestInitializeHooks.java:27`.

So the jump from line 42 to the `TestInitializeHooks` constructor means `TestSession` is fully built and is now being handed to
`TestInitializeHooks`. (Stepping once more from line 42 may first show line 47, `scenarioName = "scenario"`.)

---

## Q2. How does PicoContainer create the step definition classes?

### Where it comes from

The `cucumber-picocontainer` dependency is in `pom.xml`. When Cucumber finds it on the classpath, it uses
PicoContainer as its *object factory*, which means PicoContainer creates every glue class. You never write
`new CustomerApiSteps(...)` yourself.

### Step 1: finding the glue classes (once per run)

`TestRunner` points Cucumber at the glue packages:

```java
glue = {"com.agenticqa.steps", "com.agenticqa.hooks"}
```

Cucumber scans those packages for classes that have step or hook annotations (`@Given`, `@When`, `@Then`,
`@Before`, `@After`) and **registers** them with the object factory. Here these are:

| Glue class             | Why it is glue           |
|------------------------|--------------------------|
| `TestInitializeHooks`                | `@Before`, `@After`      |
| `CustomerApiSteps`     | `@Given/@When/@Then`     |
| `CustomerUiSteps`      | `@Given/@When/@Then`     |
| `HomeSteps`            | `@When`                  |
| `ZoneSteps`            | `@Given/@When/@Then`     |
| `ServiceCleanup`       | `@After(order = 2)`      |

When a class is registered, Cucumber also looks at its constructor parameters and registers those types as well,
and then their parameter types, and so on. That is how `TestSession` and `Variables` become known to
PicoContainer even though they are not glue classes. `Keys` has no annotations and is not needed by any
constructor, so it is not involved.

Registering only records the class. No objects are created yet.

### Step 2: a new container for every scenario

When a scenario starts, Cucumber creates a **new, empty PicoContainer** for it. When the scenario ends, the
container is thrown away along with every object it made. The next scenario gets a new container and new objects.

Inside one container, PicoContainer **caches** each object: it creates at most one instance of each class per
scenario and gives that same instance to everyone who asks for it.

### Step 3: creating an object (constructor injection)

When Cucumber needs to run a hook or step, it asks the container for an instance of the class that defines it.
Objects are created lazily, the first time the scenario needs them. PicoContainer then:

1. Looks at the class's public constructor.
2. Gets an object for each parameter. If it already made that object in this scenario, it reuses it. Otherwise it
   creates it first, applying the same rule to that class's constructor parameters.
3. Calls the constructor with those objects.

### Worked example: `CustomerApiSteps`

```java
public CustomerApiSteps(TestSession session, ServiceCleanup cleanup)
public ServiceCleanup()
public TestInitializeHooks(TestSession session)
```

Within one scenario:

```
Scenario starts → new empty container
│
├─ @Before TestInitializeHooks.bindSession() needs a TestInitializeHooks
│    └─ TestInitializeHooks(TestSession) needs a TestSession
│         └─ new TestSession()                  ← created (field initializers run)
│    └─ new TestInitializeHooks(session)                       ← created
│
├─ step "I create a customer named {string}" needs a CustomerApiSteps
│    ├─ TestSession    → already in container    ← reused, not created again
│    ├─ ServiceCleanup → new ServiceCleanup()    ← created
│    └─ new CustomerApiSteps(session, cleanup)
│
├─ later steps in CustomerApiSteps → same CustomerApiSteps instance reused
├─ a GUI step needs a CustomerUiSteps → new CustomerUiSteps(session, cleanup), same session and cleanup
│
├─ @After hooks run (evidence and closeBrowser first, then ServiceCleanup order 2, then teardownData)
│
Scenario ends → container and all its objects are discarded
```

The API classes themselves are not created by PicoContainer: steps get them with `ApiFactory.getApi(...)`,
which keeps one instance of each for the whole run (see [api.md](api.md)).

### Why this matters: sharing state between step classes

`TestInitializeHooks`, `CustomerApiSteps`, `CustomerUiSteps` and the others all receive **the same `TestSession` object**
during a scenario. That is how they share things without static fields:

- the browser `page` opened by a UI step is the one `TestInitializeHooks.closeBrowser()` screenshots on failure;
- data one step stores is the data the next step reads, API or GUI: the customer an API step created is the one
  the GUI step searches for.

In the same way, `CustomerApiSteps` and `CustomerUiSteps` share one `ServiceCleanup`, so every customer created
in the scenario is deleted at the end, whichever step class created it.

### Parallel runs

`TestRunner` runs scenarios in parallel (`@DataProvider(parallel = true)`). Each scenario gets its own container,
so each has its own `TestSession`, browser context and data. Parallel scenarios never share objects.

### Rules for classes PicoContainer creates

- Give the class **one public constructor**. Its parameters are what gets injected.
- Parameters must be **concrete classes** that PicoContainer can create in turn. It cannot invent a `String`, an
  `int`, an interface or an abstract class unless something is registered for it.
- **No cycles**: if `A` needs `B` and `B` needs `A`, creation fails.
- Do not call `new` on step classes yourself. You would get a separate object that does not share the
  scenario's state.
- Clean-up is not automatic. Throwing the container away does not close a browser, so `TestInitializeHooks.closeBrowser()` calls
  `session.close(failed)` explicitly.

---

## Q3. What does `TestSession` do?

`TestSession` (`src/main/java/com/agenticqa/core/TestSession.java`) holds **everything one scenario needs while it
runs**. PicoContainer creates one `TestSession` per scenario (see Q2) and gives that same object to every step
class and hook that asks for it in its constructor. When the scenario ends, it is thrown away.

### What it holds

| Field          | What it is                                                    | Created                              |
|----------------|---------------------------------------------------------------|--------------------------------------|
| `settings`     | Settings of the active environment (`-Denv=dev/test/uat/ci`)  | when the session is created          |
| `store`        | A map of data shared between steps (see Q4)                   | when the session is created          |
| `context`      | The Playwright browser context, with tracing switched on      | lazily, on first `page()`            |
| `page`         | The browser page                                              | lazily, on first `page()`            |
| `healer`       | The self-healing locator engine                               | lazily, on first `healer()`          |
| `scenarioName` | The scenario name, used to name the trace file                | set by `TestInitializeHooks.bindSession()`              |

### What it does

- **Browser access.** `page()` returns the scenario's browser page. API calls do not go through the session:
  the API classes call their services directly with REST Assured (see [api.md](api.md)).
- **Lazy start.** The browser context is only opened the first time a step calls `page()`. An API-only scenario
  never opens one. `hasPage()` lets `TestInitializeHooks.closeBrowser()` check whether there is a page to
  screenshot without opening a new one.
- **One browser per thread.** The context is opened in the current thread's browser (`BrowserPool`). Closing the
  session closes only the context. The browser stays open for the next scenario on that thread, which is faster.
- **Self-healing.** `healer()` returns the scenario's healer, and `healEvents()` lists the locators it healed, so
  `TestInitializeHooks.closeBrowser()` can report them.
- **Data store.** `put`, `get` and `has` share typed values between steps (see Q4).
- **Clean-up.** `close(failed)` stops tracing and closes the context. The trace zip is saved under
  `target/artifacts/traces/` only when the scenario failed. Each close is attempted separately, so a clean-up
  error never hides the real test result.

### Its life in one scenario

```
PicoContainer:  new TestSession()        settings and store created
TestInitializeHooks.bindSession(): scenarioName(...)        name remembered for the trace file; bound to the thread
steps:          page()                   browser context opened on first use
                put(...) / get(...)      data shared between steps, API and GUI
TestInitializeHooks.closeBrowser():  healEvents(), screenshot if failed
                close(failed)            tracing stopped, context closed
PicoContainer:  container discarded      TestSession thrown away
```

### Why parallel scenarios don't interfere

Every scenario gets its own `TestSession`, and nothing in it is static or shared. Parallel scenarios therefore
have separate pages and data. They share only the browser of their worker thread, and each has its own
context inside that browser.

---

## Q4. How do we share data across different steps?

Steps in a scenario often live in **different classes**. For example, `customers_page.feature`:

```gherkin
Given a customer named "Ada Lovelace" exists                       # CustomerApiSteps
When I search the customers page for that customer                 # CustomerUiSteps
Then the results show only that customer with status "active"      # CustomerUiSteps
```

The first step creates the customer through the API. The next steps, in another class, need to know which customer
was created. PicoContainer gives both classes **the same objects** for the scenario, so whatever one class stores,
the other can read. The framework has three ways to share data.

### 1. The `TestSession` store with typed keys (between step classes)

Keys are declared once as constants in `src/test/java/com/agenticqa/steps/Keys.java`:

```java
public static final DataKey<Customer> CUSTOMER = DataKey.of("customer", Customer.class);   // apis.model.Customer
public static final DataKey<Response> RESPONSE = DataKey.of("lastResponse", Response.class);  // REST Assured
public static final DataKey<String>   NAME_TAG = DataKey.of("nameTag", String.class);
```

One step stores a value (`CustomerApiSteps`):

```java
@Given("a customer named {string} exists")
public void createCustomer(String name) {
    ...
    session.put(RESPONSE, response);
    session.put(CUSTOMER, customer);
}
```

A later step in another class reads it (`CustomerUiSteps`):

```java
@When("I search the customers page for that customer")
public void searchForCustomer() {
    page().open().search(session.get(CUSTOMER).getName());
}
```

Why typed keys (`DataKey<T>`) and not plain strings:

- **The compiler checks the type.** `session.get(CUSTOMER)` returns a `Customer`, so no cast is needed, and
  `session.put(CUSTOMER, "text")` does not compile.
- **No typos.** Every class uses the same constant, so one class can't write `"customer"` while another reads
  `"Customer"`.
- **Clear errors.** If nothing was stored, `get` throws
  `Nothing stored under key 'customer' in this scenario. Did an earlier step run?` instead of returning `null`.
  Use `has(key)` to check first when a value is optional.

### 2. `Variables`: named values used in feature files

`Variables` (`src/main/java/com/agenticqa/core/Variables.java`) holds string values that the **feature file**
refers to as `${name}`. For example, `zone_id.feature`:

```gherkin
And I save the zone id as "firstZone"
...
And the zone id is not "${firstZone}"
```

The first step saves a value under `firstZone`. Later steps write `${firstZone}` and it is replaced with the value.
The same object also replaces `{unique}` with one random token per scenario, so test data never collides between
parallel scenarios.

Use `Variables` when the **feature file author** decides what is saved and where it is used. Use the
`TestSession` store when **Java code** passes an object to other steps.

### 3. Shared helper objects

Any class that a step constructor asks for is shared in the same way. For example, `CustomerApiSteps` and
`CustomerUiSteps` both receive the same `ServiceCleanup`. Each one registers a delete when it creates a
customer, `cleanup.add("customer " + id, () -> ApiFactory.getApi(CustomerApi.class).deleteCustomer(id))`, and the
single `@After` hook in `ServiceCleanup` runs all of them at the end of the scenario.

### Fields inside one step class

Within a single step class you could also keep data in ordinary instance fields, because PicoContainer reuses the
same instance for the whole scenario. Other classes can't see those fields, though, so data that more than one
class needs belongs in one of the shared objects above.

### What not to do

- **Don't use `static` fields** to pass data between steps. Static fields are shared by every scenario, including
  parallel ones, so scenarios overwrite each other's data and leftover values leak into the next scenario.
- **Don't create these objects with `new`.** A `new TestSession()` or `new Variables()` is a separate, empty
  object. Always receive them through the constructor so you get the scenario's shared one.

### Adding a new shared value

1. Add a key to `Keys.java`, for example
   `public static final DataKey<String> ORDER_ID = DataKey.of("orderId", String.class);`
2. Make sure each step class that needs it takes `TestSession` in its constructor.
3. Store it with `session.put(Keys.ORDER_ID, id)` and read it with `session.get(Keys.ORDER_ID)`.

---

## Q5. Can we change the name of `TestSession` to something else?

Yes, you can give it any name. Nothing in Cucumber or PicoContainer depends on the name `TestSession`.

### Why the name doesn't matter

PicoContainer works out what to inject from the parameter **type** in a constructor, not from the name of the class
or the parameter. If the class were renamed to `ScenarioContext`, this would work just like before:

```java
public TestInitializeHooks(ScenarioContext session) { ... }
```

PicoContainer would still create one `ScenarioContext` per scenario and give it to every class that asks for one.
The only requirements are the ones it already meets: a concrete class with a public constructor that PicoContainer
can call.

### Names commonly used for this kind of class

`ScenarioContext`, `World` (the classic Cucumber name), `TestContext`, `ScenarioState`, `SharedContext`.

### What a rename involves

The name appears in a dozen files:

- **Java (must change or it won't compile):** `TestSession.java` itself (the file name has to match the class),
  `TestInitializeHooks`, the step classes (`CustomerApiSteps`, `CustomerUiSteps`, `ZoneSteps`), `PageComponentFactory` and the
  unit tests that use it.
- **Docs and comments (won't break anything if missed, but would be out of date):** `README.md`, the files in
  `docs/` (including this one), and comments in two feature files.

### How to do it safely

1. In IntelliJ, put the cursor on the class name and use **Refactor → Rename** (Shift+F6). It renames the file,
   updates every Java reference, and offers to update comments and text too.
2. Run `mvn test-compile` to confirm everything still compiles.

You can also rename the field and parameter, which are called `session` everywhere, but that's optional. Code
compiles fine with `ScenarioContext session`.

---

## Q6. If I have a new API endpoint, do I need to create another class like `CustomerApi`?

**An endpoint of a service that already has an API class:** no, add a method to that class, in the same style
as the others (REST Assured call, `Assert.fail` on an unexpected status, return the `Response`):

```java
public Response searchCustomers(String name) {
    Response response = RestAssured.given()
            .header("Accept", "*/*")
            .queryParam("name", name)
            .get(baseUrl + customersPath + "/search");
    if (response.getStatusCode() != 200) {
        Assert.fail("Error searching Customers. Status code: " + response.getStatusCode()
                + " : " + response.getBody().asString());
    }
    return response;
}
```

**A new service:** yes, one API class per service, plus model classes for its bodies:

1. Add its `baseUrl` under `services:` in each environment file.
2. Add `XxxApi implements Api` in `src/test/java/com/agenticqa/apis/` with a no-argument constructor that reads
   `Config.getApiUrl("<service>")`.
3. Add its request and response models in `apis/model/`.
4. Call it from steps with `ApiFactory.getApi(XxxApi.class)`.

The full description is in [api.md](api.md).

## TestSession.current(): the session from static code

PicoContainer still creates one `TestSession` per scenario and injects it. `TestInitializeHooks.bindSession` (the first `@Before`
hook, `order = 1`) binds that same object to the scenario's thread, so code that Pico does not create can reach it:

```java
TestSession session = TestSession.current();   // the running scenario's session
Page page = TestSession.currentPage();          // its browser page, opened on first use
HomePage home = PageComponentFactory.get(HomePage.class);
```

There is exactly **one** ThreadLocal, the pointer to the session. Per-scenario data is not kept in separate
static ThreadLocals; it lives in the session, so it is dropped with the session and nothing has to be cleared.
`TestSession.close` (called by `TestInitializeHooks.closeBrowser`, pass or fail) removes the pointer.

Lists that steps add to during a scenario use a list key and the list helpers:

```java
// Keys.java
public static final DataKey<List<InstMan>> CREATED_INST_MAN = DataKey.listOf("createdInstMan", InstMan.class);
public static final DataKey<List<Unit.UnitKL>> UNIT_KL = DataKey.listOf("unitKL", Unit.UnitKL.class);

// any step or helper
TestSession.current().add(CREATED_INST_MAN, instMan);                    // fails clearly on null
List<InstMan> created = TestSession.current().requireList(CREATED_INST_MAN); // fails clearly if empty
List<Unit.UnitKL> units = TestSession.current().list(UNIT_KL);           // empty list if none
```

`current()` fails with a clear message outside a scenario, before `TestInitializeHooks.bindSession`, and on another thread
(`CompletableFuture`, parallel streams): a session belongs to the thread that runs its scenario.

## API classes in the existing framework's style (`Api`, `ApiFactory`)

The API part keeps the existing framework's shape: API classes with a no-argument constructor, base URL from
`Config.getApiUrl(name)`, REST Assured calls, created once for the whole run by `ApiFactory.getApi(...)`. Only the
GUI part uses the session setup above. API and GUI steps share data through the `TestSession` store. See
[api.md](api.md).
