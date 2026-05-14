package com.examd.compiler.lexer;

import com.examd.compiler.diagnostics.Span;

/**
 * LexerException is what gets thrown when the lexer hits something
 * it genuinely cannot recover from.
 *
 * I could have just thrown a plain RuntimeException, but that loses
 * two things I need:
 *
 *   1. Location — a Span so the error message can point at the exact
 *      line and column where things went wrong.
 *   2. Error code — a stable string like "E001" so tooling can match
 *      on the code rather than the human-readable message, which might
 *      change wording between versions.
 *
 * Having a dedicated type also means catch blocks can distinguish a
 * lexer error from a parser error — useful once there are multiple
 * phases that can fail.
 *
 * Right now the lexer throws this immediately when it hits a hard error.
 * Later I'll add a DiagnosticCollector that accumulates errors
 * instead of stopping at the first one — so the compiler can report
 * everything wrong in a file in a single pass. This exception will
 * stick around for the truly unrecoverable cases even then.
 */
public class LexerException extends RuntimeException {

    /** Error code, e.g. "E001". Stable across versions so tooling can rely on it. */
    public final String errorCode;

    /** Where in the source file the error occurred. */
    public final Span span;

    public LexerException(String errorCode, String message, Span span) {
        // Bakes the code and location into the message so printStackTrace()
        // gives you everything in one line without needing to inspect fields.
        super(message + " [" + errorCode + "] at " + span);
        this.errorCode = errorCode;
        this.span      = span;
    }
}