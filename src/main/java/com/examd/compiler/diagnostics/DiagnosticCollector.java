package com.examd.compiler.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DiagnosticCollector — the central accumulator for all compiler messages.
 *
 * ═══════════════════════════════════════════════════════════════
 *  WHY COLLECT INSTEAD OF THROW?
 * ═══════════════════════════════════════════════════════════════
 *
 * Imagine writing a .examd exam file with 5 mistakes.
 *
 * With throw-on-first-error:
 *   Compile → see error #1 → fix it → compile again → see error #2 → fix...
 *   Five mistakes = five compile-fix cycles. Incredibly frustrating.
 *
 * With DiagnosticCollector:
 *   Compile → see all 5 errors at once → fix them all → compile again → done.
 *   This is how javac, rustc, and clang work. It's a UX decision as much
 *   as a technical one.
 *
 * HOW IT WORKS:
 * Every compiler phase receives the same DiagnosticCollector instance.
 * Instead of throwing an exception when something is wrong, it calls
 * collector.add(Diagnostic.error(...)). Processing continues, collecting
 * more diagnostics. At the end of each phase, the pipeline checks
 * collector.hasErrors() and decides whether to proceed to the next phase.
 *
 * THE PIPELINE CONTRACT:
 *
 *   Phase 1 (Lexer)     → emits E001–E003 → if hasErrors → skip phases 2-5
 *   Phase 2 (Parser)    → emits E004–E010 → if hasErrors → skip phases 3-5
 *   Phase 3 (Validator) → emits E011–E020 → if hasErrors → skip phases 4-5
 *   Phase 4 (Optimizer) → emits W001–W009 → never blocks (warnings only)
 *   Phase 5 (Generator) → emits E021+    → output written only if no errors
 *
 * ERROR LIMIT (abort threshold):
 * After MAX_ERRORS errors, we stop collecting and abort. This prevents
 * pathological cases (e.g., a completely garbled file) from producing
 * thousands of cascade errors that obscure the root cause.
 * The limit is 50, matching javac's default behaviour.
 *
 * THREAD SAFETY:
 * Not thread-safe — the compiler pipeline is single-threaded.
 * Do not share a DiagnosticCollector across threads.
 */
public final class DiagnosticCollector {

    /**
     * Maximum number of ERROR-level diagnostics before aborting.
     * After this limit, only the "too many errors" message is added.
     * Matches javac's default of 100 — we use 50 for a DSL compiler.
     */
    public static final int MAX_ERRORS = 50;

    /** All accumulated diagnostics, in order of addition. */
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    /** Count of ERROR-severity diagnostics only — cached for fast hasErrors(). */
    private int errorCount = 0;

    /** True once MAX_ERRORS is reached and the abort limit message has been added. */
    private boolean abortLimitReached = false;

    // ── Adding diagnostics ────────────────────────────────────────────────

    /**
     * Adds a diagnostic to the collection.
     *
     * If the error limit has been reached, this method silently does nothing
     * (except the first time, where it replaces further errors with an
     * "too many errors" message).
     *
     * @param diagnostic The diagnostic to record. Must not be null.
     */
    public void add(Diagnostic diagnostic) {
        if (diagnostic == null) throw new IllegalArgumentException("Diagnostic cannot be null");

        if (abortLimitReached) return;

        if (diagnostic.severity == DiagnosticSeverity.ERROR) {
            errorCount++;
            if (errorCount >= MAX_ERRORS) {
                abortLimitReached = true;
                // Add the abort message as the last diagnostic
                diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "E000",
                    "Too many errors (" + MAX_ERRORS + "). Compilation aborted. Fix earlier errors first.",
                    diagnostic.span
                ));
                return;
            }
        }

        diagnostics.add(diagnostic);
    }

    /**
     * Convenience: creates and adds an ERROR diagnostic in one call.
     *
     * @param code       Stable error code, e.g. "E001"
     * @param message    Human-readable message
     * @param span       Source location
     * @param suggestion Optional fix suggestion, or null
     */
    public void error(String code, String message, Span span, String suggestion) {
        add(Diagnostic.error(code, message, span, suggestion));
    }

    /** Convenience: ERROR without suggestion. */
    public void error(String code, String message, Span span) {
        add(Diagnostic.error(code, message, span));
    }

    /** Convenience: WARNING with suggestion. */
    public void warning(String code, String message, Span span, String suggestion) {
        add(Diagnostic.warning(code, message, span, suggestion));
    }

    /** Convenience: WARNING without suggestion. */
    public void warning(String code, String message, Span span) {
        add(Diagnostic.warning(code, message, span));
    }

    /** Convenience: HINT. */
    public void hint(String code, String message, Span span) {
        add(Diagnostic.hint(code, message, span));
    }

    // ── Querying ──────────────────────────────────────────────────────────

    /**
     * Returns true if at least one ERROR-level diagnostic has been added.
     * The pipeline uses this to decide whether to proceed to the next phase.
     */
    public boolean hasErrors() {
        return errorCount > 0;
    }

    /** Returns the number of ERROR-level diagnostics. */
    public int errorCount() {
        return errorCount;
    }

    /** Returns the total count of all diagnostics (all severities). */
    public int totalCount() {
        return diagnostics.size();
    }

    /** Returns true if no diagnostics of any kind have been added. */
    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

    /**
     * Returns an unmodifiable view of all diagnostics.
     * Order is the order in which they were added (i.e. source order).
     */
    public List<Diagnostic> getAll() {
        return Collections.unmodifiableList(diagnostics);
    }

    /**
     * Returns only the ERROR-level diagnostics.
     * Useful when you want to show only fatal problems.
     */
    public List<Diagnostic> getErrors() {
        return diagnostics.stream()
            .filter(d -> d.severity == DiagnosticSeverity.ERROR)
            .collect(Collectors.toList());
    }

    /**
     * Returns only the WARNING-level diagnostics.
     */
    public List<Diagnostic> getWarnings() {
        return diagnostics.stream()
            .filter(d -> d.severity == DiagnosticSeverity.WARNING)
            .collect(Collectors.toList());
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    /**
     * Renders all diagnostics as a human-readable report string.
     *
     * Each diagnostic is rendered in this format:
     *
     *   error[E001] at physics.examd:5:10
     *    5 │  title: "unterminated
     *       │         ^^^^^^^^^^^^^^^^^^ Unterminated quoted string
     *   hint: Close the string with a double-quote on the same line
     *
     * @param sourceLines The source file split into lines (1-indexed: use index lineNum-1).
     *                    Pass null or empty to skip the source excerpt — only the header
     *                    and message will be rendered.
     * @return A formatted multi-line string. Empty string if no diagnostics.
     */
    public String render(String[] sourceLines) {
        if (diagnostics.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            renderOne(d, sourceLines, sb);
            sb.append('\n');
        }

        // Summary line: "3 error(s), 1 warning(s)"
        long warnings = diagnostics.stream()
            .filter(d -> d.severity == DiagnosticSeverity.WARNING).count();
        if (errorCount > 0 || warnings > 0) {
            sb.append(errorCount).append(" error(s)");
            if (warnings > 0) sb.append(", ").append(warnings).append(" warning(s)");
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Renders a single diagnostic into the StringBuilder.
     *
     * Example output (for a lexer error on line 5, col 10):
     *
     *   error[E001] at physics.examd:5:10 — Unterminated quoted string
     *    5 │  title: "unterminated string here
     *       │         ^^^^^^^^^^^^^^^^^^^^^^^^^
     *   hint: Close the string with a double-quote on the same line
     */
    private static void renderOne(Diagnostic d, String[] sourceLines, StringBuilder sb) {
        // ── Header: severity[code] at location — message ──────────────────
        sb.append(d.severity.label())
          .append('[').append(d.code).append(']')
          .append(" at ").append(d.span)
          .append(" — ").append(d.message)
          .append('\n');

        // ── Source excerpt ─────────────────────────────────────────────────
        if (sourceLines != null && d.span.lineStart >= 1
                && d.span.lineStart <= sourceLines.length) {

            String sourceLine = sourceLines[d.span.lineStart - 1]; // 0-indexed
            String lineNumStr = String.valueOf(d.span.lineStart);
            String padding = " ".repeat(lineNumStr.length());

            // " 5 │  title: "unterminated..."
            sb.append(' ').append(lineNumStr).append(" │  ").append(sourceLine).append('\n');

            // "   │  ^^^^^^^^^^^^^^^^^^^^^^^^^"
            // The carets start at colStart and extend to colEnd (or end of line)
            int caretStart = Math.max(0, d.span.colStart - 1);
            int caretEnd   = (d.span.lineStart == d.span.lineEnd)
                             ? Math.min(d.span.colEnd, sourceLine.length())
                             : sourceLine.length();
            int caretLen   = Math.max(1, caretEnd - caretStart);

            sb.append(' ').append(padding).append(" │  ")
              .append(" ".repeat(caretStart))
              .append("^".repeat(caretLen))
              .append('\n');
        }

        // ── Suggestion ────────────────────────────────────────────────────
        if (d.hasSuggestion()) {
            sb.append("hint: ").append(d.suggestion).append('\n');
        }
    }

    /**
     * Renders all diagnostics using only the one-line toString format.
     * Useful for unit tests and log output.
     */
    public String renderCompact() {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            sb.append(d).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "DiagnosticCollector[errors=" + errorCount
               + ", total=" + diagnostics.size() + "]";
    }
}