# agentic-qa-java

A Java test automation framework for **API, UI and end-to-end** tests, written as plain-English Cucumber
scenarios. One scenario can call several REST services, drive a real browser and pass data between the two.

| | |
|---|---|
| Language | Java 17, Maven |
| Browser and HTTP | [Playwright for Java](https://playwright.dev/java/) 1.48 |
| BDD | Cucumber 7 (Gherkin feature files), PicoContainer for dependency injection |
| Runner | TestNG, with scenarios running in parallel |
| Config | One YAML file per environment |
| CI | GitHub Actions, one job per test suite |

## What the framework does

- **Each scenario is isolated.** Every scenario gets its own `TestSession` with its own browser, API context
  and data store. Scenarios can therefore run in parallel (4 threads by default) without sharing state.
- **API and UI work in one scenario.** API calls go through the browser context, so they share cookies and
  login state with the page. Bridge steps pass a customer created through the API to the UI steps, and back.
- **Generic HTTP steps.** You can build named requests from JSON files, tables or raw text, change them over
  many steps (set, remove, deep-merge), send them to any configured service, save values from the response
  (`${id}`) and use those values in later requests. None of this needs Java per endpoint.
  See [docs/api-requests.md](docs/api-requests.md).
- **Self-healing locators.** When a selector stops matching (for example, an id is renamed), the framework
  finds the element from a stored fingerprint, keeps the test running and reports the heal so someone
  updates the page object. See [docs/healing.md](docs/healing.md).
- **Clear reporting.** You get a one-page HTML run summary, the full Cucumber reports, a failure screenshot,
  a Playwright trace and the HTTP call log for every failed scenario, plus a GitHub job summary in CI.
  See [docs/reporting.md](docs/reporting.md).
- **Automatic cleanup.** Customers and other resources a scenario creates are deleted after it, whether it
  passed or failed.

The architecture and the flow of a test run are described in [docs/architecture.md](docs/architecture.md).

## Project layout

```
agentic-qa-java/
├── pom.xml                              dependencies, Surefire config, -Denv / -Dthreads defaults
├── healing/fingerprints.json            healing baseline (kept in git)
├── docs/                                architecture, API requests, healing, reporting
├── .github/
│   ├── workflows/tests.yml              CI: starts the sample app, runs the api and ui suites
│   └── scripts/job_summary.py           turns run-summary.json into a GitHub job summary
└── src/
    ├── main/java/com/agenticqa/
    │   ├── config/                      EnvConfig (loads the YAML), Settings (typed config)
    │   ├── core/                        TestSession, DataKey, Variables, JsonPaths, TestData
    │   ├── api/                         ApiClient (base), CustomerApi, ServiceClient, ApiState, ApiCall
    │   └── ui/                          CustomersPage (page object)
    │       └── healing/                 SmartLocator, Healer, HealingScorer, Fingerprint(Store), HealEvent
    ├── main/resources/config/env/       dev.yaml, ci.yaml
    ├── test/java/com/agenticqa/
    │   ├── runner/TestRunner.java       Cucumber + TestNG entry point (parallel)
    │   ├── hooks/Hooks.java             @Before / @After around every scenario
    │   ├── steps/                       step definitions and cleanup
    │   ├── reporting/RunSummaryPlugin   run-summary.html / .json
    │   └── core/, healing/              unit tests (JsonPaths, Variables, HealingScorer)
    └── test/resources/
        ├── features/{api,ui,e2e}/       Gherkin feature files
        └── testdata/                    JSON payloads used by the steps
```

## Setup

### Prerequisites

- **JDK 17** or newer (`java -version`)
- **Maven 3.8+** (`mvn -v`)
- **The application under test.** The tests target the sample FastAPI app in the separate
  `sample-target-app` repository. It needs [uv](https://docs.astral.sh/uv/) (and Python, which uv can
  install for you).

### 1. Start the application under test

```bash
cd ../sample-target-app
uv sync
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Check that it is up: <http://127.0.0.1:8000/> shows the customers page, and
<http://127.0.0.1:8000/docs> shows the API.

### 2. Install the dependencies and the browser

```bash
mvn -q test-compile
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

On Linux, use `install --with-deps chromium` to also install the system libraries. API scenarios also
launch a browser, because their HTTP calls go through the browser context to share cookies. If the
browser is missing, Playwright downloads it on first use.

### 3. Check the configuration (optional)

```bash
mvn -q compile exec:java -Dexec.mainClass=com.agenticqa.ConfigCheck -Denv=dev
```

This prints the active environment and the settings loaded from it.

## Running tests

| Goal | Command |
|---|---|
| Everything (scenarios and unit tests) | `mvn test` |
| One suite, by tag | `mvn test -Dcucumber.filter.tags="@api"` |
| Combined tags | `mvn test -Dcucumber.filter.tags="@ui and not @healing"` |
| Smoke tests | `mvn test -Dcucumber.filter.tags="@smoke"` |
| Another environment | `mvn test -Denv=ci` (reads `config/env/ci.yaml`) |
| More or fewer threads | `mvn test -Dthreads=8` |
| Point one service elsewhere | `mvn test -Dservices.customers.baseUrl=http://localhost:9000` |
| Unit tests only | `mvn test -Dtest='*Test'` |

The environment can also be set with the `ENV` environment variable. The default is `dev`, which shows the
browser window (`headless: false`). `ci` runs headless.

### Tags in use

| Tag | Meaning |
|---|---|
| `@api` | REST-only scenarios, no browser |
| `@ui` | Scenarios that open the browser (includes the e2e ones) |
| `@e2e` | API and UI together in one scenario |
| `@healing` | Scenarios that rename ids on purpose to show self-healing |
| `@smoke` | A small, fast subset |

### Where to look after a run

| File | What it shows |
|---|---|
| `target/reports/run-summary.html` | **Start here.** Totals, failures with screenshots, healed locators |
| `target/reports/cucumber.html` | Every step of every scenario, with attachments |
| `target/artifacts/traces/*.zip` | Playwright trace of each failed scenario. Open it with `npx playwright show-trace <zip>` or at <https://trace.playwright.dev> |
| `target/healing/heals.jsonl` | One line per healed locator |

## Writing tests

### 1. A scenario using existing steps

Most new tests need no Java. Add a `.feature` file under `src/test/resources/features/<area>/` and tag it:

```gherkin
@api
Feature: Orders

  Scenario: Create a customer from a file and change it before sending
    Given a new "c" request from "customers/new_customer.json"
    And the "c" request field "status" is set to "inactive"
    When I send the "c" request as POST to "/customers" on the "customers" service
    Then the "c" response status is 201
    And I save the "c" response field "id" as "id"
    And "/customers/${id}" on the "customers" service is deleted after the scenario
    When I send GET "/customers/${id}" to the "customers" service as "read"
    Then the "read" response field "status" is "inactive"
```

The full list of generic steps, path syntax (`a.b[0]`, `items[]`, `items[-1]`), typed values (`json:3`) and
variables (`{unique}`, `${name}`) is in [docs/api-requests.md](docs/api-requests.md).

### 2. A new service

Add it to every environment file. The steps then address it by name:

```yaml
services:
  orders:
    baseUrl: http://127.0.0.1:8001
    timeoutMs: 30000          # optional, defaults to api.timeoutMs
    headers:
      X-Api-Key: change-me    # sent with every request
```

For business-level steps (such as `I create a customer named "Ada"`), add a typed client that extends
`ApiClient`, as `CustomerApi` does. `ApiClient` takes care of URLs, timeouts, headers, JSON and the call log.

### 3. A new page

1. Create a page object in `src/main/java/com/agenticqa/ui/`. Declare each element that is always on the page
   as a `SmartLocator` with a stable key, a readable description and its selector:
   ```java
   searchInput = new SmartLocator(page, healer, "customers.searchInput", "search input", "#search-input");
   ```
   Use plain Playwright `Locator`s for elements that can legitimately be missing (result rows, empty states).
2. Steps call page object methods and never use selectors themselves.
3. Commit `healing/fingerprints.json` after the first green run, because it is the baseline healing compares against.

### 4. New step definitions

Put them in `src/test/java/com/agenticqa/steps/`. Take what you need through the constructor, and
PicoContainer creates one instance per scenario:

```java
public class OrderSteps {
    private final TestSession session;
    private final ApiState api;

    public OrderSteps(TestSession session, ApiState api) {
        this.session = session;
        this.api = api;
    }
}
```

To share data between step classes, use `session.put(key, value)` / `session.get(key)` with a typed `DataKey`
(see `steps/Keys.java`). Register anything the scenario creates for cleanup.

### 5. A new environment

Copy `src/main/resources/config/env/dev.yaml` to `<name>.yaml`, change the URLs and run with `-Denv=<name>`.

## CI

[`.github/workflows/tests.yml`](.github/workflows/tests.yml) runs on every push to `main`, on pull requests
and on demand. For each suite in the matrix (`api`, `ui`) it:

1. checks out this repository and the sample app (repository variable `SAMPLE_APP_REPO`, plus secret
   `SAMPLE_APP_TOKEN` if that repository is private),
2. starts the app on port 8000 and waits for it,
3. installs Chromium (UI suite only, cached),
4. runs `mvn test -Denv=ci -Dcucumber.filter.tags="@<suite>"`,
5. writes a job summary and uploads reports, heal logs, traces and the app log.

To add a suite, add its tag to `matrix.suite`.

## Documentation

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, components, the scenario lifecycle and the workflow from start to finish |
| [docs/api-requests.md](docs/api-requests.md) | Named requests, payload editing, chaining, services, all HTTP steps |
| [docs/healing.md](docs/healing.md) | How self-healing locators and fingerprints work |
| [docs/reporting.md](docs/reporting.md) | Every report, attachment and CI artifact |
