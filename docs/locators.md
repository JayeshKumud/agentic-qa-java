# Locators in JSON files

Page objects hold no selectors. Each page has one JSON file under
[`src/main/resources/locators`](../src/main/resources/locators), and the page object looks elements up by name
when it uses them:

```java
public CustomersPage(Page page, Healer healer) {
    this.page = page;
    this.el = new PageElements(page, healer, "customers");   // locators/customers.json
}

public CustomersPage search(String text) {
    el.get("searchInput").fill(text);
    el.get("searchButton").click();
    page.waitForLoadState();       // the click waits for the navigation; this waits for the new page to load
    return this;
}
```

When the app changes an id, you fix it in the JSON file and the Java code stays as it is.

## File format

An example showing each form (the real file is
[`locators/customers.json`](../src/main/resources/locators/customers.json)):

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

## How it works

| Class | Role |
|---|---|
| [`LocatorRepository`](../src/main/java/com/agenticqa/ui/LocatorRepository.java) | Reads `locators/<page>.json` once per JVM and caches it, shared by all threads (read-only). It checks every entry and names the file and the entry when one is malformed. |
| [`PageElements`](../src/main/java/com/agenticqa/ui/PageElements.java) | Binds one locator file to the scenario's `Page` and `Healer`. `get(name)` returns a Playwright `Locator`, resolved through the healer when `heal` is true. `within(parent, name)` finds an inner element, for example a cell in a row. |

- **Looked up on use, not in the constructor.** `el.get("searchInput")` runs every time the element is used, so
  healing always works on the page as it is now. The constructor only checks that the file exists.
- **Typos fail clearly.** An unknown name fails with the list of names in the file:
  `No locator 'serchInput' in locators/customers.json. Locators: [searchInput, searchButton, ...]`.
- **Healing keys stay stable.** The fingerprint key of an element is `<page>.<name>`, for example
  `customers.searchInput`. These are the keys in `healing/fingerprints.json`. Renaming an entry in the JSON
  therefore starts a new fingerprint baseline for that element.

## Adding a page

1. Create `src/main/resources/locators/<page>.json` with the elements.
2. Create the page object with a `PageElements` for that file, and actions that use `el.get("...")`.
3. Use it from a step class, created lazily (see `CustomerUiSteps.page()`), so API-only scenarios never open a
   browser.

`LocatorRepositoryTest` shows how to unit-test a locator file without a browser.
