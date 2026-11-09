package com.agenticqa.ui.healing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scores how similar a page element is to a stored fingerprint. Pure logic, no browser, so it is unit tested.
 * <p>
 * Only properties the stored fingerprint actually has count. Each one has a weight (id and name matter most);
 * text-like values are compared word by word, so "search-input" and "customer-search" still share "search".
 * The result is the weighted share of matching properties, from 0 (nothing alike) to 1 (identical).
 */
public final class HealingScorer {

    private static final double TAG = 1, ID = 3, NAME = 3, TYPE = 1, PLACEHOLDER = 2, ARIA = 2, TEXT = 2, CLASSES = 1;

    /** Utility class, not instantiable. */
    private HealingScorer() {}

    /**
     * Similarity of a candidate to the expected fingerprint.
     *
     * @param expected  the stored fingerprint
     * @param candidate an element on the current page
     * @return a score from 0 to 1
     */
    public static double score(Fingerprint expected, Fingerprint candidate) {
        double total = 0, matched = 0;
        double[] part;

        part = exact(TAG, expected.tag(), candidate.tag());                total += part[0]; matched += part[1];
        part = words(ID, expected.id(), candidate.id());                   total += part[0]; matched += part[1];
        part = words(NAME, expected.name(), candidate.name());             total += part[0]; matched += part[1];
        part = exact(TYPE, expected.type(), candidate.type());             total += part[0]; matched += part[1];
        part = words(PLACEHOLDER, expected.placeholder(), candidate.placeholder()); total += part[0]; matched += part[1];
        part = words(ARIA, expected.ariaLabel(), candidate.ariaLabel());   total += part[0]; matched += part[1];
        part = words(TEXT, expected.text(), candidate.text());             total += part[0]; matched += part[1];
        part = sets(CLASSES, expected.classes(), candidate.classes());     total += part[0]; matched += part[1];

        return total == 0 ? 0 : matched / total;
    }

    /**
     * Exact, case-insensitive comparison.
     *
     * @return {weight counted, weight matched}; {0, 0} if the expected value is empty
     */
    private static double[] exact(double weight, String expected, String actual) {
        if (isEmpty(expected)) return new double[]{0, 0};
        return new double[]{weight, expected.equalsIgnoreCase(nullToEmpty(actual)) ? weight : 0};
    }

    /**
     * Word-overlap comparison (1 for equal values, otherwise the Jaccard index of their words).
     *
     * @return {weight counted, weight matched}; {0, 0} if the expected value is empty
     */
    private static double[] words(double weight, String expected, String actual) {
        if (isEmpty(expected)) return new double[]{0, 0};
        if (expected.equalsIgnoreCase(nullToEmpty(actual))) return new double[]{weight, weight};
        return new double[]{weight, weight * jaccard(tokens(expected), tokens(actual))};
    }

    /**
     * Set-overlap comparison for class lists.
     *
     * @return {weight counted, weight matched}; {0, 0} if the expected list is empty
     */
    private static double[] sets(double weight, List<String> expected, List<String> actual) {
        if (expected == null || expected.isEmpty()) return new double[]{0, 0};
        Set<String> a = new HashSet<>(expected);
        Set<String> b = actual == null ? Set.of() : new HashSet<>(actual);
        return new double[]{weight, weight * jaccard(a, b)};
    }

    /**
     * Splits a value into lowercase words at any non-alphanumeric character.
     *
     * @param value the value, may be null
     * @return its words
     */
    static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) return result;
        Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(s -> !s.isEmpty()).forEach(result::add);
        return result;
    }

    /**
     * Jaccard index: size of the intersection divided by size of the union.
     *
     * @return 0..1, and 0 when both sets are empty
     */
    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        return (double) common.size() / union.size();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
