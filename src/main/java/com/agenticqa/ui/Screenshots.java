package com.agenticqa.ui;

import com.agenticqa.core.TestSession;
import com.microsoft.playwright.Page;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Takes the screenshot behind {@link PageComponents#screenshot()}: the whole page of a window, the parts scrolled
 * out of view included. The PNG is saved as target/artifacts/screenshots/&lt;scenario&gt;/&lt;time&gt;_&lt;page&gt;.png
 * and handed to the scenario's {@link TestSession}; the {@code @AfterStep} hook attaches it to the reports
 * (Cucumber, Extent, Allure) under the step that took it. Outside a scenario the file is still saved, nothing is
 * attached.
 */
final class Screenshots {

    private static final Path FOLDER = Paths.get("target", "artifacts", "screenshots");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH-mm-ss-SSS");

    private Screenshots() {
    }

    /**
     * Takes a full-page screenshot of a window.
     *
     * @param window the window (Playwright page)
     * @param label  what the screenshot shows, the page object's name; used in the reports and the file name
     * @return the saved PNG file
     */
    static Path take(Page window, String label) {
        TestSession session = sessionOrNull();
        String folder = session == null ? "no-scenario" : safe(session.scenarioName());
        Path file = FOLDER.resolve(folder).resolve(LocalDateTime.now().format(TIME) + "_" + safe(label) + ".png");
        byte[] png = window.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true));
        if (session != null) session.addScreenshot(new TestSession.Screenshot(label, png, file));
        return file;
    }

    /** The scenario running on this thread, or null outside a scenario (then the file is only saved). */
    private static TestSession sessionOrNull() {
        try {
            return TestSession.current();
        } catch (IllegalStateException noScenario) {
            return null;
        }
    }

    /** The text with anything but letters, digits, '-' and '_' replaced by '-', for file and folder names. */
    private static String safe(String text) {
        return text.trim().replaceAll("[^A-Za-z0-9_-]+", "-");
    }
}
