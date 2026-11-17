package com.agenticqa.runner;

import com.agenticqa.config.EnvConfig;
import com.agenticqa.config.Settings;
import io.cucumber.testng.Pickle;
import io.cucumber.testng.PickleWrapper;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs a failed scenario again, immediately and on its own: passed scenarios never run twice, and the rest of
 * the feature is not repeated. How often, and which scenarios, comes from the {@code retry} section of the
 * environment file ({@code count}, {@code tags}), overridable with {@code -Dretry.count} and {@code -Dretry.tags}.
 * <p>
 * Each attempt is a new Cucumber scenario run: PicoContainer builds new step objects, so it gets a new
 * browser context, a new {@code {unique}} token and its own cleanup. TestNG marks the earlier attempts as
 * skipped, so the build passes if the last attempt passes. Attempts are counted per scenario (feature file and
 * line) in a concurrent map, because scenarios run in parallel.
 */
public class ScenarioRetry implements IRetryAnalyzer {

    private static final Map<String, Integer> RETRIES = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult result) {
        Settings.Retry settings = EnvConfig.get().retry();
        Object[] parameters = result.getParameters();
        if (parameters.length == 0 || !(parameters[0] instanceof PickleWrapper wrapper)) return false;
        Pickle pickle = wrapper.getPickle();
        if (!settings.appliesTo(pickle.getTags())) return false;

        String key = pickle.getUri() + ":" + pickle.getScenarioLine();
        int retry = RETRIES.merge(key, 1, Integer::sum);
        if (retry > settings.count()) return false;
        System.out.printf("[RETRY] %s (%s) failed; running it again, retry %d of %d%n",
                pickle.getName(), key.substring(key.lastIndexOf('/') + 1), retry, settings.count());
        return true;
    }
}
