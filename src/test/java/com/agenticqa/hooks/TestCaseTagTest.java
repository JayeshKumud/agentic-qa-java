package com.agenticqa.hooks;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The scenario tag rules: @mute, and the required test-case tag. */
public class TestCaseTagTest {

    private static final String PREFIX = "@PRKLM-";

    @Test
    public void aTagContainingMuteMutesTheScenarioInAnyCase() {
        assertTrue(TestCaseTag.isMuted(List.of("@api", "@mute")));
        assertTrue(TestCaseTag.isMuted(List.of("@Mute")));
        assertTrue(TestCaseTag.isMuted(List.of("@muteUntilFixed")), "contains @mute, as in the original hook");
        assertFalse(TestCaseTag.isMuted(List.of("@api", "@PRKLM-1001")));
    }

    @Test
    public void aValidTestCaseTagIsReturned() {
        assertEquals(TestCaseTag.validate(List.of("@api", "@PRKLM-1001"), PREFIX), "@PRKLM-1001");
        assertEquals(TestCaseTag.validate(List.of("@prklm-7"), PREFIX), "@prklm-7", "prefix ignores case");
    }

    @Test
    public void noTagsFail() {
        AssertionError e = expectThrows(AssertionError.class, () -> TestCaseTag.validate(List.of(), PREFIX));
        assertTrue(e.getMessage().startsWith("No tags found"), e.getMessage());
    }

    @Test
    public void aMissingTestCaseTagFails() {
        AssertionError e = expectThrows(AssertionError.class,
                () -> TestCaseTag.validate(List.of("@api", "@smoke"), PREFIX));
        assertTrue(e.getMessage().startsWith("Missing required @PRKLM- tag"), e.getMessage());
        assertTrue(e instanceof TestCaseTag.TagError, "a TagError, so ScenarioRetry does not retry it");
    }

    @Test
    public void onlyDigitsMayFollowThePrefixOfTheFirstMatchingTag() {
        for (String bad : List.of("@PRKLM-", "@PRKLM-12a", "@PRKLM-ABC", "@PRKLM-1-2")) {
            AssertionError e = expectThrows(AssertionError.class,
                    () -> TestCaseTag.validate(List.of(bad, "@PRKLM-1001"), PREFIX));
            assertTrue(e.getMessage().startsWith("Invalid tag format"), bad + ": " + e.getMessage());
        }
    }

    @Test
    public void findReturnsTheFirstTagWithThePrefixOrNull() {
        assertEquals(TestCaseTag.find(List.of("@ui", "@PRKLM-2", "@PRKLM-3"), PREFIX), "@PRKLM-2");
        assertNull(TestCaseTag.find(List.of("@ui"), PREFIX));
    }
}
