package com.agenticqa.api;

import com.agenticqa.apis.CustomerApi;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** ApiFactory as in the existing framework: one instance per API class for the whole run. No service is called. */
public class ApiFactoryTest {

    /** An API class without a no-argument constructor. */
    public static class NeedsArgumentApi implements Api {
        public NeedsArgumentApi(String name) {}
    }

    @Test
    public void sameInstanceForTheWholeRunAndEveryThread() throws Exception {
        CustomerApi first = ApiFactory.getApi(CustomerApi.class);
        assertSame(ApiFactory.getApi(CustomerApi.class), first);
        assertSame(CompletableFuture.supplyAsync(() -> ApiFactory.getApi(CustomerApi.class)).get(), first,
                "shared across threads");
    }

    @Test
    public void failsClearlyWithoutANoArgumentConstructor() {
        RuntimeException e = expectThrows(RuntimeException.class, () -> ApiFactory.getApi(NeedsArgumentApi.class));
        assertTrue(e.getMessage().startsWith("Unable to create api instance: "), e.getMessage());
    }
}
