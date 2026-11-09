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

    private final Settings.Healing settings;
    private final FingerprintStore store;
    private final Map<String, String> healedSelectors = new HashMap<>();
    private final List<HealEvent> events = new ArrayList<>();

    /**
     * Creates the healer of one scenario.
     *
     * @param settings healing settings of the active environment
     */
    public Healer(Settings.Healing settings) {
        this.settings = settings;
        this.store = FingerprintStore.at(settings.fingerprintsFile());
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
     * Finds the element of a smart locator, healing it when needed.
     *
     * @param page    the page to search
     * @param element the element definition
     * @return a locator for the element (the original one if healing is off or not possible)
     */
    public synchronized Locator resolve(Page page, SmartLocator element) {
        Locator original = page.locator(element.selector());
        if (!settings.enabled()) return original;

        String healed = healedSelectors.get(element.key());
        if (healed != null) return page.locator(healed);

        if (appears(original)) {
            if (original.count() == 1) capture(element.key(), original);
            return original;
        }
        return heal(page, element, original);
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
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(settings.probeTimeoutMs()));
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
            log("cannot heal %s: '%s' found nothing and there is no baseline fingerprint yet",
                    element.description(), element.selector());
            return original;
        }

        List<Candidate> ranked = candidates(page, expected);
        if (ranked.isEmpty()) {
            log("cannot heal %s: the page has no elements", element.description());
            return original;
        }
        Candidate best = ranked.get(0);
        double runnerUp = ranked.size() > 1 ? ranked.get(1).score() : 0;

        if (best.score() < settings.minScore()) {
            log("cannot heal %s: best match '%s' scores %.2f, below the minimum %.2f",
                    element.description(), best.selector(), best.score(), settings.minScore());
            return original;
        }
        if (best.score() - runnerUp < MIN_MARGIN) {
            log("cannot heal %s: '%s' (%.2f) and '%s' (%.2f) are too similar to choose safely",
                    element.description(), best.selector(), best.score(), ranked.get(1).selector(), runnerUp);
            return original;
        }

        HealEvent event = new HealEvent(element.key(), element.description(), element.selector(), best.selector(),
                round(best.score()), round(runnerUp), page.url(), OffsetDateTime.now().toString());
        events.add(event);
        healedSelectors.put(element.key(), best.selector());
        log("HEALED %s", event.summary());
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
        Path file = Paths.get(settings.reportFile());
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
