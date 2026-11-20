package com.agenticqa.ui.healing;

import com.agenticqa.config.Settings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@link SmartLocator}s for one scenario.
 * <ol>
 *   <li>If the page object's selector matches one element, its fingerprint is saved as the new baseline and the
 *       normal locator is returned.</li>
 *   <li>If it matches nothing within the probe timeout, every element on the page is scored against the baseline.
 *       The best one is used only if it reaches minScore and clearly beats the runner-up; the heal is logged,
 *       kept for the scenario report and appended to the report file.</li>
 *   <li>Otherwise the original locator is returned, so the test fails with Playwright's normal error.</li>
 * </ol>
 * A healed selector is reused for the rest of the scenario, so the probe delay is paid once per element.
 */
public class Healer {

    /** The best candidate must beat the runner-up by at least this much, otherwise the choice is a guess. */
    static final double MIN_MARGIN = 0.15;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Collects every element on the page with its fingerprint and a selector that finds exactly it. */
    private static final String CANDIDATES_JS = """
            () => {
              const capture = %s;
              const unique = sel => { try { return document.querySelectorAll(sel).length === 1; } catch (e) { return false; } };
              const selectorFor = el => {
                if (el.id && unique('#' + CSS.escape(el.id))) return '#' + CSS.escape(el.id);
                const tag = el.tagName.toLowerCase();
                const name = el.getAttribute('name');
                if (name) { const s = tag + '[name="' + CSS.escape(name) + '"]'; if (unique(s)) return s; }
                const parts = [];
                for (let e = el; e && e.nodeType === 1 && e !== document.documentElement; e = e.parentElement) {
                  if (e !== el && e.id && unique('#' + CSS.escape(e.id))) { parts.unshift('#' + CSS.escape(e.id)); break; }
                  let i = 1;
                  for (let s = e.previousElementSibling; s; s = s.previousElementSibling) if (s.tagName === e.tagName) i++;
                  parts.unshift(e.tagName.toLowerCase() + ':nth-of-type(' + i + ')');
                }
                return parts.join(' > ');
              };
              return [...document.body.querySelectorAll('*')].slice(0, 3000)
                .filter(el => !['script', 'style', 'template'].includes(el.tagName.toLowerCase()))
                .map(el => ({ fingerprint: capture(el), selector: selectorFor(el) }));
            }""".formatted(Fingerprint.CAPTURE_JS);

    private final FingerprintStore store;
    private final Map<String, String> healedSelectors = new HashMap<>();
    private final List<HealEvent> events = new ArrayList<>();
    private final List<ElementLookup> lookups = new ArrayList<>();

    /** How many lookups are kept; a failed step needs only the last few. */
    static final int MAX_LOOKUPS = 20;

    /** Creates the healer of one scenario, with the healing settings of the active environment. */
    public Healer() {
        this.store = FingerprintStore.at(Settings.getFingerprintsFile());
    }

    /**
     * Heals that happened in this scenario so far.
     *
     * @return an unmodifiable list, empty if nothing was healed
     */
    public synchronized List<HealEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * The last element lookups of this scenario, oldest first. Logged to the report when a step fails.
     *
     * @return an unmodifiable list, at most {@value #MAX_LOOKUPS} entries
     */
    public synchronized List<ElementLookup> lookups() {
        return Collections.unmodifiableList(new ArrayList<>(lookups));
    }

    /**
     * Records a lookup, also of elements that are not healed, so a failed step can show what it looked for.
     *
     * @param page        the page searched
     * @param key         the element key
     * @param description the readable name
     * @param selector    the selector used
     * @param outcome     what the lookup found
     */
    public synchronized void record(Page page, String key, String description, String selector, String outcome) {
        if (lookups.size() == MAX_LOOKUPS) lookups.remove(0);
        lookups.add(new ElementLookup(key, description, selector, outcome, page));
    }

    /**
     * Finds the element of a smart locator, healing it when needed.
     *
     * @param page    the page to search
     * @param element the element definition
     * @return a locator for the element (the original one if healing is off or not possible)
     */
    public synchronized Locator resolve(Page page, SmartLocator element) {
        Locator original = page.locator(element.selector());
        if (!Settings.isHealingEnabled()) {
            record(page, element, element.selector(), "healing is off (healing.enabled=false)");
            return original;
        }

        String healed = healedSelectors.get(element.key());
        if (healed != null) {
            record(page, element, healed, "healed earlier in this scenario from '" + element.selector() + "'");
            return page.locator(healed);
        }

        if (appears(original)) {
            int count = original.count();
            if (count == 1) capture(element.key(), original);
            record(page, element, element.selector(), count == 1 ? "found 1 element"
                    : "found " + count + " elements (an action needs exactly 1, make the selector more specific)");
            return original;
        }
        return heal(page, element, original);
    }

    private void record(Page page, SmartLocator element, String selector, String outcome) {
        record(page, element.key(), element.description(), selector, outcome);
    }

    /** Logs why healing failed, records the lookup and returns the original locator. */
    private Locator cannotHeal(Page page, SmartLocator element, Locator original, String reason) {
        log("cannot heal %s: %s", element.description(), reason);
        record(page, element, element.selector(), "found nothing within " + Settings.getProbeTimeoutMs()
                + " ms; cannot heal: " + reason);
        return original;
    }

    /**
     * Waits up to the probe timeout for the selector to match something.
     *
     * @param locator the original locator
     * @return true if at least one element is attached
     */
    private boolean appears(Locator locator) {
        try {
            locator.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(Settings.getProbeTimeoutMs()));
            return true;
        } catch (PlaywrightException timeout) {
            return false;
        }
    }

    /**
     * Saves the fingerprint of an element whose selector works.
     *
     * @param key     the element key
     * @param locator a locator that matches exactly one element
     */
    private void capture(String key, Locator locator) {
        try {
            Object raw = locator.evaluate(Fingerprint.CAPTURE_JS);
            store.put(key, MAPPER.convertValue(raw, Fingerprint.class));
        } catch (PlaywrightException ignored) {
            // the page navigated in between; the next resolve captures it
        }
    }

    /**
     * Looks for the element that best matches the stored fingerprint.
     *
     * @param page     the page
     * @param element  the element definition
     * @param original the locator that found nothing
     * @return a locator for the replacement, or the original one if no safe replacement exists
     */
    private Locator heal(Page page, SmartLocator element, Locator original) {
        Fingerprint expected = store.get(element.key());
        if (expected == null) {
            return cannotHeal(page, element, original, "'" + element.selector()
                    + "' found nothing and there is no baseline fingerprint yet");
        }

        List<Candidate> ranked = candidates(page, expected);
        if (ranked.isEmpty()) {
            return cannotHeal(page, element, original, "the page has no elements");
        }
        Candidate best = ranked.get(0);
        double runnerUp = ranked.size() > 1 ? ranked.get(1).score() : 0;

        if (best.score() < Settings.getMinScore()) {
            return cannotHeal(page, element, original, String.format("best match '%s' scores %.2f, below the minimum %.2f",
                    best.selector(), best.score(), Settings.getMinScore()));
        }
        if (best.score() - runnerUp < MIN_MARGIN) {
            return cannotHeal(page, element, original, String.format("'%s' (%.2f) and '%s' (%.2f) are too similar to choose safely",
                    best.selector(), best.score(), ranked.get(1).selector(), runnerUp));
        }

        HealEvent event = new HealEvent(element.key(), element.description(), element.selector(), best.selector(),
                round(best.score()), round(runnerUp), page.url(), OffsetDateTime.now().toString());
        events.add(event);
        healedSelectors.put(element.key(), best.selector());
        log("HEALED %s", event.summary());
        record(page, element, best.selector(), "HEALED from '" + element.selector() + "' (score " + round(best.score()) + ")");
        appendToReport(event);
        return page.locator(best.selector());
    }

    /**
     * Scores every element of the page.
     *
     * @param page     the page
     * @param expected the stored fingerprint
     * @return candidates, best first
     */
    @SuppressWarnings("unchecked")
    private List<Candidate> candidates(Page page, Fingerprint expected) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) page.evaluate(CANDIDATES_JS);
        List<Candidate> result = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            Fingerprint fp = MAPPER.convertValue(entry.get("fingerprint"), Fingerprint.class);
            result.add(new Candidate((String) entry.get("selector"), HealingScorer.score(expected, fp)));
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return result;
    }

    /**
     * Appends one JSON line to the report file. Synchronized on the class, because all scenarios share the file.
     *
     * @param event the heal
     */
    private void appendToReport(HealEvent event) {
        Path file = Paths.get(Settings.getHealingReportFile());
        synchronized (Healer.class) {
            try {
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                Files.writeString(file, MAPPER.writeValueAsString(event) + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            } catch (IOException e) {
                log("could not write %s: %s", file, e.getMessage());
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static void log(String format, Object... args) {
        System.out.println("[HEAL] " + String.format(format, args));
    }

    /**
     * A page element and its similarity to the baseline.
     *
     * @param selector a selector that finds exactly this element
     * @param score    similarity, 0..1
     */
    private record Candidate(String selector, double score) {}
}
