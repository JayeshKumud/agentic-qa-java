# Running tests: environments, parallel and sequential runs, cleanup and retries

## Quick reference

```bash
mvn test                                              # dev environment, 4 threads, no retries
mvn test -Denv=test                                   # test environment
mvn test -Denv=uat -Dthreads=8                        # UAT, 8 scenarios at a time
mvn test -Dcucumber.filter.tags="@api and not @slow"  # a subset, by tag
mvn test -Dexecution.defaultMode=sequential           # untagged scenarios one at a time
mvn test -Dcleanup.beforeRun=false                    # keep the reports and logs of earlier runs
mvn test -Dretry.count=2 -Dretry.tags="@ui"           # retry failed @ui scenarios up to twice
mvn test -Dcucumber.features=@target/reports/failed-scenarios.txt   # only what failed last time
mvn allure:report                                     # Allure HTML from the last run
```

## Configuration: common.yaml plus one file per environment

Settings come from two files in [`src/main/resources/config`](../src/main/resources/config), merged at startup:

| File | Holds |
|---|---|
| [`common.yaml`](../src/main/resources/config/common.yaml) | what every environment shares: browser, headless, viewport, **wait times**, API timeout, default headers, the **endpoint catalog**, execution mode, cleanup, healing, retry defaults |
| `env/<name>.yaml` | only what differs per environment: **URLs**, **auth (client id, client secret, token URL)**, and the odd override (headless off in dev, longer waits in UAT, retry count) |

The environment file is merged on top of `common.yaml`: a key it sets wins, sections are merged key by key
(setting `ui.waits.actionMs` keeps `ui.waits.navigationMs` from `common.yaml`), and lists are replaced.

| Environment file | Used for | Browser | Retries | Auth |
|---|---|---|---|---|
| `dev.yaml` | local runs against the sample app on `127.0.0.1:8000` (default) | visible | off | none |
| `test.yaml` | the shared test environment | headless | 1 | OAuth2 client credentials |
| `uat.yaml` | user acceptance testing | headless | 2 | OAuth2 client credentials |
| `ci.yaml` | GitHub Actions, sample app started by the workflow | headless | 1 | none |

Choose one with `-Denv=<name>` or the `ENV` environment variable. An unknown name fails at startup and lists
the known ones.

### Browser and wait times (common.yaml)

```yaml
ui:
  browser: chromium          # chromium, firefox or webkit
  headless: true
  slowMoMs: 0
  viewport: { width: 1366, height: 768 }
  waits:
    actionMs: 15000          # click, fill, locator waits      -> context.setDefaultTimeout
    navigationMs: 30000      # page.navigate, load             -> context.setDefaultNavigationTimeout
    assertionMs: 15000       # assertThat(...) web assertions  -> PlaywrightAssertions.setDefaultAssertionTimeout
api:
  timeoutMs: 15000           # one HTTP request, for services without their own timeoutMs
```

An environment file overrides only what it needs; for example `uat.yaml` sets longer waits.

### Endpoints (common.yaml)

Endpoint paths are the same in every environment, so the catalog lives in `common.yaml`, once. Only the
service's base URL and auth change per environment. See
[api-requests.md](api-requests.md#endpoint-catalog-call-endpoints-by-name).

### URLs, client ids and secrets (environment files)

```yaml
services:
  customers:
    baseUrl: ${UAT_CUSTOMERS_URL:-https://uat-api.customers.example.com}
    auth:
      type: oauth2                                # none, oauth2, basic or bearer
      tokenUrl: ${UAT_AUTH_URL:-https://uat-auth.example.com/oauth2/token}
      clientId: ${UAT_CUSTOMERS_CLIENT_ID}        # no fallback: the run fails at startup if it is not set
      clientSecret: ${UAT_CUSTOMERS_CLIENT_SECRET}
      scope: customers.read customers.write
```

- `${NAME}` is filled from the system property `NAME` (`-DNAME=...`), or else the environment variable `NAME`.
- `${NAME:-fallback}` uses the fallback when neither is set.
- A placeholder without fallback and without value fails at startup with a message naming the variable, so a
  run never silently sends requests without its credentials.
- Placeholders are filled after the YAML is parsed, so commented-out lines are never checked.

Keep secrets out of the files: reference them as `${NAME}` and set them as CI secrets or local environment
variables (`$env:UAT_CUSTOMERS_CLIENT_SECRET = "..."` in PowerShell).

Auth types, handled by [`ServiceAuth`](../src/main/java/com/agenticqa/api/ServiceAuth.java):

| `type` | Needs | Sent with every request to the service |
|---|---|---|
| `none` (default) | nothing | nothing |
| `oauth2` | `tokenUrl`, `clientId`, `clientSecret`, optional `scope` | `Authorization: Bearer <token>`; the token is fetched once per run with the client credentials grant, shared by all threads, and renewed 60 s before it expires |
| `basic` | `username`, `password` | `Authorization: Basic ...` |
| `bearer` | `token` | `Authorization: Bearer <token>` |

Secrets never reach a log or report: `Authorization`, `Cookie` and API key headers show as `****` in the API call
log attached to failed scenarios, and `ConfigCheck` masks the secret fields. A step can still send its own
`Authorization` header (`the "x" request header "Authorization" is "Bearer wrong"`), which wins, for negative
tests.

### One-off overrides

Any setting can be overridden for one run with a system property named by its path, without editing a file:

| Property | Example |
|---|---|
| `ui.baseUrl` | `-Dui.baseUrl=https://feature-123.test.example.com` |
| `ui.browser` | `-Dui.browser=firefox` |
| `ui.headless` | `-Dui.headless=false` (watch a UAT run locally) |
| `ui.waits.actionMs` | `-Dui.waits.actionMs=30000` |
| `services.<name>.baseUrl` | `-Dservices.customers.baseUrl=http://localhost:9000` |
| `execution.defaultMode` | `-Dexecution.defaultMode=sequential` |
| `cleanup.beforeRun` | `-Dcleanup.beforeRun=false` |
| `retry.count`, `retry.tags` | see [Retries](#retries-of-failed-scenarios) |

Print what a run will use, with both files merged, placeholders and overrides applied, and secrets masked:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.agenticqa.ConfigCheck -Denv=uat
```

### Adding an environment

Copy `test.yaml` to `<name>.yaml`, change the hosts (or the variable names) and run with `-Denv=<name>`. Add
the name to `EnvConfig.KNOWN` so it shows in the error message for unknown names. Anything the new environment
shares with the others stays in `common.yaml`.

## Parallel or sequential: `@Parallel` and `@Sequential`

Tag a feature (all its scenarios) or a single scenario:

```gherkin
@api @Parallel
Feature: A zone id set in one step and read in the next steps

  Scenario: Runs side by side with the other parallel scenarios
    ...

  @Sequential
  Scenario: Runs alone
    ...
```

| Tags of the scenario (its own plus the feature's) | How it runs |
|---|---|
| `@Sequential` (also together with `@Parallel`) | alone, one after the other, after all parallel scenarios |
| `@Parallel` | in parallel, up to `-Dthreads` at once |
| neither | `execution.defaultMode` in `common.yaml` (`parallel`) |

Tags are compared ignoring case, but must be spelled `@Sequential` and `@Parallel`. `TestRunner` has two TestNG
test methods: `runParallelScenario` with a `parallel = true` data provider, and `runSequentialScenario` with a
`parallel = false` one. Each data provider picks its scenarios with `Settings.Execution.modeOf(tags)`. TestNG
runs the two methods one after the other, so while the sequential scenarios run, nothing else does. Use
`@Sequential` for scenarios that change shared state (global settings, one fixed account, a whole table) and
would disturb others. The console shows the split: `[EXECUTION] 30 scenario(s) run in parallel`,
`[EXECUTION] 1 scenario(s) run sequentially`.

## Clean logs and reports before each run

Before a run starts, `TestRunner` (in `@BeforeSuite`, so before Cucumber opens any report file) empties the
folders under `cleanup` in `common.yaml`:

```yaml
cleanup:
  beforeRun: true
  folders: [target/reports, target/allure-results, target/artifacts, target/healing, target/logs]
  logFile: target/logs/run.log
```

So the reports, traces, heal log and run log hold only the new run, also without `mvn clean`. Everything
printed during the run (`[API]`, `[BROWSER]`, `[AUTH]`, `[RETRY]`, `[ZONE]` lines, Cucumber's output, errors) is
also written to `target/logs/run.log`.

- `-Dcleanup.beforeRun=false` keeps everything from earlier runs.
- The rerun file is kept: with `-Dcucumber.features=@target/reports/failed-scenarios.txt`, that file survives
  the cleanup, because Cucumber reads it afterwards.
- Only folders inside the project are accepted; an absolute path or `..` fails the run.
- `healing/fingerprints.json` (the healing baseline, in git) is outside `target` and is never touched.

## Parallel threads

`TestRunner`'s parallel data provider (`parallelScenarios`) is `parallel = true`, and Surefire sets TestNG's `dataproviderthreadcount` to
`${threads}` (default 4 in `pom.xml`). TestNG therefore runs up to N scenarios at once, each one whole on one
thread. `-Dthreads=1` runs them one at a time, which helps when debugging. Scenarios tagged `@Sequential` never
run in that pool; see [Parallel or sequential](#parallel-or-sequential-parallel-and-sequential).

What keeps parallel scenarios apart:

| Concern | How it is handled |
|---|---|
| Browser | [`BrowserPool`](../src/main/java/com/agenticqa/core/BrowserPool.java): one Playwright and one browser **per worker thread**, used only by that thread (Playwright objects are not thread-safe). Every scenario gets a **new browser context**, so it has its own cookies, storage, page and trace. With 4 threads at most 4 browsers run, whatever the number of scenarios. A crashed browser is replaced, and all browsers are closed when the JVM exits. |
| Scenario state | PicoContainer creates new `TestSession`, `ApiState`, `Variables`, page objects and cleanup objects for each scenario. Nothing is static. |
| Test data | `{unique}` and `TestData.uniqueName` add a random token per scenario, so parallel scenarios never find each other's records. Each scenario deletes what it created. |
| Shared files | The fingerprint store and `heals.jsonl` are synchronized. Locator files and the settings (with the endpoint catalog) are read once and are read-only. Trace file names include the thread name. |
| Reports | All report plugins (Cucumber, run summary, Allure, Extent) are made for parallel events. Console lines carry the thread name: `[API] [TestNG-PoolService-2] GET ...`. |

Pick the thread count for the machine and the app. UI scenarios cost about one browser tab each, and API-only
scenarios never start a browser.

## Retries of failed scenarios

A failed scenario can run again, immediately. Only that scenario runs again: other scenarios of the feature,
and scenarios that passed, are not repeated. This is done by
[`ScenarioRetry`](../src/test/java/com/agenticqa/runner/ScenarioRetry.java), a TestNG retry analyzer on
`TestRunner.runParallelScenario` and `TestRunner.runSequentialScenario`.

### Configuration

In `common.yaml` (default for all environments), overridden per environment file where needed:

```yaml
retry:
  count: 2                 # how often a failed scenario may run again; 0 = retries off
  tags: "not @noretry"     # Cucumber tag expression: which scenarios may be retried; "" = all
```

Or per run, overriding the file: `-Dretry.count=1 -Dretry.tags="@ui and not @payments"`.

Choosing which tests are retried:

| Goal | `retry.tags` |
|---|---|
| everything except scenarios tagged `@noretry` | `not @noretry` (the default in the files) |
| only UI scenarios (where flakiness usually comes from) | `@ui` |
| only scenarios you have marked as known to be flaky | `@flaky` |
| everything | `""` |

Tags on the feature count too, so `@noretry` above `Feature:` excludes the whole file. Put `@noretry` on
scenarios whose failure must never be hidden, for example scenarios that are not safe to repeat.

### What a retry does

- Each attempt is a full new scenario run: new step objects, a new browser context, a new `{unique}` token and
  its own cleanup. Data from the failed attempt is cleaned up by that attempt's `@After` hooks.
- The console shows `[RETRY] <scenario> (<file>:<line>) failed; running it again, retry 1 of 2`.
- TestNG marks the earlier attempts as **skipped** and the build result follows the **last** attempt. A
  scenario that passes on retry does not fail the build.

### How retries show in the reports

| Report | Shows |
|---|---|
| `run-summary.html` / `.json` | One row per scenario (its last attempt), with an "N attempts" chip. "Passed after retry" tile and `flaky` count. The GitHub job summary lists those scenarios and adds a warning for each. |
| Allure | One test with the earlier attempts in its **Retries** tab. |
| Extent, `cucumber.html` | Every attempt as its own entry (these reports have no notion of retries). |
| `failed-scenarios.txt` | Only scenarios that still failed after all retries. |

A scenario that often passes only on retry is flaky. Fix it rather than raising `retry.count`.

### Re-running only the failed scenarios later

Every run writes `target/reports/failed-scenarios.txt` in Cucumber's rerun format (feature file plus scenario
lines). It lists only the scenarios that still failed after retries. To run exactly those again, for example
after a fix or once an environment is back:

```bash
mvn test -Denv=uat -Dcucumber.features=@target/reports/failed-scenarios.txt
```

The file is empty when everything passed. Copy it elsewhere first if you want to keep it, because the next run
overwrites it.
