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
