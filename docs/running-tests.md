# Running tests: environments, parallel threads and retries

## Quick reference

```bash
mvn test                                              # dev environment, 4 threads, no retries
mvn test -Denv=test                                   # test environment
mvn test -Denv=uat -Dthreads=8                        # UAT, 8 scenarios at a time
mvn test -Dcucumber.filter.tags="@api and not @slow"  # a subset, by tag
mvn test -Dretry.count=2 -Dretry.tags="@ui"           # retry failed @ui scenarios up to twice
mvn test -Dcucumber.features=@target/reports/failed-scenarios.txt   # only what failed last time
mvn allure:report                                     # Allure HTML from the last run
```

## Environments

Each environment is one YAML file in [`src/main/resources/config/env`](../src/main/resources/config/env):

| File | Used for | Browser | Retries |
|---|---|---|---|
| `dev.yaml` | local runs against the sample app on `127.0.0.1:8000` (default) | visible | off |
| `test.yaml` | the shared test environment | headless | 1 |
| `uat.yaml` | user acceptance testing | headless | 2 |
| `ci.yaml` | GitHub Actions, sample app started by the workflow | headless | 1 |

Choose one with `-Denv=<name>` or the `ENV` environment variable. An unknown name fails at startup and lists
the known ones. Every file has the same sections: `ui`, `api`, `services`, `healing` and `retry`.

### URLs and secrets: placeholders

`test.yaml` and `uat.yaml` ship with placeholder hosts (`*.example.com`). Either replace the fallbacks with your
real hosts, or leave the file as it is and set variables per machine or CI job:

```yaml
ui:
  baseUrl: ${UAT_UI_URL:-https://uat.customers.example.com}
services:
  customers:
    baseUrl: ${UAT_CUSTOMERS_URL:-https://uat-api.customers.example.com}
    headers:
      X-Api-Key: ${UAT_CUSTOMERS_API_KEY}        # no fallback: the run fails at startup if it is not set
```

- `${NAME}` is filled from the system property `NAME` (`-DNAME=...`), or else the environment variable `NAME`.
- `${NAME:-fallback}` uses the fallback when neither is set.
- A placeholder without fallback and without value fails at startup with a message naming the variable, so a
  run never silently sends requests without its API key.
- Placeholders are filled after the YAML is parsed, so commented-out lines are never checked.

Keep secrets out of the files: reference them as `${NAME}` and set them as CI secrets.

### One-off overrides

These system properties win over the file for one run, without editing it:

| Property | Example |
|---|---|
| `ui.baseUrl` | `-Dui.baseUrl=https://feature-123.test.example.com` |
| `ui.browser` | `-Dui.browser=firefox` |
| `ui.headless` | `-Dui.headless=false` (watch a UAT run locally) |
| `services.<name>.baseUrl` | `-Dservices.customers.baseUrl=http://localhost:9000` |
| `retry.count`, `retry.tags` | see [Retries](#retries-of-failed-scenarios) |

Print what a run will use, with placeholders and overrides applied:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.agenticqa.ConfigCheck -Denv=uat
```

### Adding an environment

Copy `test.yaml` to `<name>.yaml`, change the hosts (or the variable names) and run with `-Denv=<name>`. Add
the name to `EnvConfig.KNOWN` so it shows in the error message for unknown names.

## Parallel threads

`TestRunner`'s data provider is `parallel = true`, and Surefire sets TestNG's `dataproviderthreadcount` to
`${threads}` (default 4 in `pom.xml`). TestNG therefore runs up to N scenarios at once, each one whole on one
thread. `-Dthreads=1` runs them one at a time, which helps when debugging.

What keeps parallel scenarios apart:

| Concern | How it is handled |
|---|---|
| Browser | [`BrowserPool`](../src/main/java/com/agenticqa/core/BrowserPool.java): one Playwright and one browser **per worker thread**, used only by that thread (Playwright objects are not thread-safe). Every scenario gets a **new browser context**, so it has its own cookies, storage, page and trace. With 4 threads at most 4 browsers run, whatever the number of scenarios. A crashed browser is replaced, and all browsers are closed when the JVM exits. |
| Scenario state | PicoContainer creates new `TestSession`, `ApiState`, `Variables`, page objects and cleanup objects for each scenario. Nothing is static. |
| Test data | `{unique}` and `TestData.uniqueName` add a random token per scenario, so parallel scenarios never find each other's records. Each scenario deletes what it created. |
| Shared files | The fingerprint store and `heals.jsonl` are synchronized. Locator and endpoint files are read once and are read-only. Trace file names include the thread name. |
| Reports | All report plugins (Cucumber, run summary, Allure, Extent) are made for parallel events. Console lines carry the thread name: `[API] [TestNG-PoolService-2] GET ...`. |

Pick the thread count for the machine and the app. UI scenarios cost about one browser tab each, and API-only
scenarios never start a browser.

## Retries of failed scenarios

A failed scenario can run again, immediately. Only that scenario runs again: other scenarios of the feature,
and scenarios that passed, are not repeated. This is done by
[`ScenarioRetry`](../src/test/java/com/agenticqa/runner/ScenarioRetry.java), a TestNG retry analyzer on
`TestRunner.runScenario`.

### Configuration

In the environment file:

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
