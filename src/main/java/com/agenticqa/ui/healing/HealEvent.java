package com.agenticqa.ui.healing;

/**
 * One healed locator: the original selector found nothing and another element was used instead.
 * The page object should be updated to the new selector.
 *
 * @param key         the element key, for example "customers.searchInput"
 * @param description readable element name, for example "search input"
 * @param oldSelector the selector in the page object
 * @param newSelector the selector that was used instead
 * @param score       similarity of the chosen element to the stored fingerprint (0..1)
 * @param runnerUp    score of the second best element, 0 if there was none
 * @param url         page URL when it happened
 * @param at          ISO-8601 timestamp
 */
public record HealEvent(String key, String description, String oldSelector, String newSelector,
                        double score, double runnerUp, String url, String at) {

    /**
     * One readable line for logs and reports.
     *
     * @return for example: search input: '#search-input' -> '#customer-search' (score 0.80, runner-up 0.27)
     */
    public String summary() {
        return String.format("%s: '%s' -> '%s' (score %.2f, runner-up %.2f) on %s",
                description, oldSelector, newSelector, score, runnerUp, url);
    }
}
