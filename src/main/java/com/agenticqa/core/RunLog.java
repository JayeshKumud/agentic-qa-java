package com.agenticqa.core;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Copies everything the run prints (the [API], [BROWSER], [AUTH], [RETRY] lines, Cucumber's pretty output, errors)
 * into one log file, {@code cleanup.logFile} in config/common.yaml, while it still appears on the console. The
 * file is emptied by {@link RunCleaner} before each run, so it always holds exactly the last run.
 */
public final class RunLog {

    private static OutputStream file;
    private static PrintStream originalOut;
    private static PrintStream originalErr;

    /** Utility class, not instantiable. */
    private RunLog() {}

    /**
     * Starts copying System.out and System.err into the file. Calling it again while running does nothing.
     *
     * @param logFile the file, for example target/logs/run.log; null or blank does nothing
     */
    public static synchronized void start(String logFile) {
        if (file != null || logFile == null || logFile.isBlank()) return;
        Path path = Paths.get(logFile);
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            file = new FileOutputStream(path.toFile(), true);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open run log " + path, e);
        }
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(new Tee(originalOut, file), true));
        System.setErr(new PrintStream(new Tee(originalErr, file), true));
        System.out.println("[LOG] run log: " + path.toAbsolutePath());
    }

    /** Stops copying, restores the console streams and closes the file. */
    public static synchronized void stop() {
        if (file == null) return;
        System.out.flush();
        System.err.flush();
        System.setOut(originalOut);
        System.setErr(originalErr);
        try {
            file.close();
        } catch (IOException ignored) {
            // best effort
        }
        file = null;
    }

    /** Writes every byte to the console stream and to the file. */
    private static final class Tee extends OutputStream {
        private final OutputStream console;
        private final OutputStream copy;

        Tee(OutputStream console, OutputStream copy) {
            this.console = console;
            this.copy = copy;
        }

        @Override
        public void write(int b) throws IOException {
            console.write(b);
            synchronized (copy) {
                copy.write(b);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            console.write(bytes, offset, length);
            synchronized (copy) {
                copy.write(bytes, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            console.flush();
            synchronized (copy) {
                copy.flush();
            }
        }
    }
}
