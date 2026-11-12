# Locators in JSON files and the BasePage

Page objects hold no selectors. Selectors live in JSON files under
[`src/main/resources/locators`](../src/main/resources/locators), and a page object can use **several** of them:
one per area of the page, plus shared files for parts that many pages have. Every page object extends
[`BasePage`](../src/main/java/com/agenticqa/ui/BasePage.java), which holds the page and its locator files and
offers the common actions by locator name:

```java
public class CustomersPage extends BasePage<CustomersPage> {

    /** Locator files of this page, most specific first; common.json is added by BasePage. */
    public static final List<String> LOCATORS = List.of("customers", "customer-form", "pagination");

    public CustomersPage(Page page, Healer healer) {
        super(page, healer, LOCATORS.toArray(String[]::new));
    }

    @Override
    protected String path() {
        return "/";
    }

    public CustomersPage search(String text) {
        fill("searchInput", text);          // found in customers.json
        return submit("searchButton");      // click and wait for the new page
    }

    public CustomersPage nextPage() {
        return submit("pagination.nextLink");   // only pagination.json
    }
}
```

When the app changes an id, you fix it in the JSON file and the Java code stays as it is.

## Several locator files per page

The customers page uses four files:

| File | Holds | Used by |
|---|---|---|
| [`customers.json`](../src/main/resources/locators/customers.json) | search box, results table and its cells | `CustomersPage` |
| [`customer-form.json`](../src/main/resources/locators/customer-form.json) | the add-customer form | `CustomersPage` (and any page with the same form) |
| [`pagination.json`](../src/main/resources/locators/pagination.json) | paging links and "Page X of Y" | every list page |
| [`common.json`](../src/main/resources/locators/common.json) | elements every page has, such as the `heading` | every page: `BasePage` adds it last |

How a name is found:

- **Plain name** (`"addButton"`): the files are searched in the order the page lists them, then `common.json`.
  The first file that has the name wins, so a page's own file can **override** a shared entry by using the same
  name.
- **Qualified name** (`"pagination.nextLink"`): only that file is used. Use it when two files have the same name,
  or to make clear where a shared element comes from. The file need not be one of the page's files, and files
  in subfolders work too (`"customers/search.input"` is `locators/customers/search.json`, name `input`).
- **Unknown name**: fails and lists the names of every file of the page:

  ```
  No locator 'nextLnk' in the locator files of this page.
    locators/customers.json: [searchInput, searchButton, rows, ...]
    locators/customer-form.json: [newName, newEmail, addButton]
    locators/pagination.json: [pageIndicator, nextLink, previousLink]
    locators/common.json: [heading]
  ```

Every file is checked when the page object is created, so a missing or malformed file fails at once.

## What BasePage gives every page

| Member | Does |
|---|---|
| `open()` | Navigates to `path()` (relative to `ui.baseUrl`) |
| `path()` | Abstract: the page's path, for example `"/"` or `"/orders"` |
| `find(name)` | The element's `Locator` (self-healing if marked so) |
| `fill(name, text)`, `click(name)`, `select(name, option)` | Actions on an element |
| `submit(name)` | Clicks and waits for the page the click loads (forms, links), so the next read never sees the old document |
| `text(name)`, `isVisible(name)` | Reads (`isVisible` is meant for `"heal": false` elements) |
| `heading()`, `title()`, `url()` | The `heading` of `common.json`, the document title, the current URL |
| `el` | The `PageElements`, for `el.within(row, "rowName")` |
| `page` | The Playwright `Page`, for anything else |

`BasePage<T>` takes the page class as its type parameter, so every action returns the concrete page and calls
chain: `customersPage.open().search("Ada").nextPage()`. Wait times come from `ui.waits` in
`config/common.yaml`.

## File format

An example showing each form:

```json
{
  "_comment": "Keys starting with _ are ignored.",

  "searchInput": { "selector": "#search-input", "description": "search input" },
  "addButton":   "#add-customer-button",

  "rows":      { "selector": "#customers-table tr.customer-row", "heal": false },
  "noResults": { "selector": "#no-results",                     "heal": false },
  "rowName":   { "selector": ".col-name", "heal": false, "description": "name cell, inside a row" }
}
```

| Field | Default | Meaning |
|---|---|---|
| `selector` | required | Any Playwright selector: CSS, `text=...`, `role=button[name="Save"]`, XPath. |
| `description` | the name | Readable name used in logs and heal reports (`the "search input" locator was healed ...`). |
| `heal` | `true` | `true` makes the element a [self-healing](healing.md) locator. Use `false` for elements that can legitimately be missing, such as result rows or an empty-result message. Otherwise their absence would start a search for a look-alike. |

A plain string (`"addButton": "#add-customer-button"`) is short for `{ "selector": "...", "heal": true }`.
Locator names must not contain a dot (the dot separates file and name).

## How it works

| Class | Role |
|---|---|
| [`LocatorRepository`](../src/main/java/com/agenticqa/ui/LocatorRepository.java) | Reads `locators/<file>.json` once per JVM and caches it, shared by all threads (read-only). It checks every entry and names the file and the entry when one is malformed. `find(files, name)` does the lookup across files. |
| [`PageElements`](../src/main/java/com/agenticqa/ui/PageElements.java) | Binds a page's locator files to the scenario's `Page` and `Healer`. `get(name)` returns a Playwright `Locator`, resolved through the healer when `heal` is true. `within(parent, name)` finds an inner element, for example a cell in a row. |
| [`BasePage`](../src/main/java/com/agenticqa/ui/BasePage.java) | Base of every page object: adds `common.json`, builds the `PageElements`, offers the actions above. |

- **Looked up on use, not in the constructor.** `find("searchInput")` runs every time the element is used, so
  healing always works on the page as it is now.
- **Healing keys stay stable.** The fingerprint key of an element is `<file>.<name>`, for example
  `customers.searchInput` or `pagination.nextLink`. These are the keys in `healing/fingerprints.json`. The same
  name in two files never shares a fingerprint. **Moving an entry to another file, or renaming it, changes its
  key**: rename the key in `healing/fingerprints.json` too, or the element starts a new baseline and cannot heal
  until it has been seen once.

## Adding a page

1. Create its locator file(s) under `src/main/resources/locators`, for example `orders.json`. Reuse
   `pagination.json`, `common.json` or other shared files for parts the page has in common with others; put
   new shared parts (a header, a dialog) in a file of their own.
2. Create the page object: `extends BasePage<OrdersPage>`, call `super(page, healer, "orders", "pagination")`,
   implement `path()`, and write business actions with `fill`, `click`, `submit` and `find`.
3. Use it from a step class, created lazily (see `CustomerUiSteps.page()`), so API-only scenarios never open a
   browser.

`LocatorRepositoryTest` shows how to unit-test locator files, including the lookup across files, without a
browser.
