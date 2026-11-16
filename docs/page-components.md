# Page objects per scenario: `TestSession.pageComponent`

Steps get their page objects from `PageComponentFactory`:

```java
// HomeSteps, CustomerUiSteps
private HomePage homePage() {
    return PageComponentFactory.get(HomePage.class);
}
```

Every call in one scenario returns the **same** `HomePage`. A new scenario gets a new one. The page objects are
kept by this method in
[`TestSession`](../src/main/java/com/agenticqa/core/TestSession.java):

```java
private final Map<Class<? extends PageComponent>, PageComponent> pageComponents = new HashMap<>();

public synchronized <T extends PageComponent> T pageComponent(Class<T> type, Supplier<T> create) {
    PageComponent existing = pageComponents.get(type);
    if (existing == null) {
        existing = create.get();
        pageComponents.put(type, existing);
    }
    return type.cast(existing);
}
```

## How a call goes

```java
// PageComponentFactory.get
TestSession session = TestSession.current();                          // 1. the scenario on this thread
return session.pageComponent(HomePage.class,
        () -> create(HomePage.class, session));                       // 2. how to make one, if needed
```

| Call in the scenario | `pageComponents` before | What happens | Returned |
|---|---|---|---|
| 1st `get(HomePage.class)` | `{}` | `get` finds nothing, `create.get()` runs `new HomePage(page, healer)`, it is stored | the new `HomePage` |
| 2nd `get(HomePage.class)` | `{HomePage → #1}` | found, the supplier is **not** called | the same `HomePage` #1 |
| `get(CustomersPage.class)` | `{HomePage → #1}` | nothing for that class, a `CustomersPage` is created and stored | the new `CustomersPage` |
| next scenario, `get(HomePage.class)` | `{}` (new session) | created again, in the new scenario's browser context | a new `HomePage` #2 |

## The method line by line

### `<T extends PageComponent> T ... (Class<T> type, ...)`

The generic `T` ties the argument to the result: `pageComponent(HomePage.class, ...)` returns a `HomePage`, so the
step needs no cast. `extends PageComponent` means only page objects can be stored, not any class.

### `Map<Class<...>, PageComponent> pageComponents`

The key is the **class** itself. There is at most one page object of each type per scenario, and a class cannot be
mistyped the way a string name like `"homePage"` can.

The map's values are declared as plain `PageComponent`, because one map holds many different page types. That is
why the method reads into `PageComponent existing` and not into a `T`.

### `Supplier<T> create`

The caller passes **how** to make the page, not the page itself. The supplier runs only when the page does not
exist yet. That matters because creating a page calls `session.page()`, which opens the browser context:

- a page is created once, not on every `get`;
- nothing in the session knows about constructors or reflection; that stays in `PageComponentFactory`;
- an API-only scenario that never asks for a page never starts a browser.

Passing an already-made page (`pageComponent(HomePage.class, new HomePage(...))`) would build a new page object,
and open the browser, on every call, and then throw it away.

### `if (existing == null) { existing = create.get(); pageComponents.put(type, existing); }`

Classic "create on first use": look up, create if missing, store, return.

Why not the shorter `pageComponents.computeIfAbsent(type, t -> create.get())`? A page constructor may itself ask
for another page, for example a `HomePage` that builds its `HeaderComponent` with
`PageComponentFactory.get(HeaderComponent.class)`. That would change the map while `computeIfAbsent` is still
computing, and `HashMap.computeIfAbsent` throws `ConcurrentModificationException` in that case. With a plain
`get` and `put`, the supplier runs before the map is touched, so nested pages work.

### `return type.cast(existing);`

`existing` is a `PageComponent`; the method promises a `T`. `type.cast` converts it, checked at run time. It cannot
fail here, because the only thing ever stored under `HomePage.class` is the `HomePage` the supplier made, but
unlike a raw `(T) existing` it gives no "unchecked cast" warning and would fail at once, with the class names, if
something wrong ever got in.

### `synchronized`

A scenario runs on one thread, so in normal use no two threads call this on the same session. The keyword is a
cheap guard for code that does go to another thread (a parallel stream, a listener): without it, two threads could
both see `null` and create two pages of the same type. `TestSession`'s other lazy getters (`page()`, `healer()`)
are `synchronized` for the same reason.

Java locks are re-entrant, so the nested case above (a page constructor that calls `pageComponent` again on the
same thread) does not deadlock.

## Where the pages end

`TestSession.close(failed)`, which `Hooks.after` calls pass or fail, does:

```java
context = null;
page = null;
pageComponents.clear();
```

The page objects hold the scenario's Playwright `Page`, and that page belongs to the browser context that was just
closed. Clearing the map means no one can get an old page object pointing to a closed browser. And because the
session is per scenario (PicoContainer creates a new one each time), the next scenario starts with an empty map
anyway; the `clear()` just makes sure a closed session cannot hand out dead pages.

| Without the session map | With `TestSession.pageComponent` |
|---|---|
| `new HomePage(page, healer)` in every step class, or a field in each one | one `PageComponentFactory.get(HomePage.class)` from anywhere |
| two step classes have two different `HomePage` objects for one scenario | both get the same object |
| a static cache or `ThreadLocal` of pages must be cleared after each scenario, or the next scenario gets a page of a closed browser | pages live in the session and end with it, see [keys.md](keys.md#why-there-is-no-clear-or-remove) |
| parallel scenarios on a shared cache can get each other's pages | each scenario has its own session and its own map |

## Adding a page object

Nothing to register. A class that implements `PageComponent` and has a public `(Page page, Healer healer)`
constructor can be used at once, in the same style as `HomePage`:

```java
public class OrdersPage implements PageComponent {

    public static final String LOCATORS = "orderspage";   // locators/orderspage.json

    private final Page page;
    private final PageElements el;

    public OrdersPage(Page page, Healer healer) {
        this.page = page;
        this.el = new PageElements(page, healer, LOCATORS);
    }

    @Override
    public String getTitle() {
        return page.title();
    }
}

// in a step
OrdersPage orders = PageComponentFactory.get(OrdersPage.class);
```

If the constructor is missing, `PageComponentFactory` fails with
`OrdersPage needs a public constructor (Page page, Healer healer)`.
