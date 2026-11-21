package com.agenticqa.core;

import com.agenticqa.config.Settings;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The trace and video files of one scenario, kept under target/artifacts when they are useful:
 * <ul>
 *   <li>the Playwright trace only when the scenario failed ({@link #stopTrace});</li>
 *   <li>the page's video when the scenario failed, or always with {@code ui.video=always}; the raw recording
 *       Playwright writes under target/artifacts/videos/recording is always deleted ({@link #keepVideo}).</li>
 * </ul>
 * File names carry a time stamp, the scenario name and the thread name, so parallel or retried scenarios with the
 * same name never overwrite each other's files. Everything is best effort: a file that cannot be saved or deleted
 * never hides the scenario's real result.
 */
final class ScenarioArtifacts {

    /** Where Playwright writes videos while recording; kept videos are copied to target/artifacts/videos. */
    static final Path VIDEO_RECORDING_DIR = Paths.get("target", "artifacts", "videos", "recording");

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final String scenarioName;

    /**
     * @param scenarioName the Cucumber scenario name, used in the file names
     */
    ScenarioArtifacts(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    /**
     * Stops tracing; the trace is written to a zip only when the scenario failed.
     *
     * @param context the scenario's browser context
     * @param failed  true if the scenario failed
     * @return the trace zip, or null if none was kept
     */
    Path stopTrace(BrowserContext context, boolean failed) {
        try {
            if (!failed) {
                context.tracing().stop();
                return null;
            }
            Path file = file("traces", ".zip");
            context.tracing().stop(new Tracing.StopOptions().setPath(file));
            System.out.println("Trace saved: " + file);
            return file;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The page's video, read before the context closes (afterwards Playwright no longer gives it).
     *
     * @param page the scenario's page, or null if none was opened
     * @return the video, or null when there is no page or video recording is off
     */
    static Video videoOf(Page page) {
        try {
            return page == null ? null : page.video();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Keeps the finished video when the scenario failed or {@code ui.video=always}, then deletes the raw
     * recording. Call it after the context is closed: closing finishes writing the video.
     *
     * @param video  the page's video ({@link #videoOf}), or null
     * @param failed true if the scenario failed
     * @return the kept video file, or null if none was kept
     */
    Path keepVideo(Video video, boolean failed) {
        if (video == null) return null;
        Path kept = null;
        if (failed || Settings.keepsAllVideos()) {
            try {
                Path file = file("videos", ".webm");
                video.saveAs(file);
                System.out.println("Video saved: " + file);
                kept = file;
            } catch (RuntimeException e) {
                // best effort
            }
        }
        deleteRecording(video);
        return kept;
    }

    /**
     * Deletes the raw recording under videos/recording: through Playwright, and if the file is still there, from
     * disk. Only this scenario's file; parallel scenarios record into the same folder.
     */
    private static void deleteRecording(Video video) {
        Path recording = null;
        try {
            recording = video.path();
            video.delete();
        } catch (RuntimeException e) {
            // tried again below
        }
        if (recording == null) return;
        try {
            Files.deleteIfExists(recording);
        } catch (Exception e) {
            System.out.println("Could not delete video recording " + recording + ": " + e.getMessage());
        }
    }

    /**
     * Path of a file of this scenario, for example
     * target/artifacts/traces/2026-11-09_14-05-09_create-and-find-a-customer_TestNG-1.zip
     *
     * @param folder    the folder under target/artifacts, for example traces or videos
     * @param extension the file extension with its dot, for example .zip
     * @return the file path
     */
    private Path file(String folder, String extension) {
        String slug = scenarioName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        // thread name keeps the files of parallel (or retried) scenarios with the same name apart
        String thread = Thread.currentThread().getName().replaceAll("[^A-Za-z0-9]+", "-");
        return Paths.get("target", "artifacts", folder,
                LocalDateTime.now().format(FILE_STAMP) + "_" + slug + "_" + thread + extension);
    }
}
