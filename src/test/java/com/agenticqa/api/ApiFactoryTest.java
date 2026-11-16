package com.agenticqa.api;

import com.agenticqa.core.TestSession;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** ApiFactory without a browser: creating a client does not open a context, only calling it does. */
public class ApiFactoryTest {

    /** A client without the (TestSession) constructor. */
    public static class NoSessionApi implements Api {
        public NoSessionApi() {}
    }

    @Test
    public void sameClientWithinAScenarioNewOneInTheNext() {
        ApiFactory scenarioOne = new ApiFactory(new TestSession()); // PicoContainer does this once per scenario
        CustomerApi first = scenarioOne.getApi(CustomerApi.class);
        assertSame(scenarioOne.getApi(CustomerApi.class), first, "reused within the scenario");

        ApiFactory scenarioTwo = new ApiFactory(new TestSession());
        assertNotSame(scenarioTwo.getApi(CustomerApi.class), first, "the next scenario gets its own client");
    }

    @Test
    public void failsClearlyWithoutTheSessionConstructor() {
        ApiFactory apis = new ApiFactory(new TestSession());
        IllegalStateException e = expectThrows(IllegalStateException.class, () -> apis.getApi(NoSessionApi.class));
        assertTrue(e.getMessage().contains("needs a public constructor (TestSession session)"), e.getMessage());
    }
}
