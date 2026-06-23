package com.eventsystem.infrastructure.init;

/**
 * Thrown when the initial-state file cannot be parsed or one of its commands
 * fails to execute. Carries the 1-based line number so the failure can be
 * reported precisely. Being a {@link RuntimeException}, it both aborts the
 * surrounding transaction (triggering rollback) and propagates out of the
 * {@code CommandLineRunner} to fail application startup — satisfying the
 * "all-or-nothing" initialization requirement (V3 §2.a.ii).
 */
public class InitFileException extends RuntimeException {

    private final int lineNumber;

    public InitFileException(int lineNumber, String message) {
        super(format(lineNumber, message));
        this.lineNumber = lineNumber;
    }

    public InitFileException(int lineNumber, String message, Throwable cause) {
        super(format(lineNumber, message), cause);
        this.lineNumber = lineNumber;
    }

    public int lineNumber() {
        return lineNumber;
    }

    private static String format(int lineNumber, String message) {
        if (lineNumber > 0) {
            return "Init-state file error on line " + lineNumber + ": " + message;
        }
        return "Init-state file error: " + message;
    }
}
