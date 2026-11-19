# Finding and writing locators

How to find a good locator for an element in the browser, write it in a `locators/<page>.json` file, and use it
from a page object. For the file format itself, see [locators.md](locators.md).

## 1. How a locator gets used

Every entry in a locator file ends up as one string passed to Playwright:

```java
page.locator(definition.selector())      // PageElements / SmartLocator
```

So the `selector` in the JSON must be a **Playwright selector string**. Playwright looks at how the string starts
and picks a search engine for it. A string with no prefix is read as **CSS**.

```json
{
  "searchInput": { "selector": "#search-input", "description": "search input" },
  "addButton":   "#add-customer-button"
}
```

```java
el.get("searchInput").fill("Ada");     // the page object asks for the element by name
el.get("addButton").click();
```

## 2. Finding the element in the browser

### Inspect the element (DevTools)

1. Open the page in Chrome or Edge and press **F12**.
2. Click the arrow icon (top left of DevTools), then click the element on the page.
3. The Elements panel highlights its HTML, for example:

   ```html
   <button type="submit" name="login" id="kc-login" class="btn btn-primary" data-testid="login-btn">Sign in</button>
   ```

4. Pick the most stable thing in it (see [section 3](#3-which-locator-to-choose)): here `data-testid`, then the
   text "Sign in", then `id` or `name`. Avoid the classes.

### Check that it finds exactly one element

In the DevTools **Console** tab:

```js
document.querySelectorAll('[data-testid="login-btn"]').length   // CSS: should be 1
$x("//button[@name='login']").length                             // XPath: should be 1
```

Or in the **Elements** tab press **Ctrl+F** and type a CSS selector or XPath: it shows "1 of 1" when it is unique.

### Let Playwright suggest one (codegen)

Playwright's recorder opens a browser, and when you click an element it writes a locator for it:

```bash
mvn -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="codegen http://127.0.0.1:8000"
```

Use the **Pick locator** button in the recorder window, hover over an element, and copy what it shows, for
example `getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in"))`. Turn it into a selector
string with the table in [section 4](#4-selector-strings).

## 3. Which locator to choose

From most to least stable. Take the first one the element has:

| Priority | Use | Why |
|---|---|---|
| 1 | `data-testid` | added for tests, developers rarely change it |
| 2 | role + visible name (`role=button[name="Sign in"]`) | how a user sees the element; survives layout changes |
| 3 | label of an input | stays the same when the markup changes |
| 4 | placeholder or visible text | readable, but changes when the wording changes |
| 5 | `id` or `name` | fine if it is a real, fixed value |
| 6 | CSS with classes or structure | breaks when the styling or layout changes |
| 7 | XPath | when nothing else works, for example going up to a parent |

**Avoid:**

- Generated ids and classes such as `#ember123`, `.css-1x2y3z`, `mat-input-7`: they change on every build or load.
- Long paths such as `div > div:nth-child(3) > span`: any layout change breaks them.
- Positions (`nth=2`, `:nth-child`) unless the order is what you are testing.

## 4. Selector strings

| Kind | Selector string | Finds |
|---|---|---|
| CSS id | `#kc-login` | the element with `id="kc-login"` |
| CSS class | `.btn.btn-primary` | an element with both classes (no space between them) |
| CSS attribute | `[name="login"]` | the element with `name="login"` |
| CSS, several attributes | `[type="submit"][name="login"]` | one element with **both** attributes |
| CSS, tag + attribute | `button[type="submit"]` | a `<button>` with `type="submit"` |
| CSS, inside | `form#login button` | a button anywhere inside `form#login` (space = any depth) |
| CSS, direct child | `div.menu > button` | a button that is a direct child of `div.menu` |
| test id | `data-testid=login-btn` | the element with `data-testid="login-btn"` |
| id shortcut | `id=kc-login` | same as `#kc-login` |
| text, contains | `text=Sign in` | an element containing "Sign in", any case |
| text, exact | `text="Sign in"` | an element whose whole text is exactly "Sign in" |
| role, exact name | `role=button[name="Sign in"]` | a button whose **whole** accessible name is "Sign in" (case-sensitive) |
| role, any case | `role=button[name="Sign in" i]` | a button whose whole name is "Sign in", any case |
| role, part of name | `role=button[name=/Sign in/]` | a button whose name **contains** "Sign in" (add `i` after the last `/` for any case) |
| role, no name | `role=dialog` | any dialog |
| XPath | `xpath=//button[@name='login']` | a button with `name="login"` |
| XPath, two attributes | `xpath=//*[@type='submit' and @name='login']` | an element with both attributes |
| XPath, by text | `xpath=//span[text()='Total']` | a span whose text is exactly "Total" |
| XPath, contains text | `xpath=//div[contains(text(),'Invalid')]` | a div whose text contains "Invalid" |
| XPath, parent | `xpath=//td[text()='Ada']/..` | the row (parent) of the cell "Ada" |
| chained | `div.menu >> text=Save` | "Save" inside `div.menu`; each part searches inside the previous result |
| label | `internal:label="Username or email"i` | the **input** whose label is "Username or email" |

The last one is the string `page.getByLabel(...)` builds. The `internal:` prefix works, but Playwright does not
document it as public syntax. At the end, `i` means "contains, any case" and `s` means "exact".

### How the `getBy...` methods map to strings

| Playwright method | Equivalent selector string |
|---|---|
| `getByTestId("login-btn")` | `data-testid=login-btn` |
| `getByRole(BUTTON, name "Sign in")` | `role=button[name=/Sign in/i]` (part of the name, any case) |
| `getByRole(BUTTON, name "Sign in", exact)` | `role=button[name="Sign in"]` |
| `getByText("Sign in")` | `text=Sign in` |
| `getByText("Sign in", exact)` | `text="Sign in"` |
| `getByLabel("Password")` | `internal:label="Password"i` |
| `getByPlaceholder("Search")` | `[placeholder="Search"]` |
| `getByTitle("Close")` | `[title="Close"]` |
| `getByAltText("Logo")` | `img[alt="Logo"]` |

## 5. Writing it in JSON

### Quotes and backslashes

A JSON string is written in double quotes. A double quote **inside** the selector must be written `\"`. JSON
removes the backslash when the file is read, so Playwright never sees it.

```json
"loginButton": { "selector": "[type=\"submit\"][name=\"login\"]" }
```

Single quotes, or no quotes, need no backslash:

```json
"loginButton": { "selector": "[type='submit'][name='login']" }
"loginButton": { "selector": "[type=submit][name=login]" }
"loginButton": { "selector": "xpath=//*[@type='submit' and @name='login']" }
```

| You write | Starts with `//`? | Needs `\`? |
|---|---|---|
| CSS with `"` inside | no | yes, `\"` |
| CSS with `'` or no quotes | no | no |
| XPath | yes (`xpath=//...`) | no, if you use `'` |

`//` belongs to XPath only (it means "anywhere in the page"); CSS never uses it.

### Self-healing or not

```json
"signInButton": { "selector": "#kc-login", "description": "sign in button" },
"errorMessage": { "selector": "text=Invalid username or password", "heal": false }
```

- `heal` is `true` by default: if the selector stops matching, the healer looks for the element that looks like
  the one it saw last time (see [healing.md](healing.md)).
- Use `"heal": false` for elements that may legitimately be missing, such as an error message, a result row, or an
  empty-list message. Otherwise their absence starts a search for a look-alike.

### One entry for many elements (placeholders)

Write a word in capitals in the selector and replace it when you use the locator:

```json
"roleLogin": { "selector": "[data-testid='modal-box'] button.role_login_ROLE" },
"rowTitle":  { "selector": "[row-index='MYROW'] div[col-id='title']", "heal": false }
```

```java
el.get("roleLogin", "ROLE", "COE").click();      // button.role_login_COE
el.get("rowTitle", "MYROW", "0").textContent();  // row 0
```

### An element inside another one

```json
"rows":    { "selector": "#customers-table tr.customer-row", "heal": false },
"rowName": { "selector": ".col-name", "heal": false }
```

```java
Locator firstRow = el.get("rows").first();
String name = el.within(firstRow, "rowName").textContent();   // .col-name inside that row
```

### By id

All of these find `<input id="username">`:

| Selector string | Kind | Notes |
|---|---|---|
| `#username` | CSS | shortest and most common |
| `id=username` | Playwright attribute shortcut | same result, reads clearly |
| `[id="username"]` | CSS attribute | works for any id, even with special characters |
| `xpath=//*[@id='username']` | XPath | only if you need XPath anyway |

```json
"usernameField": "#username",
"passwordField": { "selector": "#password", "description": "password input" },
"signInButton":  { "selector": "id=kc-login", "description": "sign in button" },
"errorBox":      { "selector": "[id='error-box']", "description": "error message", "heal": false }
```

```java
el.get("usernameField").fill("system");
el.get("signInButton").click();
```

When `#id` does not work:

- **Special characters** (`.`, `:`, `[`, or a leading digit), for example `id="user.name"`: CSS reads
  `#user.name` as id `user` plus class `name`. Use `[id="user.name"]` or `id=user.name`.
- **Generated ids** such as `mat-input-7`, `ember123` or `j_idt45` change on every build or load. Use the label,
  name, role or test id.
- **Ids with a changing part**, for example `row-17-delete`: match the fixed part with `[id^="row-"]` (starts
  with), `[id$="-delete"]` (ends with) or `[id*="delete"]` (contains), or use a placeholder,
  `"#row-MYROW-delete"`, with `el.get("deleteButton", "MYROW", "17")`.

### By type (for example a password field)

All of these find `<input type="password" name="password">`:

| Selector string | Kind | Matches |
|---|---|---|
| `[type="password"]` | CSS attribute | any element with `type="password"` |
| `input[type="password"]` | CSS, tag + attribute | only an `<input>` with that type (clearer) |
| `input[type="password"][name="password"]` | CSS, two attributes | stricter, if there is more than one password field |
| `xpath=//input[@type='password']` | XPath | the same, as XPath |

```json
"passwordInput":   { "selector": "input[type='password']", "description": "password input" },
"newPassword":     { "selector": "input[type='password'][name='newPassword']", "description": "new password" },
"confirmPassword": { "selector": "input[type='password'][name='confirmPassword']", "description": "confirm password" }
```

```java
el.get("passwordInput").fill(password);
```

- A login form usually has one password field, so `input[type='password']` is unique and survives id and name
  changes.
- A change-password form has several: `input[type='password']` then matches all of them and Playwright fails with
  a *strict mode violation*. Add `name`, `id` or the label to tell them apart (`>> nth=0` works too, but breaks
  if the fields are reordered).
- `type` is the HTML attribute. A password field has no visible text, so `text=...` cannot find it; its label
  can: `internal:label="Password"i`.
- No space between the brackets: `input[type='password'][name='password']` is one input with both attributes.

### A button in a table row, picked by the value in another column

A table with a Name column and a Select column, where each Select button carries the name in its class:

```html
<tr><td>COE</td><td><button class="button button-small button-kkk role_login_COE">Select</button></td></tr>
<tr><td>NBE</td><td><button class="button button-small button-kkk role_login_NBE">Select</button></td></tr>
```

One entry with a placeholder serves every row ([`homepage.json`](../src/main/resources/locators/homepage.json)):

```json
"selectButtonByName": {
  "selector": "button.button-small.button-kkk.role_login_MYNAME",
  "description": "Select button in the row whose Name column is MYNAME"
}
```

```java
// HomePage
public HomePage selectByName(String name) {
    clickAndWait(el.get("selectButtonByName", "MYNAME", name));   // button....role_login_COE
    return this;
}

// step
pages.home().selectByName("COE");
```

The class can only match that row's button, so the Name column is never read. CSS classes cannot contain
spaces, so this works for names like `COE`. If the button's class does not contain the name (or the name has
spaces), find the row by its first cell and the button inside it:

```json
"selectButtonByName": {
  "selector": "xpath=//tr[td[1][normalize-space()='MYNAME']]//button[contains(@class,'button-kkk')]"
}
```

## 6. Examples

The element, then a good entry for it:

```html
<input id="username" name="username" type="text">              <!-- label: "Username or email" -->
```
```json
"usernameField": { "selector": "#username", "description": "user name input" }
```

```html
<button type="submit" name="login" class="btn btn-primary">Sign in</button>
```
```json
"loginButton": { "selector": "role=button[name='Sign in']", "description": "sign in button" }
```
or, if the text may be translated: `"selector": "[type=submit][name=login]"`

```html
<input placeholder="Search customers" class="form-control">
```
```json
"searchInput": { "selector": "[placeholder='Search customers']", "description": "search input" }
```

```html
<div data-testid="change-role">Change role</div>
```
```json
"roleTitle": { "selector": "data-testid=change-role", "description": "change role title" }
```

```html
<span class="alert">Invalid username or password.</span>
```
```json
"invalidLoginError": { "selector": "text=Invalid username or password", "heal": false }
```

```html
<tr><td>Ada Lovelace</td><td><button>Delete</button></td></tr>
```
```json
"deleteButtonOfRow": { "selector": "xpath=//tr[td[text()='NAME']]//button[text()='Delete']", "heal": false }
```
```java
el.get("deleteButtonOfRow", "NAME", "Ada Lovelace").click();
```

## 7. Common mistakes

| Mistake | What happens | Fix |
|---|---|---|
| `"selector": "Save Layout"` | read as CSS: a `<Layout>` tag inside a `<Save>` tag, never matches | `text=Save Layout` |
| `"selector": "Username or email"` | read as CSS, never matches | `internal:label="Username or email"i`, or the input's `#id` |
| `[type=submit] [name=login]` (space) | `name=login` **inside** a `type=submit` element | `[type=submit][name=login]` |
| `.btn .btn-primary` (space) | `.btn-primary` inside `.btn` | `.btn.btn-primary` |
| `"[name="login"]"` in JSON | the inner `"` ends the JSON string: invalid file | `"[name=\"login\"]"` or `"[name='login']"` |
| `//button` in a CSS selector | not CSS | `xpath=//button`, or the CSS `button` |
| a selector matching 2+ elements | the action fails with "strict mode violation" | make it more specific, or use `within(...)` |
| `role=button[name="Add Master"]` on a button with an icon or `::after` | finds nothing: the quoted name must be the **whole** accessible name, and the icon or `::after` text is part of it | `role=button[name=/Add Master/]`, or `button:has(span.dropdown-menu-text:text-is('Add Master'))` |
| a generated id (`#mat-input-7`) | breaks on the next build | use the label, role, name or test id |

## 8. Checking a locator

- **DevTools Console:** `document.querySelectorAll('your-css').length` or `$x("your-xpath").length` should be `1`.
- **Playwright Inspector:** run a scenario with `PWDEBUG=1` set, and type a selector in the Inspector's locator
  box: it highlights what it finds.
- **In a run:** a selector that finds nothing waits until `ui.waits.actionMs` and fails with the selector in the
  message. A `heal: true` element that is healed is logged in `target/healing/heals.jsonl`, with the new selector
  to put in the JSON file.
