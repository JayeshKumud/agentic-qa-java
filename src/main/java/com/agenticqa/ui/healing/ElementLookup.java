package com.agenticqa.ui.healing;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

/**
 * One element lookup of a scenario: which element of which locator file was asked for, the selector used, and
 * what the lookup found or why healing could not help. When a step fails, the last lookups are logged to the
 * report (see {@link Healer#lookups()}), so a timeout shows which element it was waiting for.
 *
 * @param key         the element, for example "homepage.addMasterOption.TestWINDOW"
 * @param description the readable name from the locator file
 * @param selector    the selector that was used (the healed one if the element was healed)
 * @param outcome     what happened, for example "found 1 element" or "cannot heal: ... no baseline fingerprint"
 * @param page        the page the lookup ran on, to count the matches again when the step fails; null for an
 *                    inner element, whose selector is relative to its parent
 */
public record ElementLookup(String key, String description, String selector, String outcome, Page page) {

    /**
     * Describes the lookup for the report, with the number of elements the selector matches now.
     *
     * @return a few lines of text
     */
    public String describe() {
        return key + " (" + description + ")" + System.lineSeparator()
                + "    selector: " + selector + System.lineSeparator()
                + "    lookup:   " + outcome + System.lineSeparator()
                + "    now:      " + matchesNow();
    }

    /** How many elements the selector matches at this moment, without waiting, and on which URL. */
    private String matchesNow() {
        try {
            if (page == null) return "not checked (relative to its parent element)";
            if (page.isClosed()) return "the page is closed";
            int count = page.locator(selector).count();
            return count + (count == 1 ? " element matches" : " elements match") + " on " + page.url();
        } catch (PlaywrightException e) {
            return "could not check: " + e.getMessage().lines().findFirst().orElse("");
        }
    }
}
