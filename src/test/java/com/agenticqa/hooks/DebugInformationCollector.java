package com.agenticqa.hooks;

import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects what helps to understand a failed scenario and attaches it to Allure as "debug-info": the page's URL
 * and title (if the scenario opened a page) and the last API response ({@link Keys#RESPONSE}). Uses
 * {@link TestSession#current()}, so it must run before the browser is closed. Every part is best effort.
 */
public class DebugInformationCollector {
    private static final Logger logger = LoggerFactory.getLogger(DebugInformationCollector.class);
    private static final int MAX_BODY = 4000;

    /** Collects and attaches the debug information of the current scenario. */
    public void collect() {
        StringBuilder info = new StringBuilder();
        try {
            TestSession session = TestSession.current();
            if (session.hasPage()) {
                Page page = session.page();
                info.append("Page URL: ").append(page.url()).append('\n')
                        .append("Page title: ").append(page.title()).append('\n');
            }
            if (session.data().has(Keys.RESPONSE)) {
                Response response = session.data().get(Keys.RESPONSE);
                String body = response.asString();
                if (body.length() > MAX_BODY) body = body.substring(0, MAX_BODY) + "... (cut)";
                info.append("Last API response: ").append(response.getStatusLine()).append('\n').append(body);
            }
        } catch (RuntimeException e) {
            info.append("Debug information incomplete: ").append(e.getMessage());
        }
        if (info.isEmpty()) return;
        logger.info("Debug information:\n{}", info);
        Allure.addAttachment("debug-info", "text/plain", info.toString());
    }
}
