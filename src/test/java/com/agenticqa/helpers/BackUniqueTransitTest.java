package com.agenticqa.helpers;

import com.agenticqa.apis.model.Unique;
import com.agenticqa.core.TestSession;
import com.agenticqa.steps.Keys;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** BackUniqueTransit and the session's map helpers, without a browser or a service. */
public class BackUniqueTransitTest {

    private TestSession session;

    @AfterMethod(alwaysRun = true)
    public void closeSession() {
        if (session != null) session.close(false);
    }

    @Test
    public void replacesStoredBaseNamesTrimmedAndKeepsOtherValues() {
        session = new TestSession();
        session.bindToCurrentThread();
        session.data().put(Keys.BASE_UNIQUE, "U1", new Unique("REG-1001"));
        session.data().put(Keys.BASE_UNIQUE, "U2", new Unique("REG-1002"));

        BackUniqueTransit transit = new BackUniqueTransit("10:00", "U1, U2 ,REG-77,");
        assertEquals(transit.getUniqueRegIds(), List.of("REG-1001", "REG-1002", "REG-77"));
        assertEquals(transit.getTime(), "10:00");
    }

    @Test
    public void emptyOrMissingUniqueIdsGiveNoIds() {
        session = new TestSession();
        session.bindToCurrentThread();
        assertTrue(new BackUniqueTransit("10:00", "").getUniqueRegIds().isEmpty());
        assertTrue(new BackUniqueTransit("10:00", null).getUniqueRegIds().isEmpty());
    }

    @Test
    public void mapHelpersKeepOrderReplaceRejectNullAndReturnACopy() {
        session = new TestSession();
        assertTrue(session.data().map(Keys.BASE_UNIQUE).isEmpty());
        expectThrows(IllegalArgumentException.class, () -> session.data().put(Keys.BASE_UNIQUE, "U1", null));
        expectThrows(IllegalArgumentException.class, () -> session.data().put(Keys.BASE_UNIQUE, null, new Unique("R")));

        session.data().put(Keys.BASE_UNIQUE, "B", new Unique("REG-B"));
        session.data().put(Keys.BASE_UNIQUE, "A", new Unique("REG-A"));
        session.data().put(Keys.BASE_UNIQUE, "B", new Unique("REG-B2"));    // replaces, keeps its position
        Map<String, Unique> map = session.data().map(Keys.BASE_UNIQUE);
        assertEquals(List.copyOf(map.keySet()), List.of("B", "A"));
        assertEquals(map.get("B").getUniqRegId(), "REG-B2");
        expectThrows(UnsupportedOperationException.class, () -> map.put("C", new Unique("REG-C")));
    }

    @Test
    public void parallelScenariosEachSeeOnlyTheirOwnUniques() throws Exception {
        int scenarios = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);   // fewer threads than scenarios: threads are reused
        try {
            List<Future<List<String>>> results = new ArrayList<>();
            for (int i = 0; i < scenarios; i++) {
                int n = i;
                results.add(pool.submit(() -> {
                    TestSession own = new TestSession();             // what PicoContainer does per scenario
                    own.bindToCurrentThread();                       // what TestInitializeHooks.bindSession does
                    try {
                        own.data().put(Keys.BASE_UNIQUE, "U1", new Unique("REG-" + n));   // same name, own value
                        Thread.sleep(5);                             // let the scenarios overlap
                        return new BackUniqueTransit("10:00", "U1").getUniqueRegIds();
                    } finally {
                        own.close(false);                            // what TestInitializeHooks.closeBrowser does
                    }
                }));
            }
            for (int i = 0; i < scenarios; i++) {
                assertEquals(results.get(i).get(), List.of("REG-" + i), "scenario " + i + " saw another's unique");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void aReusedThreadDoesNotSeeThePreviousScenariosUniques() throws Exception {
        ExecutorService oneThread = Executors.newSingleThreadExecutor();
        try {
            oneThread.submit(() -> {
                TestSession first = new TestSession();
                first.bindToCurrentThread();
                first.data().put(Keys.BASE_UNIQUE, "U1", new Unique("REG-FIRST"));
                first.close(false);
                return null;
            }).get();
            List<String> second = oneThread.submit(() -> {
                TestSession next = new TestSession();
                next.bindToCurrentThread();
                try {
                    return new BackUniqueTransit("10:00", "U1").getUniqueRegIds();
                } finally {
                    next.close(false);
                }
            }).get();
            assertEquals(second, List.of("U1"), "the next scenario on the same thread starts empty");
        } finally {
            oneThread.shutdownNow();
        }
    }

    @Test
    public void needsARunningScenario() {
        expectThrows(IllegalStateException.class, () -> new BackUniqueTransit("10:00", "U1"));
    }
}
