package com.agenticqa.core;

import com.agenticqa.api.ApiCall;
import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings;
import com.agenticqa.ui.healing.HealEvent;
import com.agenticqa.ui.healing.Healer;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one scenario needs while it runs: the browser page, the API access and a typed store for sharing data
 * between steps. Cucumber's PicoContainer creates one instance per scenario and injects it into every step class,
 * so parallel scenarios never share a browser or data.
 * <p>
 * The page and the API calls come from the same browser context, so they share cookies and login state.
 * The browser starts lazily, the first time a step asks for the page or the API.
 */
public class TestSession {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Settings settings = EnvConfig.get();
    private final Map<DataKey<?>, Object> store = new HashMap<>();
    private final List<ApiCall> apiCalls = new ArrayList<>();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private Healer healer;
    private String scenarioName = "scenario";

    /**
     * Settings of the active environment.
     *
     * @return the typed settings
     */
    public Settings settings() {
        return settings;
    }

    /**
     * Remembers the scenario name; it is used to name the trace file when the scenario fails.
     *
     * @param name the Cucumber scenario name
     */
    public void scenarioName(String name) {
        this.scenarioName = name;
    }

    /**
     * The scenario's browser page, created on first use.
     *
     * @return this scenario's page
     */
    public synchronized Page page() {
        if (page == null) page = context().newPage();
        return page;
    }

    /**
     * Tells whether a page was opened, so hooks never create one just to take a screenshot.
     *
     * @return true if page() was called before
     */
    public synchronized boolean hasPage() {
        return page != null;
    }

    /**
     * The self-healing engine of this scenario, created on first use.
     *
     * @return this scenario's healer
     */
    public synchronized Healer healer() {
        if (healer == null) healer = new Healer(settings.healing());
        return healer;
    }

    /**
     * Locators healed in this scenario, without creating a healer just to ask.
     *
     * @return the heals, empty if none
     */
    public synchronized List<HealEvent> healEvents() {
        return healer == null ? List.of() : healer.events();
    }

    /**
     * API access that shares cookies with the browser. Requests need the full URL (api.baseUrl plus the path),
     * because the underlying context is configured with the UI base URL.
     *
     * @return the API request context of this scenario
     */
    public synchronized APIRequestContext api() {
        return context().request();
    }

    /**
     * Adds an HTTP call to this scenario's call log. Called by every API client.
     *
     * @param call the finished call
     */
    public synchronized void recordApiCall(ApiCall call) {
        apiCalls.add(call);
    }

    /**
     * HTTP calls of this scenario so far, oldest first.
     *
     * @return a copy of the call log
     */
    public synchronized List<ApiCall> apiCalls() {
        return List.copyOf(apiCalls);
    }

    /**
     * Stores a value for later steps of the same scenario.
     *
     * @param key   the typed key
     * @param value the value to keep
     * @param <T>   the type of the value
     */
    public <T> void put(DataKey<T> key, T value) {
        store.put(key, value);
    }

    /**
     * Reads a stored value.
     *
     * @param key the typed key
     * @param <T> the type of the value
     * @return the stored value
     * @throws IllegalStateException if nothing was stored under the key in this scenario
     */
    public <T> T get(DataKey<T> key) {
        Object value = store.get(key);
        if (value == null) {
            throw new IllegalStateException("Nothing stored under key '" + key.name()
                    + "' in this scenario. Did an earlier step run?");
        }
        return key.type().cast(value);
    }

    /**
     * Tells whether a value is stored.
     *
     * @param key the typed key
     * @param <T> the type of the value
     * @return true if a value was stored
     */
    public <T> boolean has(DataKey<T> key) {
        return store.containsKey(key);
    }

    /**
     * The browser context, started on first use.
     *
     * @return the scenario's browser context
     */
    private synchronized BrowserContext context() {
        if (context == null) startBrowser();
        return context;
    }

    /** Launches the configured browser and creates the context with tracing switched on. */
    private void startBrowser() {
        Settings.Ui ui = settings.ui();
        String name = ui.browser() == null ? "chromium" : ui.browser().toLowerCase();
        playwright = Playwright.create();
        BrowserType type = switch (name) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
        BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions()
                .setHeadless(ui.headless())
                .setSlowMo(ui.slowMoMs());
        if (name.equals("chromium")) {
            launch.setArgs(List.of("--disable-dev-shm-usage")); // needed inside containers
        }
        browser = type.launch(launch);
        context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(ui.baseUrl())
                .setViewportSize(1366, 768));
        context.setDefaultTimeout(ui.timeoutMs());
        PlaywrightAssertions.setDefaultAssertionTimeout(ui.timeoutMs()); // assertThat would otherwise use 5 s
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true).setSnapshots(true).setSources(false));
    }

    /**
     * Stops tracing (the zip is kept only when the scenario failed) and closes everything.
     * Each close is attempted separately so cleanup never hides the real test result.
     *
     * @param failed true if the scenario failed
     * @return the saved trace file, or null if none was saved (passed, or no browser was started)
     */
    public synchronized Path close(boolean failed) {
        Path saved = null;
        if (context != null) {
            try {
                if (failed) {
                    Path trace = traceFile();
                    context.tracing().stop(new Tracing.StopOptions().setPath(trace));
                    System.out.println("Trace saved: " + trace);
                    saved = trace;
                } else {
                    context.tracing().stop();
                }
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        quietly(() -> { if (context != null) context.close(); });
        quietly(() -> { if (browser != null) browser.close(); });
        quietly(() -> { if (playwright != null) playwright.close(); });
        return saved;
    }

    /**
     * Path of the trace file of this scenario, for example
     * target/artifacts/traces/2026-11-09_14-05-09_create-and-find-a-customer.zip
     *
     * @return the trace path
     */
    private Path traceFile() {
        String slug = scenarioName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return Paths.get("target", "artifacts", "traces",
                LocalDateTime.now().format(FILE_STAMP) + "_" + slug + ".zip");
    }

    /**
     * Runs cleanup code and ignores failures.
     *
     * @param action the cleanup action
     */
    private static void quietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
