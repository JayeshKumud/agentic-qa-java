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
| `When I use the only grid` | goes back to the window's single grid |

After `I use the grid with column`, **every grid step on that window** works on the picked grid:

- the general grid steps ([`GridSteps`](../src/test/java/com/agenticqa/steps/GridSteps.java)): `the grid is loaded`,
  `the grid column ... contains ...`, `I check the grid rows matching`, ...
- the Price grid steps ([`PriceSteps`](../src/test/java/com/agenticqa/steps/PriceSteps.java)): `the Price grid is
  loaded`, `the Price grid rows matching have ... in column ...`, ...
- the `PricePage` methods those steps call (`elementsInCell`, `verifySelectionShown`, `checkboxesWithPriceDiv`,
  ...), because they use `grid()` too.

No step text changes: the steps you already have keep working, on the grid you picked.

### How long a choice lasts

The choice belongs to **the window** it was made on:

- It stays until you pick another grid, or `I use the only grid`, on that window.
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
| `2 grids have a 'Status' column on <url>: pick the grid by a column only it has` | both grids have that column | pick a column only one of them has |
| `Expected the grid with the column 'Bookings' to be shown within 15000 ms, on <url>` | no grid has that column (yet) | check the header text; open the tab or panel that shows the grid first |
| `2 grids on <url>: pick one by a column only it has, with the step 'I use the grid with column "Booking"' or pricePage.grid("Booking")` | a grid step ran on a window with two grids, and none was picked | add `I use the grid with column "..."` before it |

## In Java

```java
GridLibrary grid = pages.price().grid();               // the Price window's grid: the only one, or the one picked
GridLibrary bookings = pages.price().grid("Booking");  // the grid with a Booking column, whatever was picked
GridLibrary chosen = windows.current().grid();         // the current window's grid, as GridSteps uses it
new GridLibrary(page, healer, "Booking");              // the same, without a page object
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

`grid("Booking")` always gives the grid with that column; `grid()` follows the choice of the window. A step class of
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
session.useGrid(windows.current().window(), null);        // like I use the only grid
session.gridColumn(window);                                // the column picked for a window, or null
```

Prefer `grid("Booking")` in code: it says which grid it uses where it is used.

## Limits

- **Grids with the same columns.** A grid is picked by a column only it has. If both grids show exactly the same
  columns, no column tells them apart; that needs picking by a container from the page's locator file (for example
  `"bookingsGrid": "#bookings .ag-root-wrapper"`), which is not built yet.
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

```powershell
mvn test -Dtest=GridLibraryTwoGridsTest
```
