package com.agenticqa.core;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RunCleanerTest {

    @Test
    public void emptiesAFolderButKeepsTheRerunFile() throws Exception {
        Path folder = Files.createTempDirectory("cleaner");
        Path old = Files.writeString(Files.createDirectories(folder.resolve("traces")).resolve("old.zip"), "x");
        Path rerun = Files.writeString(folder.resolve("failed-scenarios.txt"), "a.feature:3");
        RunCleaner.emptyFolder(folder, List.of(rerun));
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(folder.resolve("traces")));
        assertEquals(Files.readString(rerun), "a.feature:3");
    }

    @Test
    public void refusesFoldersOutsideTheProject() {
        assertTrue(RunCleaner.isUnsafe(Paths.get("x").toAbsolutePath()));
        assertTrue(RunCleaner.isUnsafe(Paths.get("../other").normalize()));
        assertTrue(RunCleaner.isUnsafe(Paths.get(".").normalize()));
        assertFalse(RunCleaner.isUnsafe(Paths.get("target/reports").normalize()));
    }

    @Test
    public void findsRerunFilesInCucumberFeatures() {
        assertEquals(RunCleaner.rerunFiles("@target/reports/failed-scenarios.txt"),
                List.of(Paths.get("target/reports/failed-scenarios.txt")));
        assertEquals(RunCleaner.rerunFiles("src/test/resources/features"), List.of());
        assertEquals(RunCleaner.rerunFiles(null), List.of());
    }

    @Test
    public void uniqueNumbersNeverRepeatAcrossThreads() {
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 20_000).parallel().forEach(i -> seen.add(TestData.uniqueNumber()));
        assertEquals(seen.size(), 20_000);
    }
}
