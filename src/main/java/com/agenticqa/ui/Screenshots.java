package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Takes screenshots on request, for page objects and steps. Page objects call it through {@link PageComponents}:
 * <pre>
 * pricePage.screenshot("after filter");                    // the visible part of the Price window
 * pricePage.fullPageScreenshot("price grid");              // the whole page, scrolled parts included
 * pricePage.screenshot(el.get("filterButton"), "filter");  // one element only
 * windows.current().screenshot("now");                     // in a step: the window the scenario is on
 * </pre>
 * Each PNG is saved under target/artifacts/screenshots/&lt;scenario&gt;/&lt;time&gt;_&lt;name&gt;.png and handed to
 * the scenario's {@link TestSession}; the {@code @AfterStep} hook attaches it to the reports (Cucumber, Extent,
 * Allure) under the step that took it. Outside a scenario the file is still saved, nothing is attached.
 */
public final class Screenshots {

    private static final Path FOLDER = Paths.get("target", "artifacts", "screenshots");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm-ss-SSS");

    private Screenshots() {
    }

    /**
     * Takes a screenshot of the visible part of a window.
     *
     * @param window the window (Playwright page)
     * @param name   the name shown in the reports and used in the file name
     * @return the saved PNG file
     */
    public static Path take(Page window, String name) {
        TestSession session = sessionOrNull();
        Path file = file(session, name);
        keep(session, name, window.screenshot(new Page.ScreenshotOptions().setPath(file)), file);
        return file;
    }

    /**
     * Takes a screenshot of the whole page of a window, the parts scrolled out of view included.
     *
     * @param window the window (Playwright page)
     * @param name   the name shown in the reports and used in the file name
     * @return the saved PNG file
     */
    public static Path takeFullPage(Page window, String name) {
        TestSession session = sessionOrNull();
        Path file = file(session, name);
        keep(session, name, window.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true)), file);
        return file;
    }

    /**
     * Takes a screenshot of one element only, for example a grid row or a dialog; waits for it to be visible.
     *
     * @param element the element
     * @param name    the name shown in the reports and used in the file name
     * @return the saved PNG file
     */
    public static Path take(Locator element, String name) {
        TestSession session = sessionOrNull();
        Path file = file(session, name);
        keep(session, name, element.screenshot(new Locator.ScreenshotOptions().setPath(file)), file);
        return file;
    }

    /** Hands the screenshot to the scenario, so the next {@code @AfterStep} attaches it to the reports. */
    private static void keep(TestSession session, String name, byte[] png, Path file) {
        if (session != null) session.addScreenshot(new TestSession.Screenshot(name, png, file));
    }

    /** The scenario running on this thread, or null outside a scenario (then the file is only saved). */
    private static TestSession sessionOrNull() {
        try {
            return TestSession.current();
        } catch (IllegalStateException noScenario) {
            return null;
        }
    }

    /**
     * The file for a screenshot, with anything but letters, digits, '-' and '_' replaced by '-'. Playwright creates
     * the folders.
     */
    private static Path file(TestSession session, String name) {
        String folder = session == null ? "no-scenario" : safe(session.scenarioName());
        return FOLDER.resolve(folder).resolve(LocalDateTime.now().format(TIME) + "_" + safe(name) + ".png");
    }

    private static String safe(String text) {
        return text.trim().replaceAll("[^A-Za-z0-9_-]+", "-");
    }
}
