package com.agenticqa.healing;

import com.agenticqa.ui.healing.Fingerprint;
import com.agenticqa.ui.healing.HealingScorer;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Unit tests of the similarity score, using the real elements of the customers page. */
public class HealingScorerTest {

    private static final Fingerprint SEARCH_INPUT =
            new Fingerprint("input", "search-input", "q", "text", "Search by name", null, null, List.of());
    private static final Fingerprint NAME_INPUT =
            new Fingerprint("input", "new-customer-name", "name", "text", "Name", null, null, List.of());
    private static final Fingerprint ADD_BUTTON =
            new Fingerprint("button", "add-customer-button", null, "submit", null, null, "Add Customer", List.of());
    private static final Fingerprint SEARCH_BUTTON =
            new Fingerprint("button", "search-button", null, "submit", null, null, "Search", List.of());

    @Test
    public void identicalElementScoresOne() {
        assertEquals(HealingScorer.score(SEARCH_INPUT, SEARCH_INPUT), 1.0, 1e-9);
    }

    @Test
    public void renamedIdStillScoresHigh() {
        Fingerprint renamed = new Fingerprint("input", "customer-search", "q", "text", "Search by name", null, null, null);
        double score = HealingScorer.score(SEARCH_INPUT, renamed);
        assertTrue(score >= 0.75, "score was " + score);
    }

    @Test
    public void similarLookingNeighbourScoresLow() {
        double score = HealingScorer.score(SEARCH_INPUT, NAME_INPUT);
        assertTrue(score < 0.4, "score was " + score);
    }

    @Test
    public void renamedButtonBeatsTheOtherButtonClearly() {
        Fingerprint renamed = new Fingerprint("button", "create-customer", null, "submit", null, null, "Add Customer", null);
        double right = HealingScorer.score(ADD_BUTTON, renamed);
        double wrong = HealingScorer.score(ADD_BUTTON, SEARCH_BUTTON);
        assertTrue(right - wrong >= 0.15, "right " + right + " vs wrong " + wrong);
    }

    @Test
    public void emptyFingerprintScoresZero() {
        Fingerprint empty = new Fingerprint(null, null, null, null, null, null, null, null);
        assertEquals(HealingScorer.score(empty, SEARCH_INPUT), 0.0, 1e-9);
    }
}
