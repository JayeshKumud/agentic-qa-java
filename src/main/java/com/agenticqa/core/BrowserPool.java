package com.agenticqa.core;

import com.agenticqa.config.Settings;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One browser per worker thread, reused by every scenario that thread runs.
 * <p>
 * Playwright objects must only be used by the thread that created them, and launching a browser is the slowest
 * part of a UI scenario. So each TestNG worker thread gets its own {@link Playwright} and {@link Browser} the
 * first time it needs one, and every scenario on that thread opens a new, isolated {@code BrowserContext} in it
 * (own cookies, storage and trace). With {@code -Dthreads=4} at most four browsers run, however many scenarios
 * there are. A browser that crashed is replaced the next time it is asked for. All browsers are closed when the
 * JVM exits.
 */
public final class BrowserPool {

    private static final ThreadLocal<Worker> WORKER = new ThreadLocal<>();
    private static final List<Worker> ALL = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(BrowserPool::closeAll, "browser-pool-shutdown"));
    }

    /** Utility class, not instantiable. */
    private BrowserPool() {}

    /** The Playwright instance and browser of one thread. */
    private record Worker(Playwright playwright, Browser browser) {}

    /**
     * The browser of the calling thread, launched on first use.
     *
     * @param ui the browser settings (name, headless, slow motion)
     * @return a connected browser owned by this thread
     */
    public static Browser browser(Settings.Ui ui) {
        Worker worker = WORKER.get();
        if (worker != null && worker.browser().isConnected()) return worker.browser();
        if (worker != null) close(worker); // crashed or closed: start a new one
        worker = launch(ui);
        WORKER.set(worker);
        ALL.add(worker);
        return worker.browser();
    }

    private static Worker launch(Settings.Ui ui) {
        String name = ui.browser() == null ? "chromium" : ui.browser().toLowerCase();
        Playwright playwright = Playwright.create();
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
        System.out.printf("[BROWSER] %s launched for thread %s%n", name, Thread.currentThread().getName());
        return new Worker(playwright, type.launch(launch));
    }

    private static void close(Worker worker) {
        ALL.remove(worker);
        try {
            worker.browser().close();
        } catch (RuntimeException ignored) {
            // best effort
        }
        try {
            worker.playwright().close();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    /** Closes every browser; runs when the JVM exits, after all scenarios finished. */
    private static void closeAll() {
        for (Worker worker : ALL) close(worker);
    }
}
