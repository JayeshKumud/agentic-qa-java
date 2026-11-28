# Several grids on one page

Some windows show two AG Grids at once, both visible and side by side or one above the other. The grid steps and
[`GridLibrary`](../src/main/java/com/agenticqa/ui/GridLibrary.java) work on **one grid at a time**, and on such a
window you say which one: by a column that only that grid has.

```gherkin
When I open the Price Booking window
And I use the grid with column "Booking"        # the grid that has a Booking column
Then the Price grid is loaded
When I check the Price grid rows matching
  | Booking | Booking A |
And I use the grid with column "Customer"       # now the other grid
Then in the grid rows matching, "Status" is "Open"
  | Customer | Ada |
```

## Why a grid has to be picked

Every lookup of `GridLibrary` (the header, column ids, rows, cells, values and the waits) searches inside one
grid element, its **root** (`root` in [`locators/grid.json`](../src/main/resources/locators/grid.json), AG Grid's
`.ag-root-wrapper`). AG Grid puts the header, the rows and the loading overlay inside that element, so `grid.json`
works for every grid.

With two grids, `.ag-root-wrapper` finds two elements. Before grids had a root, this failed in
`CommonActions.assertVisible` with a strict-mode error listing both elements. Worse, rows are found by their
`row-index`, and both grids have a row 0, so a page-wide lookup could mix the rows of the two grids without any
error. Now a lookup can only reach the picked grid.

| Window has | `grid()` is |
|---|---|
| one grid | that grid; nothing to pick, and nothing changes for these windows |
| several grids, one picked | the picked grid |
| several grids, none picked | an error at `waitUntilLoaded()` that says to pick one |

## In feature files

| Step | Does |
|---|---|
| `When I use the grid with column "Booking"` | picks the grid of the current window that has a column with that header, and waits until it has loaded |
| `When I use the grid in "bookingsGrid"` | picks the grid in an element of the page: `bookingsGrid` is a locator in the current page's locator file ([below](#grids-with-the-same-columns-pick-by-container)); waits until it has loaded |
| `When I use the only grid` | goes back to the window's single grid (clears a column or a container choice) |

After `I use the grid with column` or `I use the grid in`, **every grid step on that window** works on the picked grid:

- the general grid steps ([`GridSteps`](../src/test/java/com/agenticqa/steps/GridSteps.java)): `the grid is loaded`,
  `the grid column ... contains ...`, `I check the grid rows matching`, ...
- the Price grid steps ([`PriceSteps`](../src/test/java/com/agenticqa/steps/PriceSteps.java)): `the Price grid is
  loaded`, `the Price grid rows matching have ... in column ...`, ...
- the `PricePage` methods those steps call (`elementsInCell`, `verifySelectionShown`, `checkboxesWithPriceDiv`,
  ...), because they use `grid()` too.

No step text changes: the steps you already have keep working, on the grid you picked.

### How long a choice lasts

The choice belongs to **the window** it was made on:

- It stays until you pick another grid (by column or by container: the newer choice replaces the other), or
  `I use the only grid`, on that window.
- Other windows keep their own choice, so after `I go back to the "Price Booking" window` the Price window's
  grid is the one picked there earlier.
- When a window's page is replaced in place (opening Unique Window from Price Booking loads it into the same
  browser window), the choice is dropped: it was for the old page's grid.
- It ends with the scenario. Parallel scenarios each have their own: the choice lives in the scenario's
  `TestSession`.

### Choosing the column

Pick a column that **only that grid** has, and write its header exactly as shown (case and spaces count):

| Grids | Booking grid's columns | Customer grid's columns | Pick with |
|---|---|---|---|
| Booking and Customer | Booking, Status, Price | Customer, Status | `"Booking"` or `"Price"` for the first, `"Customer"` for the second; **not** `"Status"` |

`I use the grid with column` waits for the grid at once, so a wrong choice fails at that step, not later:

| Message | Means | Fix |
|---|---|---|
| `2 grids have a 'Status' column on <url>: pick the grid by a column only it has, or by its container ...` | both grids have that column | pick a column only one of them has, or [pick by container](#grids-with-the-same-columns-pick-by-container) |
| `Expected the grid with the column 'Bookings' to be shown within 15000 ms, on <url>` | no grid has that column (yet) | check the header text; open the tab or panel that shows the grid first |
| `2 grids on <url>: pick one by a column only it has, with the step 'I use the grid with column "Booking"' or pricePage.grid("Booking")` | a grid step ran on a window with two grids, and none was picked | add `I use the grid with column "..."` or `I use the grid in "..."` before it |

## In Java

```java
GridLibrary grid = pages.price().grid();               // the Price window's grid: the only one, or the one picked
GridLibrary bookings = pages.price().grid("Booking");  // the grid with a Booking column, whatever was picked
GridLibrary inPanel = pages.price().gridIn("bookingsGrid"); // the grid in the bookingsGrid element of pricepage.json
GridLibrary chosen = windows.current().grid();         // the current window's grid, as GridSteps uses it
new GridLibrary(page, healer, "Booking");              // the same, without a page object
new GridLibrary(page, healer, page.locator("#bookings"), "#bookings"); // a container, without a page object
```

Every method of a `GridLibrary` stays inside its grid:

```java
GridLibrary bookings = pages.price().grid("Booking").waitUntilLoaded();
bookings.contains("Booking", "Booking A");                          // waits for the value
Locator rows = bookings.rows(Map.of("Booking", "Booking A"));       // rows of this grid only
bookings.values(Map.of("Booking", "Booking A"), "Status");
bookings.elements(Map.of("Booking", "Booking A"), "checkbox").all().forEach(Locator::check);
bookings.cellIn(rows, "Price").locator("span.something");
```

`grid("Booking")` always gives the grid with that column, `gridIn("bookingsGrid")` the grid in that element;
`grid()` follows the choice of the window. A step class of
your own that uses `windows.current().grid()` or `pages.price().grid()` therefore follows `I use the grid with
column` without any extra code:

```java
@When("I tick the {string} rows")
public void tickRows(String booking) {
    GridLibrary grid = windows.current().grid().waitUntilLoaded();   // the picked grid
    grid.elements(Map.of("Booking", booking), "checkbox").all().forEach(Locator::check);
}
```

### The grid element: `root()`

For anything `GridLibrary` has no method for, `root()` is the grid element itself, as a `Locator`. Searches from it
stay inside that grid:

```java
Locator bookingsGrid = pages.price().grid("Booking").waitUntilLoaded().root();
bookingsGrid.locator("button:has-text('Export')").click();          // that grid's Export button only
```

Call `waitUntilLoaded()` first when the grid may still be loading: `root()` does not wait. On a window with two
grids and nothing picked, `grid().root()` finds both, so pick one first.

### Picking a grid from code

The step stores the choice in the scenario's session; code can do the same:

```java
session.useGrid(windows.current().window(), "Booking");   // like I use the grid with column "Booking"
session.useGridIn(windows.current().window(), "bookingsGrid"); // like I use the grid in "bookingsGrid"
session.useGrid(windows.current().window(), null);        // like I use the only grid (clears both kinds)
session.gridColumn(window);                                // the column picked for a window, or null
session.gridContainer(window);                             // the container picked for a window, or null
```

Prefer `grid("Booking")` or `gridIn("bookingsGrid")` in code: it says which grid it uses where it is used.

## Grids with the same columns: pick by container

When both grids show exactly the same columns, no column tells them apart. Pick the grid by the element it is in
instead. Add that element to the page's locator file (for the Price window, `locators/pricepage.json`):

```json
"bookingsGrid": {
  "selector": "#bookings",
  "description": "the panel around the Bookings grid",
  "heal": false
}
```

Then pick it:

```gherkin
When I use the grid in "bookingsGrid"
Then the Price grid is loaded
```

```java
GridLibrary bookings = pages.price().gridIn("bookingsGrid").waitUntilLoaded();
```

The locator can point at the element **around** the grid (a panel, a tab, a `div` with an id: `#bookings`) or at the
**grid element itself** (`#bookings .ag-root-wrapper`); either way the grid is the one in it. The name is looked up in
the locator file of the page object the step works on (`windows.current()`), so the locator belongs in that page's
file, not in `grid.json`.

| Message | Means | Fix |
|---|---|---|
| `No locator 'bookingGrid' in locators/pricepage.json or locators/common.json. Locators: [...]` (IllegalArgumentException) | the name is not in the current page's locator file | check the spelling, or add the locator |
| `Expected the grid in 'bookingsGrid' to be shown within 15000 ms, on <url>` | the element has no grid in it (yet), or the locator finds nothing | check the selector; open the tab or panel that shows the grid first |
| `2 grids in 'bookingsGrid' on <url>: make its locator match the element around one grid only` | the element holds both grids, or the locator finds several elements | make the selector narrower |

`I use the grid in` checks the grid before keeping the choice, so a wrong name or container fails at that step and
leaves the earlier choice as it was.

## Limits

- **Grids with the same columns and no element of their own.** Picking by container needs an element that holds
  one grid only. If the app renders both grids in the same parent without ids or classes that tell them apart, a
  positional selector (`#panel > div:nth-child(2)`) works but breaks when the layout changes.
- **Nested grids.** AG Grid's master/detail rows put a grid inside a row of another. The outer grid's lookups
  would also find the inner grid's rows. The windows this was built for have side-by-side grids, not nested ones.
- **Hidden grids.** A grid in a closed tab or panel still counts: it has its columns and its root. If one of the
  two is hidden, pick the visible one by its column as usual.

## Tests

[`GridLibraryTwoGridsTest`](../src/test/java/com/agenticqa/ui/GridLibraryTwoGridsTest.java) builds a page with two
visible AG-Grid-like grids that both have a row 0 (`page.setContent`, no application needed) and checks that:

- `grid()` fails clearly when the page has two grids;
- each grid is found by its own column and returns only its own values;
- rows, cells and checkboxes never come from the other grid;
- `root()` is that grid only;
- a column both grids have fails clearly;
- a page object's `grid()` follows the grid picked for its window, and "only grid" again fails clearly.

[`GridLibraryContainerTest`](../src/test/java/com/agenticqa/ui/GridLibraryContainerTest.java) builds two grids with
the **same** columns in `#left` and `#right` (containers in the test-only `src/test/resources/locators/twogridstest.json`)
and checks that:

- each grid is found by its container, whether the locator is the panel around it or the grid element itself;
- a column cannot pick them, and the message points to picking by container;
- a container with both grids, a container without a grid and an unknown locator name fail clearly;
- `grid()` follows a container picked for the window; a column choice replaces it and the other way round; "only
  grid" clears it.

```powershell
mvn test -Dtest="GridLibraryTwoGridsTest,GridLibraryContainerTest"
```
