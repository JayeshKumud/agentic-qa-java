# Hooks: what runs around every scenario and step

No feature file or step repeats setup or teardown. The hooks below do it, in this order. Cucumber runs `@Before`
hooks from the lowest order to the highest and `@After` hooks from the highest to the lowest (the default order is
10000).

| When | Hook | Order | What it does |
|---|---|---|---|
| before the suite | `TestRunner.prepareRun` (`@BeforeSuite`) | | empties the report folders (`cleanup.folders`), starts `target/logs/run.log`, applies the REST Assured setup (`api` section, see [api.md](api.md#global-rest-assured-setup)) |
| before each scenario | `Hooks.before` | 0 | starts the scenario's clock, binds the `TestSession` to the thread (`TestSession.current()`), logs the tags |
| | `Hooks.checkTags` | 1 | skips `@mute` scenarios, checks the `@PRKLM-` test-case tag (below) |
| after each step | `Hooks.afterStep` (`@AfterStep`) | | a screenshot (`step-screenshot`) if `ui.screenshots` is `everyStep` and the scenario opened a page |
| after each scenario | `ServiceCleanup.cleanUp` | 20000 | deletes the test data the scenario registered, newest first, best effort |
| | `Hooks.after` | 10000 | healed locators, failure screenshot and `debug-info`, closes the browser context (keeps trace and video of failed scenarios and attaches them to Allure), records the result |
| after the suite | `TestRunner.finishRun` (`@AfterSuite`) | | publishes the reports in the pipeline, writes and prints `test-execution-summary.json`, closes the run log |

The attachments and the summary are described in [reporting.md](reporting.md).

## `@mute`: skip a scenario

A scenario with a tag that contains `@mute` (any case, also inherited from the feature) is not run. The hook logs
`This test is muted. Hence, skipping this test: <name>` and throws TestNG's `SkipException`, so Cucumber, TestNG,
Allure and the summary report it as **skipped**. A skipped scenario is never retried.

```gherkin
  @PRKLM-1004 @mute
  Scenario: Update a customer's status
```

## The test-case tag: `@PRKLM-<digits>`

Every scenario needs a test-case tag, the id of its test case in the test management tool. The rule, from the
`testCaseTag` section of `common.yaml`:

```yaml
testCaseTag:
  prefix: "@PRKLM-"
  required: true
```

| Tags | Result |
|---|---|
| `@api @PRKLM-1001` | runs; reported as test case `@PRKLM-1001` |
| none at all | fails: `No tags found. Expected a tag starting with @PRKLM- ...` |
| `@api @smoke` | fails: `Missing required @PRKLM- tag. Tags found: [...]` |
| `@PRKLM-10x2` | fails: `Invalid tag format. The first tag that starts with @PRKLM- must be exactly @PRKLM-<digits>, but was @PRKLM-10x2` |

- The first tag that starts with the prefix counts (case is ignored). Feature tags count too.
- A tag failure is never retried (`ScenarioRetry` recognises `TestCaseTag.TagError`), because the same tags would
  fail again.
- `-DtestCaseTag.required=false` turns the failures into warnings, for example while tagging a new feature.
- The rules are pure logic in [`TestCaseTag`](../src/test/java/com/agenticqa/hooks/TestCaseTag.java), unit tested
  in `TestCaseTagTest`.

The sample scenarios carry `@PRKLM-1001` to `@PRKLM-1014`. Give a new scenario the id of its test case.

## Deleting test data after a scenario

Data a scenario creates is deleted by [`ServiceCleanup`](../src/test/java/com/agenticqa/steps/ServiceCleanup.java),
which runs before `Hooks.after`. Register the delete in the step that creates the data, so only scenarios that
created something try to delete it. For example, a step that creates a zone:

```java
long zoneId = ApiFactory.getApi(UnitManagerApi.class).createZone(...);
session.put(Keys.ZONE_ID, zoneId);
cleanup.add("zone " + zoneId, () -> {
    ApiFactory.getApi(UnitManagerApi.class).deleteZone(zoneId, true);
    try {
        ApiFactory.getApi(EpcApi.class).deleteZoneSummaries(Set.of(zoneId));
    } catch (AssertionError e) {
        logger.info("Epc delete zone summary endpoint was not available: {}", e.getMessage());
    }
});
```

A failing delete is logged and ignored, so it never hides the scenario's real result. There is no scenario
context to clear afterwards: the `TestSession` and everything stored in it belong to the scenario and are dropped
with it.
