# Reporting

A test run produces evidence at four levels. Each level builds on the one before it:

1. **Per scenario.** The `@After` hook adds healed locators, a failure screenshot and the trace path to the scenario.
2. **Per run.** Cucumber's built-in reports, our own `RunSummaryPlugin` (`run-summary.html` / `.json`, the failed-scenario list and a console line), and the **Allure** and **Extent** reports.
3. **Per CI job.** `job_summary.py` turns the JSON into a GitHub job summary and warning annotations.
4. **Artifacts.** The workflow uploads reports, heal logs, fingerprints, traces and the app log.

Everything is local files under `target/`. No reporting server is involved.

## Output at a glance

| File | Written by | What it is for |
|---|---|---|
| `target/reports/run-summary.html` | `RunSummaryPlugin` | **Start here.** One self-contained page with totals, failures (error, screenshot, trace path), healed locators and every scenario grouped by feature. |
| `target/reports/run-summary.json` | `RunSummaryPlugin` | The same data, machine-readable. Read by CI. |
| `target/reports/cucumber.html` | Cucumber `html` plugin | Every step of every scenario, with all attachments and full stack traces. |
| `target/reports/cucumber.json` | Cucumber `json` plugin | Standard Cucumber JSON for other tools (Xray, …). |
| `target/reports/failed-scenarios.txt` | `RunSummaryPlugin` | Scenarios that still failed after retries, in rerun format: `-Dcucumber.features=@target/reports/failed-scenarios.txt` runs exactly those (see [running-tests.md](running-tests.md#re-running-only-the-failed-scenarios-later)). |
| `target/reports/extent/index.html` | Extent Cucumber adapter | **Extent Spark** report: dashboard, features and scenarios with steps, tags as categories, screenshots embedded. One file. |
| `target/allure-results/` | Allure Cucumber plugin | Raw **Allure** results, plus `environment.properties`. Turn them into HTML with `mvn allure:report`. |
| `target/reports/allure/index.html` | `mvn allure:report` | **Allure** report: overview, suites, timeline, retries, categories, environment. |
| `target/artifacts/traces/<time>_<scenario>_<thread>.zip` | `TestSession.close` | Playwright trace, **failed scenarios only**. |
| `target/artifacts/videos/<time>_<scenario>_<thread>.webm` | `TestSession.close` | Video of the page, by `ui.video`: **failed scenarios only** (default), `always` or `off`. Its path is attached to the report as `video`. |
| `target/healing/heals.jsonl` | `Healer` | One JSON line per healed locator (see [healing.md](healing.md)). |
| `target/surefire-reports/` | Maven Surefire | Plain TestNG results, including unit tests such as `HealingScorerTest`. |
| console | `pretty` plugin, `Healer`, `RunSummaryPlugin` | Steps as they run, `[HEAL]` lines, `Trace saved: …`, and the final `[REPORT]` line. |

All of these are configured in [`TestRunner`](../src/test/java/com/agenticqa/runner/TestRunner.java):

```java
@CucumberOptions(
        plugin = {"pretty",
                "com.agenticqa.reporting.ReportSetupPlugin",
                "html:target/reports/cucumber.html",
                "json:target/reports/cucumber.json",
                "com.agenticqa.reporting.RunSummaryPlugin:target/reports/run-summary.html",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm",
                "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:"}, ...)
```

The text after the colon is passed to the plugin's constructor, so you change the output location there. The JSON file is always written next to the HTML file, with the same base name.

## Complete flow

```mermaid
flowchart TD
    subgraph Scenario["Each scenario (own thread, own TestSession)"]
        S1["@Before: remember scenario name"] --> S2["Steps run<br/>(browser starts lazily, tracing on)"]
        S2 --> S3["Healer records HealEvents<br/>prints [HEAL], appends heals.jsonl"]
        S2 --> S4["@After hook"]
        S4 --> A1["attach 'self-healing' (text)<br/>if anything healed"]
        S4 --> A2["attach 'failure-screenshot' (PNG)<br/>if failed and a page was opened"]
        S4 --> A3["session.close(failed):<br/>save trace zip if failed"]
        A3 --> A4["attach 'trace' (path text)"]
    end

    A1 & A2 & A4 -->|EmbedEvent| P["RunSummaryPlugin<br/>(listens to Cucumber events)"]
    S2 -->|TestCaseStarted / Finished| P
    A1 & A2 & A4 --> C["cucumber.html / cucumber.json"]

    P -->|TestRunFinished| R1["run-summary.html"]
    P --> R2["run-summary.json"]
    P --> R3["console: [REPORT] …"]

    R2 --> J["job_summary.py (CI)"]
    J --> G1["GitHub job summary (Markdown)"]
    J --> G2["::warning:: annotation per unexpected heal"]
    R1 & R2 & C --> U["upload-artifact: reports-&lt;suite&gt;"]
```

## 1. Per scenario: what gets collected

### Tracing (always on, kept only on failure)

When a step first needs the page or the API, [`TestSession`](../src/main/java/com/agenticqa/core/TestSession.java) starts the browser and creates its context. It also starts Playwright tracing, with screenshots and DOM snapshots but no sources. API-only scenarios get a context too, because `api()` uses `context().request()`, so they are traced as well.

At the end `session.close(failed)` does one of two things:

- **On failure:** it stops tracing into `target/artifacts/traces/2026-11-09_14-05-09_create-and-find-a-customer_TestNG-PoolService-1.zip`, built from a timestamp, a slug of the scenario name and the thread name (so parallel or retried scenarios with the same name do not overwrite each other). It prints `Trace saved: …` and returns the path.
- **On a pass:** it stops tracing and throws the trace away, so passing runs leave no zip files behind.

Each close step is wrapped separately, so a cleanup error can never hide the real test result.

To open a trace: `npx playwright show-trace "<path>"`, or drag the zip onto <https://trace.playwright.dev>.

### The hooks ([`TestInitializeHooks`](../src/test/java/com/agenticqa/hooks/TestInitializeHooks.java))

Adapted from the existing framework's `TestInitializeHooks`. `@Before` hooks run lowest order first, `@After`
hooks highest order first:

| Hook | Order | What it does |
|---|---|---|
| `bindSession` | `@Before` 1 | starts the scenario clock, binds the `TestSession` to the thread |
| `checkTags` | `@Before` 2 | logs the tags; a tag containing `@MUTED` skips the scenario (`SkipException`); otherwise the first tag starting with `@PRGBATR-` must be `@PRGBATR-` plus 4 to 6 digits, or the scenario fails |
| `setUpBaseData` | `@Before` 3 | creates `TestDataSetup` (placeholder) |
| `afterStep` | `@AfterStep` | a screenshot after every step of a scenario that opened a page, named after the scenario |
| `attachHealsAndFailureScreenshot` | `@After` 7 | the attachments below, while the page is still open |
| `collectDebugInformation` | `@After` 6 | on failure, `DebugInformationCollector`: page URL and title, last API response, attached to Allure as `debug-info` |
| `saveVideo` | `@After` 5 | on failure with a page, closes the browser context first, so the video is complete |
| `closeBrowser` | `@After` 4 | `session.close(failed)`; a kept trace zip and video are attached as paths, and as files to Allure. `close` also deletes this scenario's raw recording in `target/artifacts/videos/recording` (never the whole folder: parallel scenarios share it) |
| `ServiceCleanup.cleanUp` | `@After` 2 | deletes the test data registered by the steps, newest first |
| `teardownData` | `@After` 1 | deletes the zone in `Keys.ZONE_ID` (not for `@TelephoneInstructions`) with `UnitManagerApi` / `EnergyProfileCalculatorApi` (placeholders), then records the result in `testExecutionMap` |

[`JsonReporter`](../src/test/java/com/agenticqa/reporting/JsonReporter.java) (a Cucumber plugin) turns
`testExecutionMap` into `target/reports/test-execution-summary.json` when the run finishes. `TestRunner.afterSuite`
publishes the reports in the pipeline (`ArtifactoryPublisher`, `AllurePublisher`: placeholders), adds their URLs
and prints the summary. `TestRunner`'s static block also registers `ExtentReportLogFilter` and
`AllureReportLogFilter`, which log every REST Assured request and response in the Extent step and as Allure
attachments.

**Placeholders.** `FeatureAwareManager` (runs every scenario), `ArtifactoryPublisher`, `AllurePublisher`,
`UnitManagerApi`, `EnergyProfileCalculatorApi` and `TestDataSetup` keep the names of the company classes but do
nothing except log a warning. Paste the real code into them.

Attachments of `attachHealsAndFailureScreenshot` and `closeBrowser`, made with `scenario.attach(data, mediaType, name)`:

| Attachment name | Media type | When | Content |
|---|---|---|---|
| `self-healing` | `text/plain` | at least one locator healed | `Locators healed - update the locator file (src/main/resources/locators):` followed by one `HealEvent.summary()` per line |
| `failure-screenshot` | `image/png` | scenario failed **and** a page was opened (`session.hasPage()`) | full-page screenshot. If it fails, the error is ignored so the real failure stays visible. |
| `trace` | `text/plain` | a trace zip was saved (failure with a browser context) | absolute path of the zip |
| `video` | `text/plain` | a video was kept (`ui.video`) | absolute path of the webm |

The `hasPage()` check makes sure the hooks never open a browser only to take a screenshot of an API-only scenario.

**The attachment names are a contract.** `RunSummaryPlugin` recognises the `trace` and `self-healing` attachments by name, and keeps only the image named `failure-screenshot` (the step screenshots stay in the other reports). If you rename an attachment in the hook, change the plugin too.

## 2. Per run: `RunSummaryPlugin`

[`RunSummaryPlugin`](../src/test/java/com/agenticqa/reporting/RunSummaryPlugin.java) is a Cucumber `ConcurrentEventListener`. Scenarios run in parallel (`-Dthreads=N`, default 4), so events arrive from many threads. All state lives in `ConcurrentHashMap`s keyed by test case id, and the per-scenario lists are `CopyOnWriteArrayList`s.

### Events it listens to

| Event | What the plugin does |
|---|---|
| `TestRunStarted` | Records the start time, which is used to measure the run's wall-clock time. |
| `TestSourceRead` | Reads the `Feature:` line of each `.feature` file, to group scenarios by feature name. Falls back to the file name. |
| `TestCaseStarted` | Creates a `ScenarioResult` for the scenario. |
| `EmbedEvent` | Sorts each attachment: images become base64 `data:` URIs (embedded, so the HTML is one self-contained file), `trace` stores the path, and `self-healing` is split into lines with the header line dropped. One line is one heal. |
| `TestCaseFinished` | Stores the Cucumber `Result`: status, duration and error. |
| `TestRunFinished` | Keeps the **last attempt** of each scenario (a retried scenario is one test case per attempt) and remembers how many attempts it took. Then it sorts scenarios by file and line, computes totals, writes the HTML, JSON and `failed-scenarios.txt` files and prints the console line. |

### How totals are counted

| Total | Statuses |
|---|---|
| passed | `PASSED` |
| skipped | `SKIPPED`, `PENDING`, `UNUSED` (a scenario with no result also counts as `UNUSED`) |
| failed | everything else: `FAILED`, `UNDEFINED` (missing step definition), `AMBIGUOUS` |
| heals | sum of heal lines over all scenarios |
| flaky ("passed after retry") | `PASSED` on the last attempt after more than one attempt |

Totals count each scenario once, by its last attempt. Each row in the JSON has `attempts`, and the HTML shows
an "N attempts" chip on retried scenarios.

### `run-summary.html`

It is one file with inline CSS and no external assets, and it adapts to light and dark mode, so you can open it straight from a CI artifact. Sections from top to bottom:

1. **Header:** verdict chip (passed/failed), start time, environment (`-Denv`) and wall-clock time.
2. **Tiles:** Scenarios, Passed, Failed, Skipped and Locators healed. The failed, skipped and healed tiles are only coloured when their count is above 0.
3. **Failures** (only if there are any): one card per scenario with the feature and `file:line`, a **trimmed stack trace**, the trace path with the command to open it, and the screenshot.
   - Trimmed stack trace: it keeps the message, frames from `com.agenticqa.*` and the Gherkin step frame (`✽.`), and collapses everything else into `... N library frames`. At most 30 lines. The full stack stays in `cucumber.html`.
   - An `UNDEFINED` scenario shows "A step has no matching step definition."
4. **Healed locators** (only if there are any): every heal line, with the scenario it happened in.
5. **All scenarios:** one table per feature with status chip, name, `healed N` chip, `file:line`, tags and duration.
6. **Footer:** a pointer to `cucumber.html` for the step-by-step view.

### `run-summary.json`

```json
{
  "environment" : "ci",
  "started" : "2026-11-10T09:12:03.512Z",
  "durationMs" : 18734,
  "total" : 12, "passed" : 11, "failed" : 1, "skipped" : 0, "heals" : 2,
  "scenarios" : [ {
    "feature" : "Self-healing locators",
    "scenario" : "Search keeps working after the search box id changes",
    "location" : "self_healing.feature:5",
    "tags" : [ "@ui", "@healing" ],
    "status" : "passed",
    "durationMs" : 4120,
    "heals" : [ "search input: '#search-input' -> '#customer-search' (score 0.80, runner-up 0.27) on http://…" ]
  } ]
}
```

`error` (only the first line of the message), `trace` and `heals` are included only when they have a value.

### Console line

At the end of every run:

```
[REPORT] 12 scenarios: 11 passed, 1 failed, 0 skipped, 2 locator(s) healed, 18.7 s -> D:\…\target\reports\run-summary.html
```

## Allure and Extent

Both are standard Cucumber plugins, so every scenario, step, tag and attachment from the hooks (screenshot,
`self-healing`, trace and video paths) appears in them with no extra code. Both handle parallel scenarios.

### Allure

| | |
|---|---|
| Dependency | `io.qameta.allure:allure-cucumber7-jvm` |
| Results | `target/allure-results`, set in [`allure.properties`](../src/test/resources/allure.properties) |
| HTML | `mvn allure:report` writes `target/reports/allure/index.html`. `mvn allure:serve` builds it and opens it in the browser. The `allure-maven` plugin downloads the Allure command line itself, so nothing needs installing. |
| Retries | Attempts of a retried scenario are grouped under one test (**Retries** tab). |

Open the generated report through a web server (`allure:serve` does this), not straight from disk. The
browser blocks the report's data files on `file://`.

### Extent

| | |
|---|---|
| Dependency | `tech.grasshopper:extentreports-cucumber7-adapter` |
| Settings | [`extent.properties`](../src/test/resources/extent.properties) (output path, base64 screenshots) and [`extent-config.xml`](../src/test/resources/extent-config.xml) (title, theme, time format) |
| HTML | `target/reports/extent/index.html`, written at the end of `mvn test`. It is a single file, because screenshots are embedded as base64. |
| Retries | Each attempt is its own entry. |

### `ReportSetupPlugin`

[`ReportSetupPlugin`](../src/test/java/com/agenticqa/reporting/ReportSetupPlugin.java) runs when the run
starts, before any result is written:

- It **empties `target/allure-results`**. Allure would otherwise merge every earlier run it finds there (when
  `mvn clean` was not run) into one report.
- It writes the run's **environment** to both reports: environment name, UI URL, browser, threads, retry
  settings, tag filter and Java version. In Allure this is the *Environment* widget, in Extent the *System info*
  on the dashboard.

## 3. Per CI job: `job_summary.py`

[`.github/workflows/tests.yml`](../.github/workflows/tests.yml) runs one job per suite (`api` and `ui`, filtered by Cucumber tag). After the tests, and **even if they failed** (`if: always()`), it runs:

```
python3 .github/scripts/job_summary.py target/reports/run-summary.json <suite>
```

[`job_summary.py`](../.github/scripts/job_summary.py) reads the JSON and writes Markdown to `$GITHUB_STEP_SUMMARY`. When run locally it writes to stdout instead.

- **Heading and totals table:** ✅/❌, the suite, the environment, and Scenarios / Passed / Failed / Skipped / Healed locators / Time.
- **Failures table:** scenario, `file:line` and the first line of the error. Cells are escaped and cut to 200 characters. It also says which artifact holds the screenshots and which holds the traces.
- **Healed locators:** heals are split in two:
  - **Unexpected** heals (scenario not tagged `@healing`) are listed under ⚠️ and each one also prints `::warning title=Locator healed::…`. GitHub shows that as an annotation on the run page and in the PR checks.
  - **Expected** heals from `@healing` scenarios, which break selectors on purpose to prove healing works, go into a collapsed `<details>` block without warnings.

The script **never fails the job**. If the JSON is missing, for example because the build broke before the tests ran, it writes a short note instead. The test step's own exit code decides whether the job is red.

## 4. Artifacts

| Artifact | When | Contents |
|---|---|---|
| `reports-<suite>` | always | `target/reports/` (run summary, Cucumber, Extent and Allure HTML, JSON files, failed-scenario list), `target/allure-results/`, `target/healing/heals.jsonl` and `healing/fingerprints.json` (the baseline as it was after the run) |
| `traces-<suite>` | job failed | `target/artifacts/traces/*.zip` |
| `videos-<suite>` | job failed | `target/artifacts/videos/*.webm` |
| `app-log-<suite>` | job failed | the sample app's server log |

Usual triage when a CI run fails:

1. Read the **job summary** to see which scenario failed and the first line of the error.
2. Download `reports-<suite>` and open `run-summary.html` for the trimmed stack and the screenshot.
3. If that is not enough, download `traces-<suite>` and open the trace to step through actions, DOM snapshots and network.
4. If the server misbehaved, check `app-log-<suite>`.

## Running locally

```bash
mvn test                                           # all scenarios, env dev
mvn test -Dcucumber.filter.tags="@ui"              # one suite
mvn test -Denv=ci -Dthreads=2                      # other environment / parallelism
mvn allure:report                                  # Allure HTML from the last run (allure:serve opens it)
python .github/scripts/job_summary.py target/reports/run-summary.json local   # preview the CI summary
```

Then open `target/reports/run-summary.html` or `target/reports/extent/index.html`. `target/` is git-ignored and cleaned by `mvn clean`, so old traces and reports disappear with it. `heals.jsonl` is only appended to, so it keeps adding lines across runs until you clean.

## Extending

- **New evidence for every scenario** (for example console logs or a HAR file): attach it in `TestInitializeHooks.closeBrowser` with a distinct name. It appears in `cucumber.html` automatically. To show it in the run summary, add a branch for that name in `RunSummaryPlugin.onEmbed`, add a field to `ScenarioResult`, and render it in `failureCard` or `json`.
- **New fields for CI:** add them in `RunSummaryPlugin.json` first, then read them in `job_summary.py`. The JSON file is the only interface between the two.
- **Another report** (Xray, ReportPortal, …): add its Cucumber plugin to `@CucumberOptions.plugin`, as was done for Allure and Extent. The existing reports keep working next to it.
