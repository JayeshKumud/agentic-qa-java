package com.agenticqa.api;

import com.agenticqa.core.TestSession;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/** ApiFactory without a browser: creating a client does not open a context, only calling it does. */
public class ApiFactoryTest {

    private TestSession session;

    @AfterMethod(alwaysRun = true)
    public void closeSession() {
        if (session != null) session.close(false);
    }

    /** A client without the (TestSession) constructor. */
    public static class NoSessionApi implements Api {
        public NoSessionApi() {}
    }

    @Test
    public void sameClientWithinAScenarioNewOneInTheNext() {
        session = new TestSession();
        session.bindToCurrentThread();
        CustomerApi first = ApiFactory.getApi(CustomerApi.class);
        assertSame(ApiFactory.getApi(CustomerApi.class), first, "reused within the scenario");
        session.close(false);

        session = new TestSession();
        session.bindToCurrentThread();
        assertNotSame(ApiFactory.getApi(CustomerApi.class), first, "the next scenario gets its own client");
    }

    @Test
    public void failsClearlyOutsideAScenarioOrWithoutTheConstructor() {
        expectThrows(IllegalStateException.class, () -> ApiFactory.getApi(CustomerApi.class));

        session = new TestSession();
        session.bindToCurrentThread();
        expectThrows(IllegalStateException.class, () -> ApiFactory.getApi(NoSessionApi.class));
    }
}
