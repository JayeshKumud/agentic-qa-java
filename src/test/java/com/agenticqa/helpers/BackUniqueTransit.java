package com.agenticqa.helpers;

import com.agenticqa.apis.model.Unique;
import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A back unique transit for a time and a comma-separated list of uniques. Each unique that the scenario created
 * earlier (stored by base name under {@link Keys#BASE_UNIQUE}) is replaced by its registration id; any other
 * value is used as written:
 * <pre>
 * // earlier: TestSession.current().put(Keys.BASE_UNIQUE, "U1", new Unique("REG-1001"));
 * new BackUniqueTransit("10:00", "U1, REG-77").getUniqueRegIds();   // [REG-1001, REG-77]
 * </pre>
 * It finds the running scenario with {@link TestSession#current()}, so it can be created anywhere on the
 * scenario's thread, from a step or a helper, without the session being passed in.
 */
public class BackUniqueTransit {

    private static final Logger logger = LoggerFactory.getLogger(BackUniqueTransit.class);

    /**
     * @return the transit time, as given
     */
    @Getter
    private final String time;
    private final List<String> uniqueRegIds;

    /**
     * @param time      the transit time, as given
     * @param uniqueIds comma-separated base names or registration ids, for example "U1, U2, REG-77"
     * @throws IllegalStateException if no scenario is running on this thread
     */
    public BackUniqueTransit(String time, String uniqueIds) {
        this.time = time;
        this.uniqueRegIds = new ArrayList<>();

        if (uniqueIds != null && !uniqueIds.isBlank()) {
            Map<String, Unique> baseUniques = TestSession.current().map(Keys.BASE_UNIQUE);
            if (baseUniques.isEmpty()) {
                logger.info("No dynamic unique found");
            }
            for (String unique : uniqueIds.split(",")) {
                String name = unique.trim();                     // trimmed before the lookup, so "U1, U2" finds U2
                if (name.isEmpty()) continue;
                Unique stored = baseUniques.get(name);
                uniqueRegIds.add(stored != null ? stored.getUniqRegId() : name);
            }
            logger.info("Back unique transit at {}: {} -> {}", time, uniqueIds, uniqueRegIds);
        } else {
            // no uniques given: add the else branch of the original class here
            logger.info("Back unique transit at {}: no uniques given", time);
        }
    }

    /** @return the registration ids, in the order the uniques were given */
    public List<String> getUniqueRegIds() {
        return List.copyOf(uniqueRegIds);
    }
}
