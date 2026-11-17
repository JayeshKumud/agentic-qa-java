# Sharing data with Keys

Steps of one scenario often live in different classes, and some code that needs scenario data is not a step at
all (for example `BackUniqueTransit`). They share values through the scenario's `TestSession`, and every value is
stored under a **key** declared once in
[`src/test/java/com/agenticqa/steps/Keys.java`](../src/test/java/com/agenticqa/steps/Keys.java).

```java
// CustomerApiSteps: an API step stores the customer it created
session.put(Keys.CUSTOMER, customer);

// CustomerUiSteps: a GUI step of the same scenario reads it
page().open().search(session.get(Keys.CUSTOMER).getName());
```

## Why keys

A key is a `DataKey<T>`: a name plus the type of the value stored under it.

| Without keys (`Map<String, Object>`) | With `DataKey<T>` |
|---|---|
| `(Customer) store.get("customer")`: a cast at every read | `session.get(Keys.CUSTOMER)` returns a `Customer`, no cast |
| `store.put("customer", "oops")` compiles and fails later | `session.put(Keys.CUSTOMER, "oops")` does not compile |
| `"customer"` in one class, `"Customer"` in another: silently two values | every class uses the same constant |
| a missing value is `null`, and the `NullPointerException` shows up far away | `get` fails at once: `Nothing stored under key 'customer' in this scenario. Did an earlier step run?` |
| data in `static` fields or `ThreadLocal`s needs a `clear()` after every scenario | data lives in the scenario's session and ends with it |

## Why there is no `clear()` or `remove()`

### The old way: data in a static ThreadLocal

```java
// TestSession, old style
private static final ThreadLocal<HashMap<String, Unique>> baseUnique = ThreadLocal.withInitial(HashMap::new);

public static void setBaseUniqueMap(String name, Unique unique) { baseUnique.get().put(name, unique); }
public static HashMap<String, Unique> getBaseUniqueMap()        { return baseUnique.get(); }

public static void clear() {                 // must be called after EVERY scenario
    baseUnique.remove();
}
```

Here the **data itself** sits in a `static` field. A `ThreadLocal` keeps one map per **thread**, not per
scenario, and the test threads are pooled: one thread runs many scenarios, one after the other. So:

- if `clear()` is not called after a scenario (a hook fails early, a new hook forgets it, a retry runs
  differently), the **next scenario on that thread inherits the old uniques** and looks up wrong registration ids;
- every new ThreadLocal variable needs its own line in `clear()`; one missed line is one leak;
- anyone can call `getBaseUniqueMap().clear()` or `put(...)` on the live map, from anywhere.

### The new way: data in the scenario's session

```java
// Keys.java: only the key
public static final DataKey<Map<String, Unique>> BASE_UNIQUE =
        DataKey.mapOf("baseUnique", String.class, Unique.class);

// any step or helper: the data goes into the running scenario's session
TestSession.current().put(Keys.BASE_UNIQUE, "U1", unique);
Map<String, Unique> uniques = TestSession.current().map(Keys.BASE_UNIQUE);
```

The map is stored **inside the scenario's `TestSession`**, so it lives exactly as long as the scenario:

| Moment | What happens to the data |
|---|---|
| scenario starts | PicoContainer creates a **new** `TestSession` with an **empty** store |
| `TestInitializeHooks.bindSession` | binds that session to the thread, so `TestSession.current()` finds it |
| steps run | `put` / `add` fill **this session's** store; parallel scenarios fill their own sessions |
| `TestInitializeHooks.closeBrowser` | `session.close(failed)` runs, **pass or fail**, and removes the one ThreadLocal pointer |
| scenario ends | PicoContainer discards the session, and the map goes with it |
| next scenario, same thread | gets a new session with an empty store: nothing to clear |

So the "clear" is not a method someone has to remember: it is the end of the session, which always happens.
This also holds for every key you add later: a new key needs no clean-up line anywhere.

`BackUniqueTransitTest` checks both cases: `parallelScenariosEachSeeOnlyTheirOwnUniques` (16 overlapping
scenarios on 8 threads, each with its own `U1`) and `aReusedThreadDoesNotSeeThePreviousScenariosUniques` (a second
scenario on the same thread starts empty).

### The only ThreadLocal left

`TestSession` keeps exactly one `ThreadLocal`: a pointer from the thread to its current session, for
`TestSession.current()`. It holds no data of its own. `TestInitializeHooks.bindSession` sets it and `TestSession.close()` removes it,
and both are framework code that runs for every scenario, so step and helper code never deals with it.

### When you would still remove something

Only to reset a value **in the middle of a scenario**, for example before a second round in the same scenario. That
is a different job from the old `clear()`: store a new value under the key (`put` replaces it). A
`session.remove(key)` does not exist yet; add it if a step really needs to forget a value mid-scenario.

## Why the `Keys` variables can be `static`

```java
public static final DataKey<Customer> CUSTOMER = DataKey.of("customer", Customer.class);
```

A `static` field is shared by the whole run: every scenario and every thread sees the same one. That is wrong for
**data**, and right for a **key**:

| | Static ThreadLocal (old) | `Keys` constant (new) |
|---|---|---|
| What the static field holds | the data (one map per thread) | only a **name** (`"customer"`) and a **type** (`Customer`) |
| Changes during the run | yes, every `put` changes it | never: `final`, and `DataKey` is an immutable record |
| Different per scenario | must be, so it needs clean-up | no, every scenario uses the same name and type |
| Clean-up | `clear()` after every scenario | none |

Think of the key as a **label on a drawer**. Every scenario's `TestSession` has its own set of drawers; the
label `CUSTOMER` is the same everywhere, but what is inside the drawer belongs to one scenario only. Two parallel
scenarios that both write `session.put(Keys.CUSTOMER, ...)` write into two different sessions.

Declaring every key once in `Keys.java` also means:

- each name has exactly one type, so no class can store a `String` where another expects a `Customer`;
- one file lists everything scenarios share, with a comment saying who writes and who reads each key;
- renaming or retyping a key is a compile error everywhere it is used, not a silent mismatch at run time.

## The keys in `Keys.java`

| Key | Type | Factory | Written by | Read by |
|---|---|---|---|---|
| `CUSTOMER` | `Customer` (`apis.model`) | `DataKey.of` | API steps (created or updated customer), GUI step "I add a customer ... through the form" | API checks by id, GUI search and row comparison |
| `RESPONSE` | `Response` (REST Assured) | `DataKey.of` | every customer API step | `Then the API responds with status {int}` |
| `NAME_TAG` | `String` | `DataKey.of` | `Given {int} customers share a unique name tag` | GUI search for that tag |
| `ZONE_ID` | `Long` | `DataKey.of` | `Given a unique zone id is set` | the zone id steps |
| `VIEWS` | `List<String>` | `DataKey.listOf` | your steps | your steps |
| `BASE_UNIQUE` | `Map<String, Unique>` | `DataKey.mapOf` | `Given the unique {string} has registration id {string}` | `BackUniqueTransit` |

## Kinds of keys

### One value: `DataKey.of`

For a single object: a `String`, a number, a model class, a REST Assured `Response`.

```java
// Keys.java
public static final DataKey<String>   NAME_TAG = DataKey.of("nameTag", String.class);
public static final DataKey<Long>     ZONE_ID  = DataKey.of("zoneId", Long.class);
public static final DataKey<Customer> CUSTOMER = DataKey.of("customer", Customer.class);
public static final DataKey<Response> RESPONSE = DataKey.of("lastResponse", Response.class);
```

```java
session.put(Keys.NAME_TAG, tag);                 // stores, or replaces the earlier value
String tag = session.get(Keys.NAME_TAG);         // fails clearly if nothing was stored
if (session.has(Keys.ZONE_ID)) { ... }           // for a value that is optional
```

Use the wrapper types (`Long`, `Integer`, `Boolean`) in the key: `DataKey.of("zoneId", long.class)` does not
work, because a stored value is always an object.

### A list: `DataKey.listOf`

`DataKey.of("views", List.class)` would lose the element type, so lists have their own factory:

```java
// Keys.java
public static final DataKey<List<String>> VIEWS = DataKey.listOf("views", String.class);
```

A list can be built up item by item, the usual case when several steps each add one:

```java
session.add(Keys.VIEWS, "summary");              // creates the list on first use; fails on null
session.add(Keys.VIEWS, "details");
List<String> views = session.list(Keys.VIEWS);          // a read-only copy, empty if nothing was added
List<String> views = session.requireList(Keys.VIEWS);   // the same, but fails clearly if it is empty
```

or stored whole, when one step has the complete list:

```java
session.put(Keys.VIEWS, List.copyOf(fileNames)); // store a copy: later changes to fileNames cannot affect it
List<String> views = session.get(Keys.VIEWS);
```

Use **one style per key**. `List.copyOf` makes a list that cannot be changed, so a later `add` on the same key
fails with `UnsupportedOperationException`.

### A map: `DataKey.mapOf`

For values looked up by name, for example uniques by their base name:

```java
// Keys.java
public static final DataKey<Map<String, Unique>> BASE_UNIQUE =
        DataKey.mapOf("baseUnique", String.class, Unique.class);
```

```java
session.put(Keys.BASE_UNIQUE, "U1", new Unique("REG-1001"));   // creates the map on first use;
                                                               // replaces an entry with the same name;
                                                               // fails on a null name or value
Map<String, Unique> uniques = session.map(Keys.BASE_UNIQUE);   // a read-only copy, in the order added;
                                                               // empty if nothing was put
Unique u1 = uniques.get("U1");                                 // null if U1 was not stored
```

The same rule as for lists: put entries one by one with `put(key, name, value)`; do not also store a whole map
with `put(key, Map.of(...))`.

### Which one to use

| You have | Key | Write | Read |
|---|---|---|---|
| one value | `DataKey.of` | `put(key, value)` | `get(key)`, `has(key)` |
| items that steps add one by one | `DataKey.listOf` | `add(key, item)` | `list(key)`, `requireList(key)` |
| a complete list | `DataKey.listOf` | `put(key, List.copyOf(list))` | `get(key)` |
| values looked up by name | `DataKey.mapOf` | `put(key, name, value)` | `map(key)` |

## Using keys in a step class

Step classes receive the scenario's `TestSession` from PicoContainer through their constructor. Every step class
of the scenario gets the **same** session:

```java
public class OrderSteps {

    private final TestSession session;

    public OrderSteps(TestSession session) {          // PicoContainer injects it
        this.session = session;
    }

    @When("I place an order")
    public void placeOrder() {
        Order order = ApiFactory.getApi(OrderApi.class).postOrder(new Order(...)).as(Order.class);
        session.put(Keys.ORDER, order);               // later steps, API or GUI, read it
    }

    @Then("the order page shows the order")
    public void orderPageShowsIt() {
        Order order = session.get(Keys.ORDER);
        ...
    }
}
```

Static imports keep the steps short, as in `CustomerApiSteps`:

```java
import static com.agenticqa.steps.Keys.CUSTOMER;

session.put(CUSTOMER, customer);
```

## Using keys in other classes

A helper, page object or any class that PicoContainer does not create gets the session with
`TestSession.current()`, as `BackUniqueTransit` does:

```java
public BackUniqueTransit(String time, String uniqueIds) {
    Map<String, Unique> baseUniques = TestSession.current().map(Keys.BASE_UNIQUE);
    ...
}
```

`TestSession.current()` returns the same session the steps were given. It works:

- **inside a method or constructor**, called when the value is needed. Never keep it in a `static` field: that
  would hold on to the first scenario's session for the whole run;
- **while a scenario runs**, after `TestInitializeHooks.bindSession` binds the session and before `TestInitializeHooks.closeBrowser` closes it;
- **on the scenario's thread only.** Inside a `CompletableFuture`, a `parallelStream()` or your own thread pool it
  fails with `No scenario is running on this thread`. Read the session first, on the scenario's thread, and hand
  the object in.

When a step creates the helper, passing the session in is also fine, and easier to unit-test:
`new BackUniqueTransit(session, time, uniqueIds)`.

`Keys` is in `src/test/java`, so only test code can use it. A class under `src/main/java` needs its keys in a
class of its own there, for example `com.agenticqa.core.SharedKeys`.

## Adding a key

1. Add the constant to `Keys.java`, with a comment saying who writes it and who reads it:
   ```java
   /** The order placed by "I place an order", read by the order page steps. */
   public static final DataKey<Order> ORDER = DataKey.of("order", Order.class);
   ```
2. Pick the factory from the table above: `of`, `listOf` or `mapOf`.
3. Give the name (`"order"`) a value no other key uses. It appears in error messages.
4. Write with `put` or `add`, read with `get`, `list` or `map`, in any step class or through
   `TestSession.current()`.

## What not to do

- **No `static` fields or `ThreadLocal`s for scenario data.** They are shared by parallel scenarios, outlive the
  scenario and need a `clear()` that someone forgets. Put the value in the session under a key.
- **No `new TestSession()` in steps or helpers.** It is a second, empty session. Take the injected one or
  `TestSession.current()`.
- **No keys declared in step classes.** Two classes would declare `"customer"` with different types. Declare
  each key once, in `Keys.java`.
- **No changing a value read from the session to pass it on.** `list` and `map` return copies; to change what
  later steps see, `put` or `add` again.

## Related

- [api.md](api.md): API classes and how API and GUI steps share "the customer"
- [base-unique.md](base-unique.md): `BASE_UNIQUE` compared with the old static ThreadLocal `setBaseUniqueMap`, point by point
- [explanation.md](explanation.md): how PicoContainer creates the session and the step classes, `TestSession.current()`
