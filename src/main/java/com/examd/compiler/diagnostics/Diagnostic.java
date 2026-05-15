package com.examd.compiler.diagnostics;

/**
 * Diagnostic — a single message produced during compilation.
 *
 * Diagnostics are the compiler's primary way of communicating with the
 * author of a .examd file. Every problem, warning, recovery hint,
 * or informational message eventually becomes a Diagnostic object.
 *
 * Example output:
 *
 *   error[E004] at physics_test.examd:47:1
 *   47 │  [SETCION: A]
 *      │  ^^^^^^^^^^^^ Unknown block header
 *   hint: Did you mean [SECTION]?
 *
 * The goal is not just to report failures, but to make problems easy
 * to understand and quick to fix.
 *
 * DESIGN PRINCIPLES
 * ─────────────────────────────────────────────────────────────
 *
 * 1. Precise locations
 *    Diagnostics should point to the exact region in the source
 *    that caused the problem.
 *
 * 2. Stable diagnostic codes
 *    Codes such as "E004" are intended to remain stable across
 *    compiler versions so documentation, IDE tooling, and future
 *    quick-fix systems can rely on them.
 *
 * 3. Human-readable messages
 *    Compiler output is part of the developer experience.
 *    Messages should feel natural and avoid unnecessary jargon.
 *
 * 4. Immutable value objects
 *    Diagnostics are created once and never modified afterwards.
 *    This prevents accidental mutation while they move through
 *    compiler phases and rendering systems.
 */
public final class Diagnostic {

    // ── Fields ────────────────────────────────────────────────────────────

    /**
     * Indicates how serious this diagnostic is.
     *
     * Examples:
     *   ERROR   → compilation cannot safely continue
     *   WARNING → output can still be generated
     *   INFO    → informational compiler message
     *   HINT    → optional suggestion or style improvement
     */
    public final DiagnosticSeverity severity;

    /**
     * Stable identifier associated with this diagnostic.
     *
     * Examples:
     *   E001 → Error
     *   W003 → Warning
     *   I001 → Informational message
     *   H002 → Hint
     *
     * The wording of messages may evolve over time, but diagnostic
     * codes are intended to stay stable so external tooling can
     * depend on them safely.
     */
    public final String code;

    /**
     * Primary human-readable explanation shown to the user.
     *
     * Example:
     *   "Unterminated quoted string"
     *
     * Messages should explain the actual problem directly rather
     * than exposing compiler internals.
     */
    public final String message;

    /**
     * Exact source location associated with this diagnostic.
     *
     * Spans allow renderers and future IDE integrations to:
     *   - display file/line/column information
     *   - underline problematic regions
     *   - highlight source snippets
     *   - support navigation to the failing location
     *
     * Diagnostics without locations are difficult to act on,
     * so spans are always required.
     */
    public final Span span;

    /**
     * Optional recovery guidance shown after the main message.
     *
     * Example:
     *   "Did you mean [SECTION]?"
     *
     * Suggestions are one of the most valuable parts of compiler
     * diagnostics because they help users recover quickly instead
     * of forcing them to guess the intended syntax.
     */
    public final String suggestion;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Creates a fully populated diagnostic.
     *
     * All required fields are validated immediately so invalid
     * diagnostics cannot silently enter later compiler phases.
     */
    public Diagnostic(DiagnosticSeverity severity, String code,
                      String message, Span span, String suggestion) {

        // Every diagnostic must have a severity level.
        if (severity == null)
            throw new IllegalArgumentException("severity required");

        // Stable diagnostic codes are required for tooling support.
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("code required");

        // Empty messages are not useful to users.
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("message required");

        // Diagnostics must always point somewhere in the source.
        if (span == null)
            throw new IllegalArgumentException("span required");

        this.severity   = severity;
        this.code       = code;
        this.message    = message;
        this.span       = span;
        this.suggestion = suggestion;
    }

    /**
     * Convenience constructor used when no recovery suggestion exists.
     *
     * Many diagnostics only need a severity, code, message,
     * and source location.
     */
    public Diagnostic(DiagnosticSeverity severity, String code,
                      String message, Span span) {
        this(severity, code, message, span, null);
    }

    // ── Static factories ──────────────────────────────────────────────────

    /**
     * Creates an ERROR diagnostic with a recovery suggestion.
     *
     * Errors represent problems severe enough to prevent valid
     * output generation.
     */
    public static Diagnostic error(String code, String message,
                                   Span span, String suggestion) {
        return new Diagnostic(
                DiagnosticSeverity.ERROR,
                code,
                message,
                span,
                suggestion
        );
    }

    /**
     * Creates an ERROR diagnostic without a suggestion.
     */
    public static Diagnostic error(String code, String message,
                                   Span span) {
        return new Diagnostic(
                DiagnosticSeverity.ERROR,
                code,
                message,
                span,
                null
        );
    }

    /**
     * Creates a WARNING diagnostic.
     *
     * Warnings indicate suspicious or potentially unintended input,
     * but compilation can still continue safely.
     */
    public static Diagnostic warning(String code, String message,
                                     Span span, String suggestion) {
        return new Diagnostic(
                DiagnosticSeverity.WARNING,
                code,
                message,
                span,
                suggestion
        );
    }

    /**
     * Creates a WARNING diagnostic without a suggestion.
     */
    public static Diagnostic warning(String code, String message,
                                     Span span) {
        return new Diagnostic(
                DiagnosticSeverity.WARNING,
                code,
                message,
                span,
                null
        );
    }

    /**
     * Creates a HINT diagnostic.
     *
     * Hints are lightweight suggestions intended to improve
     * usability, readability, or authoring experience.
     */
    public static Diagnostic hint(String code, String message,
                                  Span span) {
        return new Diagnostic(
                DiagnosticSeverity.HINT,
                code,
                message,
                span,
                null
        );
    }

    // ── Query ─────────────────────────────────────────────────────────────

    /**
     * Returns true if this diagnostic should prevent
     * final output generation.
     *
     * In practice this usually means the severity is ERROR.
     */
    public boolean isBlocking() {
        return severity.isBlocking();
    }

    /**
     * Returns true if recovery guidance is attached.
     *
     * Renderers may use this to decide whether an additional
     * "help" or "hint" section should be displayed.
     */
    public boolean hasSuggestion() {
        return suggestion != null && !suggestion.isBlank();
    }

    // ── Display ───────────────────────────────────────────────────────────

    /**
     * Produces a compact one-line representation suitable for logs,
     * debugging, and summary output.
     *
     * Example:
     *   error[E001] at file.examd:5:10 — Unterminated quoted string
     */
    @Override
    public String toString() {
        return severity.label()
                + "[" + code + "] at "
                + span
                + " — "
                + message;
    }
}