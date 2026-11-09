package com.agenticqa.reporting;

import com.agenticqa.config.EnvConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EmbedEvent;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestSourceRead;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cucumber plugin that writes one self-contained run summary: totals, every scenario grouped by feature, and
 * for failures the error, the screenshot and the trace path; plus every healed locator of the run.
 * <p>
 * Register it in {@code @CucumberOptions(plugin = "com.agenticqa.reporting.RunSummaryPlugin:target/reports/run-summary.html")}.
 * Next to the HTML file it writes the same data as JSON (for CI) and prints a one-line summary to the console.
 * Events arrive from parallel threads, so all state is kept in concurrent maps keyed by test case id.
 */
public class RunSummaryPlugin implements ConcurrentEventListener {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String HEAL_HEADER = "Locators healed";

    private final Path htmlFile;
    private final Map<URI, String> featureNames = new ConcurrentHashMap<>();
    private final Map<UUID, ScenarioResult> scenarios = new ConcurrentHashMap<>();
    private volatile Instant runStarted = Instant.now();

    /**
     * Called by Cucumber with the text after the colon in the plugin option.
     *
     * @param htmlFile path of the HTML summary to write
     */
    public RunSummaryPlugin(String htmlFile) {
        this.htmlFile = Paths.get(htmlFile);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, e -> runStarted = e.getInstant());
        publisher.registerHandlerFor(TestSourceRead.class, this::onSource);
        publisher.registerHandlerFor(TestCaseStarted.class, this::onStarted);
        publisher.registerHandlerFor(EmbedEvent.class, this::onEmbed);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onFinished);
        publisher.registerHandlerFor(TestRunFinished.class, this::onRunFinished);
    }

    /** Remembers the feature name of each feature file. */
    private void onSource(TestSourceRead event) {
        String name = Arrays.stream(event.getSource().split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("Feature:"))
                .map(line -> line.substring("Feature:".length()).trim())
                .findFirst()
                .orElse(fileName(event.getUri()));
        featureNames.put(event.getUri(), name);
    }

    private void onStarted(TestCaseStarted event) {
        scenarios.put(event.getTestCase().getId(), new ScenarioResult(event.getTestCase(), event.getInstant()));
    }

    /** Collects what the hooks attached: failure screenshot, trace path and healed locators. */
    private void onEmbed(EmbedEvent event) {
        ScenarioResult scenario = scenarios.get(event.getTestCase().getId());
        if (scenario == null) return;
        String name = event.getName() == null ? "" : event.getName();
        if (event.getMediaType().startsWith("image/")) {
            scenario.screenshots.add("data:" + event.getMediaType() + ";base64,"
                    + Base64.getEncoder().encodeToString(event.getData()));
        } else if (name.equals("trace")) {
            scenario.trace = new String(event.getData(), StandardCharsets.UTF_8);
        } else if (name.equals("self-healing")) {
            Arrays.stream(new String(event.getData(), StandardCharsets.UTF_8).split("\\R"))
                    .filter(line -> !line.isBlank() && !line.startsWith(HEAL_HEADER))
                    .forEach(scenario.heals::add);
        }
    }

    private void onFinished(TestCaseFinished event) {
        ScenarioResult scenario = scenarios.get(event.getTestCase().getId());
        if (scenario != null) scenario.result = event.getResult();
    }

    /** Writes the HTML and JSON files and prints the console line. */
    private void onRunFinished(TestRunFinished event) {
        List<ScenarioResult> all = new ArrayList<>(scenarios.values());
        all.sort(Comparator.comparing((ScenarioResult s) -> s.testCase.getUri().toString())
                .thenComparing(s -> s.testCase.getLocation().getLine()));
        Duration wallClock = Duration.between(runStarted, event.getInstant());
        Totals totals = Totals.of(all);

        try {
            if (htmlFile.getParent() != null) Files.createDirectories(htmlFile.getParent());
            Files.writeString(htmlFile, html(all, totals, wallClock), StandardCharsets.UTF_8);
            Files.writeString(jsonFile(), json(all, totals, wallClock), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write run summary " + htmlFile, e);
        }
        System.out.printf("%n[REPORT] %d scenarios: %d passed, %d failed, %d skipped, %d locator(s) healed, %s"
                        + " -> %s%n",
                totals.total, totals.passed, totals.failed, totals.skipped, totals.heals,
                format(wallClock), htmlFile.toAbsolutePath());
    }

    // ---------------------------------------------------------------------------------------------- JSON

    private Path jsonFile() {
        String name = htmlFile.getFileName().toString().replaceFirst("\\.html?$", "") + ".json";
        return htmlFile.resolveSibling(name);
    }

    private String json(List<ScenarioResult> all, Totals totals, Duration wallClock) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("environment", EnvConfig.envName());
        root.put("started", runStarted.toString());
        root.put("durationMs", wallClock.toMillis());
        root.put("total", totals.total);
        root.put("passed", totals.passed);
        root.put("failed", totals.failed);
        root.put("skipped", totals.skipped);
        root.put("heals", totals.heals);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScenarioResult s : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature", featureOf(s));
            row.put("scenario", s.testCase.getName());
            row.put("location", fileName(s.testCase.getUri()) + ":" + s.testCase.getLocation().getLine());
            row.put("tags", s.testCase.getTags());
            row.put("status", s.status().name().toLowerCase());
            row.put("durationMs", s.duration().toMillis());
            if (s.error() != null) row.put("error", firstLine(s.error()));
            if (s.trace != null) row.put("trace", s.trace);
            if (!s.heals.isEmpty()) row.put("heals", s.heals);
            rows.add(row);
        }
        root.put("scenarios", rows);
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root);
    }

    // ---------------------------------------------------------------------------------------------- HTML

    private String html(List<ScenarioResult> all, Totals totals, Duration wallClock) {
        StringBuilder out = new StringBuilder(64 * 1024);
        String verdict = totals.failed > 0 ? "failed" : "passed";
        out.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Run summary - ").append(esc(verdict)).append("</title><style>")
                .append(CSS).append("</style></head><body><main>");

        out.append("<header><h1>Test run <span class=\"chip ").append(verdict).append("\">")
                .append(verdict).append("</span></h1><p class=\"meta\">")
                .append(esc(STAMP.format(runStarted))).append(" &middot; environment <b>")
                .append(esc(EnvConfig.envName())).append("</b> &middot; ").append(format(wallClock))
                .append(" wall clock</p></header>");

        out.append("<section class=\"tiles\">")
                .append(tile("Scenarios", totals.total, ""))
                .append(tile("Passed", totals.passed, "passed"))
                .append(tile("Failed", totals.failed, totals.failed > 0 ? "failed" : ""))
                .append(tile("Skipped", totals.skipped, totals.skipped > 0 ? "skipped" : ""))
                .append(tile("Locators healed", totals.heals, totals.heals > 0 ? "healed" : ""))
                .append("</section>");

        List<ScenarioResult> failures = all.stream().filter(s -> s.status() != Status.PASSED
                && s.status() != Status.SKIPPED).toList();
        if (!failures.isEmpty()) {
            out.append("<section><h2>Failures</h2>");
            for (ScenarioResult s : failures) out.append(failureCard(s));
            out.append("</section>");
        }

        List<ScenarioResult> healed = all.stream().filter(s -> !s.heals.isEmpty()).toList();
        if (!healed.isEmpty()) {
            out.append("<section><h2>Healed locators</h2><p class=\"hint\">The tests kept running, but these "
                    + "selectors no longer match the app. Update the page objects.</p><ul class=\"heals\">");
            for (ScenarioResult s : healed) {
                for (String heal : s.heals) {
                    out.append("<li><code>").append(esc(heal)).append("</code><span class=\"muted\"> in ")
                            .append(esc(s.testCase.getName())).append("</span></li>");
                }
            }
            out.append("</ul></section>");
        }

        out.append("<section><h2>All scenarios</h2>");
        Map<String, List<ScenarioResult>> byFeature = all.stream()
                .collect(Collectors.groupingBy(this::featureOf, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ScenarioResult>> feature : byFeature.entrySet()) {
            out.append("<h3>").append(esc(feature.getKey())).append("</h3>")
                    .append("<div class=\"scroll\"><table><thead><tr><th>Status</th><th>Scenario</th>")
                    .append("<th>Tags</th><th class=\"num\">Time</th></tr></thead><tbody>");
            for (ScenarioResult s : feature.getValue()) {
                String status = s.status().name().toLowerCase();
                out.append("<tr><td><span class=\"chip ").append(cssStatus(s.status())).append("\">")
                        .append(status).append("</span></td><td>").append(esc(s.testCase.getName()));
                if (!s.heals.isEmpty()) {
                    out.append(" <span class=\"chip healed\">healed ").append(s.heals.size()).append("</span>");
                }
                out.append("<div class=\"muted\">").append(esc(fileName(s.testCase.getUri()))).append(':')
                        .append(s.testCase.getLocation().getLine()).append("</div></td><td>")
                        .append(esc(String.join(" ", s.testCase.getTags()))).append("</td><td class=\"num\">")
                        .append(format(s.duration())).append("</td></tr>");
            }
            out.append("</tbody></table></div>");
        }
        out.append("</section>");

        out.append("<footer class=\"muted\">Full step-by-step report: cucumber.html in the same folder.</footer>")
                .append("</main></body></html>");
        return out.toString();
    }

    private String failureCard(ScenarioResult s) {
        StringBuilder out = new StringBuilder();
        out.append("<article class=\"failure\"><h3>").append(esc(s.testCase.getName()))
                .append(" <span class=\"chip failed\">").append(s.status().name().toLowerCase())
                .append("</span></h3><p class=\"muted\">").append(esc(featureOf(s))).append(" &middot; ")
                .append(esc(fileName(s.testCase.getUri()))).append(':').append(s.testCase.getLocation().getLine())
                .append("</p>");
        if (s.error() != null) {
            out.append("<pre class=\"error\">").append(esc(stack(s.error()))).append("</pre>");
        } else if (s.status() == Status.UNDEFINED) {
            out.append("<pre class=\"error\">A step has no matching step definition.</pre>");
        }
        if (s.trace != null) {
            out.append("<p><b>Trace:</b> <code>").append(esc(s.trace)).append("</code><br>")
                    .append("<span class=\"muted\">Open it with</span> <code>npx playwright show-trace \"")
                    .append(esc(s.trace)).append("\"</code> <span class=\"muted\">or drop the zip on ")
                    .append("trace.playwright.dev</span></p>");
        }
        for (String img : s.screenshots) {
            out.append("<details open><summary>Screenshot at failure</summary><img alt=\"Screenshot of the page "
                    + "when the scenario failed\" src=\"").append(img).append("\"></details>");
        }
        return out.append("</article>").toString();
    }

    private static String tile(String label, long value, String tone) {
        return "<div class=\"tile " + tone + "\"><div class=\"value\">" + value + "</div><div class=\"label\">"
                + esc(label) + "</div></div>";
    }

    private static String cssStatus(Status status) {
        return switch (status) {
            case PASSED -> "passed";
            case SKIPPED, PENDING, UNUSED -> "skipped";
            default -> "failed";
        };
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private String featureOf(ScenarioResult s) {
        return featureNames.getOrDefault(s.testCase.getUri(), fileName(s.testCase.getUri()));
    }

    private static String fileName(URI uri) {
        String path = uri.toString();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String firstLine(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getName() : error.getMessage();
        return message.lines().findFirst().orElse(message);
    }

    /**
     * The error message plus only the frames that point at this project: our own classes and the Gherkin step
     * (Cucumber prints it as a frame starting with "✽."). Assertion-library and JDK frames are dropped; the full
     * stack stays in cucumber.html.
     */
    private static String stack(Throwable error) {
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        List<String> lines = text.toString().lines().toList();
        List<String> kept = new ArrayList<>();
        int dropped = 0;
        for (String line : lines) {
            String t = line.trim();
            boolean frame = t.startsWith("at ");
            if (!frame || t.startsWith("at com.agenticqa.") || t.startsWith("at ✽.")) {
                if (dropped > 0) kept.add("\t... " + dropped + " library frames");
                dropped = 0;
                kept.add(line);
            } else {
                dropped++;
            }
            if (kept.size() >= 30) break;
        }
        return String.join("\n", kept);
    }

    private static String format(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format("%.1f s", ms / 1000.0);
        return String.format("%d min %d s", ms / 60_000, (ms % 60_000) / 1000);
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** What the plugin learned about one scenario. Fields are written by the scenario's own thread. */
    private static final class ScenarioResult {
        final TestCase testCase;
        final Instant started;
        final List<String> screenshots = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> heals = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile String trace;
        volatile Result result;

        ScenarioResult(TestCase testCase, Instant started) {
            this.testCase = testCase;
            this.started = started;
        }

        Status status() {
            return result == null ? Status.UNUSED : result.getStatus();
        }

        Duration duration() {
            return result == null ? Duration.ZERO : result.getDuration();
        }

        Throwable error() {
            return result == null ? null : result.getError();
        }
    }

    /** Counts of one run. */
    private record Totals(long total, long passed, long failed, long skipped, long heals) {
        static Totals of(List<ScenarioResult> all) {
            long passed = all.stream().filter(s -> s.status() == Status.PASSED).count();
            long skipped = all.stream().filter(s -> s.status() == Status.SKIPPED
                    || s.status() == Status.PENDING || s.status() == Status.UNUSED).count();
            long heals = all.stream().mapToLong(s -> s.heals.size()).sum();
            return new Totals(all.size(), passed, all.size() - passed - skipped, skipped, heals);
        }
    }

    private static final String CSS = """
            :root{--bg:#f7f7f5;--card:#fff;--ink:#1d1d1b;--muted:#6b6b66;--line:#e3e2dd;
              --pass:#1f7a4d;--pass-bg:#e3f3ea;--fail:#b3261e;--fail-bg:#fbe7e5;--skip:#7a5c00;--skip-bg:#fbf1d6;
              --heal:#4b3fb0;--heal-bg:#ebe8fb;--code:#f0efeb;color-scheme:light}
            @media (prefers-color-scheme:dark){:root{--bg:#161615;--card:#1f1f1d;--ink:#ecebe6;--muted:#9c9b94;
              --line:#33322f;--pass:#6fd19c;--pass-bg:#16301f;--fail:#ff8a80;--fail-bg:#3a1a17;--skip:#f0c75e;
              --skip-bg:#33290d;--heal:#b3a9ff;--heal-bg:#25213f;--code:#2a2927;color-scheme:dark}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}
            main{max-width:1100px;margin:0 auto;padding:24px 16px 48px}
            h1{font-size:24px;margin:0 0 4px;display:flex;gap:10px;align-items:center;flex-wrap:wrap}
            h2{font-size:17px;margin:32px 0 12px}h3{font-size:15px;margin:20px 0 8px}
            .meta,.muted{color:var(--muted)}.muted{font-size:12px}.hint{color:var(--muted);margin-top:-6px}
            .tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-top:20px}
            .tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px}
            .tile .value{font-size:28px;font-weight:650;font-variant-numeric:tabular-nums}
            .tile .label{color:var(--muted);font-size:13px}
            .tile.passed .value{color:var(--pass)}.tile.failed .value{color:var(--fail)}
            .tile.skipped .value{color:var(--skip)}.tile.healed .value{color:var(--heal)}
            .chip{display:inline-block;font-size:12px;font-weight:600;padding:1px 8px;border-radius:999px;vertical-align:middle}
            .chip.passed{background:var(--pass-bg);color:var(--pass)}.chip.failed{background:var(--fail-bg);color:var(--fail)}
            .chip.skipped{background:var(--skip-bg);color:var(--skip)}.chip.healed{background:var(--heal-bg);color:var(--heal)}
            .scroll{overflow-x:auto;background:var(--card);border:1px solid var(--line);border-radius:10px}
            table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:9px 12px;border-bottom:1px solid var(--line);vertical-align:top}
            tr:last-child td{border-bottom:0}th{font-size:12px;color:var(--muted);font-weight:600}
            .num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
            .failure{background:var(--card);border:1px solid var(--line);border-left:4px solid var(--fail);border-radius:10px;padding:4px 16px 12px;margin-bottom:14px}
            pre.error{background:var(--code);padding:12px;border-radius:8px;overflow-x:auto;font-size:12px;white-space:pre}
            code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:12px;word-break:break-all}
            .heals{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px 12px 32px;margin:0}
            .heals li{margin:4px 0}
            details{margin-top:10px}summary{cursor:pointer;color:var(--muted)}
            details img{display:block;margin-top:8px;max-width:100%;border:1px solid var(--line);border-radius:8px}
            footer{margin-top:32px}
            """;
}
