package com.agenticqa.reporting;

import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings;
import com.aventstack.extentreports.service.ExtentService;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunStarted;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prepares the Allure and Extent reports when the run starts, before any scenario writes a result: it writes the
 * run's environment (env name, URLs, browser, threads, execution mode, retries) to Allure's
 * {@code environment.properties} and to the Extent report's system info.
 * <p>
 * The results of earlier runs are already gone by then: {@code TestRunner} empties the report folders, including
 * {@value #ALLURE_RESULTS}, before Cucumber starts (see {@code cleanup} in config/common.yaml). Without that,
 * Allure would merge every run it finds there.
 * Listed before the Allure and Extent plugins in {@code @CucumberOptions}, so it runs first.
 */
public class ReportSetupPlugin implements ConcurrentEventListener {

    /** Folder the Allure plugin writes to; must match allure.results.directory in allure.properties. */
    static final String ALLURE_RESULTS = "target/allure-results";

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, event -> prepare());
    }

    private void prepare() {
        Map<String, String> info = environment();
        Path results = Paths.get(ALLURE_RESULTS);
        try {
            Files.createDirectories(results);
            StringBuilder properties = new StringBuilder();
            info.forEach((key, value) -> properties.append(key.replace(' ', '.')).append('=')
                    .append(value.replace("\\", "\\\\")).append(System.lineSeparator()));
            Files.writeString(results.resolve("environment.properties"), properties, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare " + results, e);
        }
        info.forEach((key, value) -> ExtentService.getInstance().setSystemInfo(key, value));
    }

    /**
     * What the run was executed against, shown in both reports.
     *
     * @return label to value, in display order
     */
    private static Map<String, String> environment() {
        Settings settings = EnvConfig.get();
        Settings.Retry retry = settings.retry();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Environment", EnvConfig.envName());
        info.put("UI URL", String.valueOf(settings.ui().baseUrl()));
        info.put("Browser", settings.ui().browser() + (settings.ui().headless() ? " (headless)" : ""));
        info.put("Threads", System.getProperty("threads", System.getProperty("dataproviderthreadcount", "?")));
        info.put("Default mode", settings.execution().defaultMode() + " (@Parallel / @Sequential tags win)");
        info.put("Retries", retry.count() == 0 ? "off" : retry.count() + " for " + (retry.tags().isBlank()
                ? "all scenarios" : retry.tags()));
        info.put("Tags", System.getProperty("cucumber.filter.tags", "all"));
        info.put("Java", System.getProperty("java.version"));
        return info;
    }
}
