package com.agenticqa.ui;

import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Maximizes and resizes the browser window of any page, for example a window the app opened:
 * <pre>
 * BrowserWindow.maximize(page);   // like the window's maximize button
 * BrowserWindow.resize(page);     // the page fills its window
 * </pre>
 * Playwright has no call to maximize a window, so this uses the Chrome DevTools Protocol: chromium only; other
 * browsers log a note and continue.
 */
public final class BrowserWindow {

    /**
     * Width and height of each page's window frame (borders, title bar), measured before the window is
     * maximized, because afterwards window size minus page size is no longer the frame. Weak keys: an entry goes
     * away with its page; synchronized: scenarios run in parallel.
     */
    private static final Map<Page, int[]> FRAMES = Collections.synchronizedMap(new WeakHashMap<>());

    private BrowserWindow() {}

    /**
     * Maximizes the page's window on the screen, like its maximize button.
     *
     * @param page the page whose window to maximize
     */
    public static void maximize(Page page) {
        frame(page);
        setWindowState(page, "maximized");
    }

    /**
     * Makes the page fill its window. Needed when the page has a fixed size ({@code ui.maximize=false}, or the
     * size the app asked for when it opened the window): the window then grows on maximize, but the page inside
     * keeps its old size. Without a fixed size the page already follows its window, so nothing is done.
     * <p>
     * Playwright un-maximizes the window when it changes the page size, so the window is maximized again
     * afterwards if it was maximized before.
     *
     * @param page the page to resize
     */
    public static void resize(Page page) {
        if (page.viewportSize() == null) return;
        int[] frame = frame(page);
        int width = intOf(page, "() => outerWidth") - frame[0];
        int height = intOf(page, "() => outerHeight") - frame[1];
        boolean maximized = "maximized".equals(windowState(page));
        page.setViewportSize(width, height);
        if (maximized) setWindowState(page, "maximized");
    }

    /** The page's window frame, measured on first use, while the window still has the size Playwright gave it. */
    private static int[] frame(Page page) {
        return FRAMES.computeIfAbsent(page, p -> {
            List<?> size = (List<?>) p.evaluate("() => [outerWidth - innerWidth, outerHeight - innerHeight]");
            return new int[] {((Number) size.get(0)).intValue(), ((Number) size.get(1)).intValue()};
        });
    }

    private static int intOf(Page page, String script) {
        return ((Number) page.evaluate(script)).intValue();
    }

    /** @return the window state ("normal", "maximized", ...), or null if it cannot be read */
    private static String windowState(Page page) {
        try {
            CDPSession session = page.context().newCDPSession(page);
            try {
                JsonObject params = new JsonObject();
                params.addProperty("windowId", windowId(session));
                return session.send("Browser.getWindowBounds", params)
                        .getAsJsonObject("bounds").get("windowState").getAsString();
            } finally {
                session.detach();
            }
        } catch (PlaywrightException e) {
            return null;
        }
    }

    private static void setWindowState(Page page, String state) {
        try {
            CDPSession session = page.context().newCDPSession(page);
            try {
                JsonObject bounds = new JsonObject();
                bounds.addProperty("windowState", state);
                JsonObject params = new JsonObject();
                params.addProperty("windowId", windowId(session));
                params.add("bounds", bounds);
                session.send("Browser.setWindowBounds", params);
            } finally {
                session.detach();
            }
        } catch (PlaywrightException e) {
            System.out.println("[BROWSER] could not set the window " + state + " (chromium only): "
                    + e.getMessage().lines().findFirst().orElse(""));
        }
    }

    private static int windowId(CDPSession session) {
        return session.send("Browser.getWindowForTarget").get("windowId").getAsInt();
    }
}
