package com.agenticqa.core;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** TestSession.current() and the list helpers, without a browser. */
public class TestSessionThreadTest {

    private static final DataKey<List<String>> ITEMS = DataKey.listOf("items", String.class);

    private TestSession session;

    @AfterMethod(alwaysRun = true)
    public void closeSession() {
        if (session != null) session.close(false); // no browser was started, so this only unbinds
    }

    @Test
    public void currentReturnsTheBoundSessionUntilItCloses() {
        session = new TestSession();
        session.bindToCurrentThread();
        assertSame(TestSession.current(), session);

        session.close(false);
        expectThrows(IllegalStateException.class, TestSession::current);
    }

    @Test
    public void otherThreadsDoNotSeeTheSession() {
        session = new TestSession();
        session.bindToCurrentThread();
        boolean failedElsewhere = CompletableFuture.supplyAsync(() -> {
            try {
                TestSession.current();
                return false;
            } catch (IllegalStateException expected) {
                return true;
            }
        }).join();
        assertTrue(failedElsewhere, "a session is bound to its own thread only");
    }

    @Test
    public void listHelpersAddInOrderCopyAndFailClearly() {
        session = new TestSession();
        assertTrue(session.data().list(ITEMS).isEmpty());
        expectThrows(IllegalStateException.class, () -> session.data().requireList(ITEMS));
        expectThrows(IllegalArgumentException.class, () -> session.data().add(ITEMS, null));

        session.data().add(ITEMS, "a");
        session.data().add(ITEMS, "b");
        List<String> items = session.data().requireList(ITEMS);
        assertEquals(items, List.of("a", "b"));
        expectThrows(UnsupportedOperationException.class, () -> items.add("c")); // a copy, not the stored list
    }
}
