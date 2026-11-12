# agentic-qa-java

A Java test automation framework for **API, UI and end-to-end** tests, written as plain-English Cucumber
scenarios. One scenario can call several REST services, drive a real browser and pass data between the two.

| | |
|---|---|
| Language | Java 17, Maven |
| Browser and HTTP | [Playwright for Java](https://playwright.dev/java/) 1.48 |
| BDD | Cucumber 7 (Gherkin feature files), PicoContainer for dependency injection |
| Runner | TestNG, with scenarios running in parallel and configurable retries |
| Config | One YAML file per environment (dev, test, uat, ci), with `${VAR}` placeholders for URLs and secrets |
| Reports | Run summary, Cucumber, Allure and Extent |
| CI | GitHub Actions, one job per test suite |

## What the framework does

- **Several environments.** `dev`, `test`, `uat` and `ci` each have their own YAML file. Choose one with `-Denv=uat`.
  URLs and secrets can come from environment variables. See [docs/running-tests.md](docs/running-tests.md).
- **Parallel and isolated.** Scenarios run on several threads (4 by default, `-Dthreads=N`). Each worker thread
  keeps one browser, and each scenario gets its own browser context, API context and data store, so
  scenarios never share state.
- **Retries of failed scenarios.** Only the failed scenario runs again, up to `retry.count` times, and only if it
  matches the `retry.tags` expression. A list of scenarios that still failed lets you re-run exactly those.
- **API and UI work in one scenario.** API calls go through the browser context, so they share cookies and
  login state with the page. Bridge steps pass a customer created through the API to the UI steps, and back.
- **Generic HTTP steps.** You can build named requests from JSON files, tables or raw text, change them over
  many steps (set, remove, deep-merge), send them to any configured service, save values from the response
  (`${id}`) and use those values in later requests. Endpoints are declared once, one line of YAML each, and
  called by name (`When I call "customers.get" ...`). In Java, `Payload` builds any body, so no request class
  or method is needed per endpoint. See [docs/api-requests.md](docs/api-requests.md).
- **Locators in JSON.** Page objects hold no selectors. They look elements up by name in
  `locators/<page>.json`. See [docs/locators.md](docs/locators.md).
- **Self-healing locators.** When a selector stops matching (for example, an id is renamed), the framework
  finds the element from a stored fingerprint, keeps the test running and reports the heal so someone
  updates the page object. See [docs/healing.md](docs/healing.md).
- **Clear reporting.** You get a one-page HTML run summary, **Allure** and **Extent** reports, the full Cucumber
  reports, a failure screenshot, a Playwright trace and the HTTP call log for every failed scenario, plus a
  GitHub job summary in CI.
  See [docs/reporting.md](docs/reporting.md).
- **Automatic cleanup.** Customers and other resources a scenario creates are deleted after it, whether it
  passed or failed.

The architecture and the flow of a test run are described in [docs/architecture.md](docs/architecture.md).

## Project layout

```
agentic-qa-java/
├── pom.xml                              dependencies, Surefire config, -Denv / -Dthreads defaults
├── healing/fingerprints.json            healing baseline (kept in git)
├── docs/                                architecture, running, API, locators, healing, reporting
├── .github/
│   ├── workflows/tests.yml              CI: starts the sample app, runs the api and ui suites
│   └── scripts/job_summary.py           turns run-summary.json into a GitHub job summary
└── src/
    ├── main/java/com/agenticqa/
    │   ├── config/                      EnvConfig (loads the YAML), Settings (typed config)
    │   ├── core/                        TestSession, BrowserPool, DataKey, Variables, JsonPaths, TestData
    │   ├── api/                         Endpoints, EndpointClient, Payload, ApiClient, ServiceClient,
    │   │                                CustomerApi, ApiState, ApiCall
    │   └── ui/                          CustomersPage (page object), LocatorRepository, PageElements
    │       └── healing/                 SmartLocator, Healer, HealingScorer, Fingerprint(Store), HealEvent
    ├── main/resources/
    │   ├── config/env/                  dev.yaml, test.yaml, uat.yaml, ci.yaml
    │   ├── endpoints/                   endpoint catalog, one YAML file per service
    │   └── locators/                    locators, one JSON file per page
    ├── test/java/com/agenticqa/
    │   ├── runner/                      TestRunner (Cucumber + TestNG, parallel), ScenarioRetry
    │   ├── hooks/Hooks.java             @Before / @After around every scenario
    │   ├── steps/                       step definitions and cleanup
    │   ├── reporting/                   RunSummaryPlugin (run summary), ReportSetupPlugin (Allure/Extent)
    │   └── core/, api/, ui/, config/,   unit tests (JsonPaths, Variables, Payload, Endpoints,
    │       healing/                     LocatorRepository, Settings, HealingScorer)
    └── test/resources/                  features/, testdata/, allure.properties, extent.properties
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

This prints the active environment and the settings it will use, with placeholders and `-D` overrides applied.

## Running tests

| Goal | Command |
|---|---|
| Everything (scenarios and unit tests) | `mvn test` |
| One suite, by tag | `mvn test -Dcucumber.filter.tags="@api"` |
| Combined tags | `mvn test -Dcucumber.filter.tags="@ui and not @healing"` |
| Smoke tests | `mvn test -Dcucumber.filter.tags="@smoke"` |
| Another environment | `mvn test -Denv=test` or `-Denv=uat` (reads `config/env/<name>.yaml`) |
| More or fewer threads | `mvn test -Dthreads=8` (`-Dthreads=1` runs one scenario at a time) |
| Retry failed scenarios | `mvn test -Dretry.count=2 -Dretry.tags="@ui"` (default: the `retry` section of the env file) |
| Re-run what failed last time | `mvn test -Dcucumber.features=@target/reports/failed-scenarios.txt` |
| Allure HTML report | `mvn allure:report` after the run (`mvn allure:serve` opens it) |
| Point one service elsewhere | `mvn test -Dservices.customers.baseUrl=http://localhost:9000` |
| Unit tests only | `mvn test -Dtest='*Test'` |

The environment can also be set with the `ENV` environment variable. The default is `dev`, which shows the
browser window (`headless: false`). `test`, `uat` and `ci` run headless. Environments, threads and retries are
described in [docs/running-tests.md](docs/running-tests.md).

### Tags in use

| Tag | Meaning |
|---|---|
| `@api` | REST-only scenarios, no browser |
| `@ui` | Scenarios that open the browser (includes the e2e ones) |
| `@e2e` | API and UI together in one scenario |
| `@healing` | Scenarios that rename ids on purpose to show self-healing |
| `@smoke` | A small, fast subset |
| `@noretry` | Never retried, even when retries are on |

### Where to look after a run

| File | What it shows |
|---|---|
| `target/reports/run-summary.html` | **Start here.** Totals, failures with screenshots, healed locators |
| `target/reports/extent/index.html` | Extent Spark report (one file, screenshots embedded) |
| `target/reports/allure/index.html` | Allure report, after `mvn allure:report` |
| `target/reports/cucumber.html` | Every step of every scenario, with attachments |
| `target/reports/failed-scenarios.txt` | Scenarios that still failed after retries, ready to re-run |
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

Then declare its endpoints in `src/main/resources/endpoints/orders.yaml`, one line each:

```yaml
service: orders
endpoints:
  create: { method: POST, path: /orders, body: orders/new_order.json }
  get:    { method: GET,  path: "/orders/{id}" }
```

Feature files call them by name (`When I call "orders.get" as "read" with:` plus a table of path parameters),
and Java code uses `new EndpointClient(session).call("orders.create", Payload.file("orders/new_order.json").set(...))`.
No client class, request record or `withX` method is needed per endpoint. See
[docs/api-requests.md](docs/api-requests.md#endpoint-catalog-call-endpoints-by-name).

### 3. A new page

1. Put its locators in `src/main/resources/locators/<page>.json`:
   ```json
   { "searchInput": { "selector": "#search-input", "description": "search input" },
     "rows":        { "selector": "#customers-table tr.customer-row", "heal": false } }
   ```
   Elements heal by default. Use `"heal": false` for elements that can legitimately be missing (result rows,
   empty states).
2. Create a page object in `src/main/java/com/agenticqa/ui/` with `new PageElements(page, healer, "<page>")` and
   use elements by name: `el.get("searchInput").fill(text)`. Steps call page object methods and never use
   selectors themselves. See [docs/locators.md](docs/locators.md).
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

Copy `src/main/resources/config/env/test.yaml` to `<name>.yaml`, change the URLs (or the variable names) and run
with `-Denv=<name>`. Reference secrets as `${NAME}` instead of writing them in the file.

## CI

[`.github/workflows/tests.yml`](.github/workflows/tests.yml) runs on every push to `main`, on pull requests
and on demand. For each suite in the matrix (`api`, `ui`) it:

1. checks out this repository and the sample app (repository variable `SAMPLE_APP_REPO`, plus secret
   `SAMPLE_APP_TOKEN` if that repository is private),
2. starts the app on port 8000 and waits for it,
3. installs Chromium (UI suite only, cached),
4. runs `mvn test -Denv=ci -Dcucumber.filter.tags="@<suite>"`,
5. builds the Allure report, writes a job summary (including scenarios that passed only after a retry), and
   uploads reports (run summary, Allure, Extent, Cucumber), heal logs, traces and the app log.

To add a suite, add its tag to `matrix.suite`.

## Documentation

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, components, the scenario lifecycle and the workflow from start to finish |
| [docs/running-tests.md](docs/running-tests.md) | Environments and placeholders, parallel threads, retries, re-running failed scenarios |
| [docs/api-requests.md](docs/api-requests.md) | Named requests, payload editing, chaining, services, all HTTP steps |
| [docs/api-steps.md](docs/api-steps.md) | How the API steps work inside: wiring, shared state, the life of a request, cleanup, adding steps |
| [docs/locators.md](docs/locators.md) | Locators in JSON files and how page objects use them |
| [docs/healing.md](docs/healing.md) | How self-healing locators and fingerprints work |
| [docs/reporting.md](docs/reporting.md) | Every report (run summary, Allure, Extent, Cucumber), attachment and CI artifact |
