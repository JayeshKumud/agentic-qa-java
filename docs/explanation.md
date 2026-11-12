# Explanation: questions and answers

Questions asked while working on this framework, with the answers.

---

## Q1. When the debugger reaches `private final List<ApiCall> apiCalls = new ArrayList<>();`, why does it then jump to `public Hooks(TestSession session)`?

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
    this.apiCalls = new ArrayList<>();        // line 42
    // context, page, healer have no initializer, so they stay null
    this.scenarioName = "scenario";           // line 47
}
```

So when the debugger stops on lines 40-47, you are actually inside `new TestSession()`.

### PicoContainer builds `TestSession` because `Hooks` needs it

`Hooks` asks for a `TestSession` in its constructor:

```java
public Hooks(TestSession session) {    // Hooks.java:27
    this.session = session;
}
```

To create `Hooks`, PicoContainer has to create a `TestSession` first:

1. It needs a `Hooks` object and sees that the constructor takes a `TestSession`.
2. It creates the `TestSession` with `new TestSession()`, which runs the field initializers (lines 40 → 42 → 47).
3. That constructor returns, and PicoContainer calls `new Hooks(session)` with the new object. The debugger then
   lands on `Hooks.java:27`.

So the jump from line 42 to the `Hooks` constructor means `TestSession` is fully built and is now being handed to
`Hooks`. (Stepping once more from line 42 may first show line 47, `scenarioName = "scenario"`.)

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
| `Hooks`                | `@Before`, `@After`      |
| `CustomerApiSteps`     | `@Given/@When/@Then`     |
| `CustomerRequestSteps` | `@Given/@When/@Then`     |
| `CustomerUiSteps`      | `@Given/@When/@Then`     |
| `HttpSteps`            | `@Given/@When/@Then`     |
| `CustomerCleanup`      | `@After(order = 20000)`  |
| `ServiceCleanup`       | `@After(order = 20000)`  |

When a class is registered, Cucumber also looks at its constructor parameters and registers those types as well,
and then their parameter types, and so on. That is how `TestSession`, `ApiState` and `Variables` become known to
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

### Worked example: `HttpSteps`

```java
public HttpSteps(TestSession session, ApiState api, Variables vars, ServiceCleanup cleanup)
public ServiceCleanup(TestSession session)
public Hooks(TestSession session)
```

Within one scenario:

```
Scenario starts → new empty container
│
├─ @Before Hooks.before() needs a Hooks
│    └─ Hooks(TestSession) needs a TestSession
│         └─ new TestSession()                  ← created (field initializers run)
│    └─ new Hooks(session)                       ← created
│
├─ step "a new {string} request from {string}" needs an HttpSteps
│    ├─ TestSession  → already in container      ← reused, not created again
│    ├─ ApiState     → new ApiState()            ← created
│    ├─ Variables    → new Variables()           ← created
│    ├─ ServiceCleanup(TestSession)
│    │    └─ TestSession → already in container  ← reused
│    │    new ServiceCleanup(session)            ← created
│    └─ new HttpSteps(session, api, vars, cleanup)
│
├─ later steps in HttpSteps → same HttpSteps instance reused
│
├─ @After hooks run (ServiceCleanup order 20000 runs before Hooks.after)
│
Scenario ends → container and all its objects are discarded
```

### Why this matters: sharing state between step classes

`Hooks`, `HttpSteps`, `CustomerApiSteps`, `ServiceCleanup` and the others all receive **the same `TestSession`
object** during a scenario. That is how they share things without static fields:

- the browser `page` opened by a UI step is the one `Hooks.after()` screenshots on failure;
- the `apiCalls` list filled by API steps is the one `Hooks.after()` attaches to the report;
- data one step stores is the data the next step reads.

In the same way, `CustomerApiSteps`, `CustomerRequestSteps` and `CustomerUiSteps` share one `CustomerCleanup`, so
every customer created in the scenario is deleted at the end, whichever step class created it.

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
- Clean-up is not automatic. Throwing the container away does not close a browser, so `Hooks.after()` calls
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
| `apiCalls`     | A log of every HTTP call made in the scenario                 | when the session is created          |
| `context`      | The Playwright browser context, with tracing switched on      | lazily, on first `page()` or `api()` |
| `page`         | The browser page                                              | lazily, on first `page()`            |
| `healer`       | The self-healing locator engine                               | lazily, on first `healer()`          |
| `scenarioName` | The scenario name, used to name the trace file                | set by `Hooks.before()`              |

### What it does

- **Browser and API access.** `page()` returns the scenario's browser page and `api()` returns Playwright's API
  request context. Both come from the same browser context, so API calls and the browser share cookies and login
  state.
- **Lazy start.** The browser context is only opened the first time a step calls `page()` or `api()`. A scenario
  that never needs one never opens one. `hasPage()` lets `Hooks.after()` check whether there is a page to
  screenshot without opening a new one.
- **One browser per thread.** The context is opened in the current thread's browser (`BrowserPool`). Closing the
  session closes only the context. The browser stays open for the next scenario on that thread, which is faster.
- **API call log.** Every API client calls `recordApiCall(...)`. If the scenario fails, `Hooks.after()` attaches
  `apiCalls()` to the report.
- **Self-healing.** `healer()` returns the scenario's healer, and `healEvents()` lists the locators it healed, so
  `Hooks.after()` can report them.
- **Data store.** `put`, `get` and `has` share typed values between steps (see Q4).
- **Clean-up.** `close(failed)` stops tracing and closes the context. The trace zip is saved under
  `target/artifacts/traces/` only when the scenario failed. Each close is attempted separately, so a clean-up
  error never hides the real test result.

### Its life in one scenario

```
PicoContainer:  new TestSession()        settings, store, apiCalls created
Hooks.before(): scenarioName(...)        name remembered for the trace file
steps:          page() / api()           browser context opened on first use
                put(...) / get(...)      data shared between steps
                recordApiCall(...)       each HTTP call logged
Hooks.after():  apiCalls(), healEvents(), screenshot if failed
                close(failed)            tracing stopped, context closed
PicoContainer:  container discarded      TestSession thrown away
```

### Why parallel scenarios don't interfere

Every scenario gets its own `TestSession`, and nothing in it is static or shared. Parallel scenarios therefore
have separate pages, call logs and data. They share only the browser of their worker thread, and each has its own
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
the other can read. The framework has four ways to share data.

### 1. The `TestSession` store with typed keys (between step classes)

Keys are declared once as constants in `src/test/java/com/agenticqa/steps/Keys.java`:

```java
public static final DataKey<Customer>    CUSTOMER = DataKey.of("customer", Customer.class);
public static final DataKey<APIResponse> RESPONSE = DataKey.of("lastResponse", APIResponse.class);
public static final DataKey<String>      NAME_TAG = DataKey.of("nameTag", String.class);
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
    page().open().search(session.get(CUSTOMER).name());
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
refers to as `${name}`. It is used to chain requests. For example, `endpoint_catalog.feature`:

```gherkin
And I save the "ada" response field "id" as "adaId"
And the "adaUpdate" request path parameter "id" is "${adaId}"
```

The first step saves the new id under `adaId`. Later steps write `${adaId}` and it is replaced with the value. The
same object also replaces `{unique}` with one random token per scenario, so test data never collides between
parallel scenarios.

Use `Variables` when the **feature file author** decides what is saved and where it is used. Use the
`TestSession` store when **Java code** passes an object to other steps.

### 3. `ApiState`: named requests and responses

`ApiState` (`src/main/java/com/agenticqa/api/ApiState.java`) keeps the requests a scenario is building and the
responses it got, each under a name (for example `"customer"` or `"order"`). One step creates a request, other
steps change its fields, a later step sends it, and any step can read an earlier response by name. `HttpSteps` and
`CustomerRequestSteps` both receive the same `ApiState`, so they work on the same requests.

### 4. Shared helper objects

Any class that a step constructor asks for is shared in the same way. For example, `CustomerApiSteps`,
`CustomerRequestSteps` and `CustomerUiSteps` all receive the same `CustomerCleanup`. Each one calls
`cleanup.track(id)` when it creates a customer, and the single `@After` hook in `CustomerCleanup` deletes all of
them at the end of the scenario.

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
