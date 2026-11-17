package com.agenticqa.hooks;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for the tags of a scenario, checked before it runs ({@link Hooks#checkTags}). Pure logic, unit tested.
 * <ul>
 *   <li>A scenario with a tag containing {@code @mute} (any case) is skipped.</li>
 *   <li>Every other scenario needs a test-case tag: the first tag that starts with the prefix (for example
 *       {@code @PRKLM-}) must be exactly the prefix followed by digits, for example {@code @PRKLM-1001}.</li>
 * </ul>
 * Tags inherited from the feature count too, because Cucumber gives a scenario its feature's tags.
 */
public final class TestCaseTag {

    /** Part of a tag that mutes a scenario. */
    static final String MUTE = "@MUTE";

    private TestCaseTag() {}

    /**
     * Tells whether the scenario is muted.
     *
     * @param tags the scenario's tags
     * @return true if a tag contains @mute, ignoring case
     */
    public static boolean isMuted(Collection<String> tags) {
        return tags.stream().map(tag -> tag.toUpperCase(Locale.ROOT)).anyMatch(tag -> tag.contains(MUTE));
    }

    /**
     * The first tag that starts with the prefix, ignoring case.
     *
     * @param tags   the scenario's tags
     * @param prefix the prefix, for example @PRKLM-
     * @return the tag, trimmed, or null if none starts with the prefix
     */
    public static String find(Collection<String> tags, String prefix) {
        String upper = prefix.toUpperCase(Locale.ROOT);
        for (String tag : tags) {
            if (tag != null && tag.trim().toUpperCase(Locale.ROOT).startsWith(upper)) return tag.trim();
        }
        return null;
    }

    /**
     * Checks that the scenario has a valid test-case tag.
     *
     * @param tags   the scenario's tags
     * @param prefix the prefix, for example @PRKLM-
     * @return the test-case tag, for example @PRKLM-1001
     * @throws AssertionError if there are no tags, none starts with the prefix, or the first one that does is not
     *                        the prefix followed by digits
     */
    public static String validate(Collection<String> tags, String prefix) {
        if (tags.isEmpty()) {
            throw new TagError("No tags found. Expected a tag starting with " + prefix + " (for example "
                    + prefix + "1001)");
        }
        String testCaseTag = find(tags, prefix);
        if (testCaseTag == null) {
            throw new TagError("Missing required " + prefix + " tag. Tags found: " + tags);
        }
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "\\d+", Pattern.CASE_INSENSITIVE);
        if (!pattern.matcher(testCaseTag).matches()) {
            throw new TagError("Invalid tag format. The first tag that starts with " + prefix
                    + " must be exactly " + prefix + "<digits>, but was " + testCaseTag);
        }
        return testCaseTag;
    }

    /**
     * A scenario's tags break the rules. {@code ScenarioRetry} never retries it: running the same tags again
     * cannot pass.
     */
    public static final class TagError extends AssertionError {
        /**
         * @param message what is wrong with the tags
         */
        public TagError(String message) {
            super(message);
        }
    }
}
