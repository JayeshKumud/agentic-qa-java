package com.agenticqa.core;

import com.agenticqa.config.Settings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Empties the log and report folders of earlier runs ({@code cleanup.folders} in config/common.yaml) before a
 * new run starts, so reports, traces, heal logs and the run log show only the new run, also without
 * {@code mvn clean}. Switch it off for one run with {@code -Dcleanup.beforeRun=false}.
 * <p>
 * The rerun file of {@code -Dcucumber.features=@target/reports/failed-scenarios.txt} is kept: Cucumber reads it
 * after the cleanup, so deleting it would leave nothing to rerun.
 */
public final class RunCleaner {

    /** Utility class, not instantiable. */
    private RunCleaner() {}

    /**
     * Empties the configured folders, if cleanup is on.
     *
     * @param cleanup the cleanup settings
     * @return the folders that were emptied
     */
    public static List<Path> clean(Settings.Cleanup cleanup) {
        if (!cleanup.beforeRun()) {
            System.out.println("[CLEANUP] off (cleanup.beforeRun=false): reports of earlier runs are kept");
            return List.of();
        }
        List<Path> keep = rerunFiles(System.getProperty("cucumber.features"));
        List<Path> cleaned = new ArrayList<>();
        for (String folder : cleanup.folders()) {
            Path path = Paths.get(folder).normalize();
            if (isUnsafe(path)) {
                throw new IllegalStateException("cleanup.folders may only name folders inside the project, not '"
                        + folder + "'");
            }
            if (Files.exists(path)) {
                emptyFolder(path, keep);
                cleaned.add(path);
            }
        }
        System.out.println("[CLEANUP] emptied before the run: " + (cleaned.isEmpty() ? "nothing to clean" : cleaned));
        return cleaned;
    }

    /**
     * Deletes everything in a folder except the given files (and the folders that hold them).
     *
     * @param folder the folder
     * @param keep   files to keep
     */
    static void emptyFolder(Path folder, List<Path> keep) {
        Map<Path, byte[]> kept = new LinkedHashMap<>();
        try {
            for (Path file : keep) {
                if (file.toAbsolutePath().startsWith(folder.toAbsolutePath()) && Files.isRegularFile(file)) {
                    kept.put(file, Files.readAllBytes(file));
                }
            }
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!file.equals(folder)) Files.deleteIfExists(file);
                }
            }
            for (Map.Entry<Path, byte[]> file : kept.entrySet()) {
                Files.createDirectories(file.getKey().toAbsolutePath().getParent());
                Files.write(file.getKey(), file.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot empty " + folder + " (is a report still open in another "
                    + "program?)", e);
        }
    }

    /**
     * A relative path inside the project is safe; absolute paths and paths leaving the project ({@code ..}) are not.
     *
     * @param path a normalized path
     * @return true if it must not be deleted
     */
    static boolean isUnsafe(Path path) {
        return path.isAbsolute() || path.toString().isEmpty() || path.startsWith("..") || path.toString().equals(".");
    }

    /**
     * Files named in cucumber.features with a leading @ (Cucumber's rerun files).
     *
     * @param features the cucumber.features property, may be null
     * @return the rerun files
     */
    static List<Path> rerunFiles(String features) {
        if (features == null || features.isBlank()) return List.of();
        List<Path> files = new ArrayList<>();
        for (String part : features.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("@")) files.add(Paths.get(trimmed.substring(1)).normalize());
        }
        return files;
    }
}
