package com.examd.compiler.diagnostics;

/**
 * DiagnosticSeverity — the four levels of compiler feedback.
 *
 * WHY FOUR LEVELS?
 * Not everything the compiler notices is equally serious. Consider:
 *
 *   ERROR   → "This file cannot produce valid output at all."
 *             The compiler must stop before generating anything.
 *             Example: [Q: 1] has no [EVALUATE] block — answer checking is impossible.
 *
 *   WARNING → "This file will compile, but something looks suspicious."
 *             The output will be generated, but the author should review this.
 *             Example: total_marks: 100 but the section marks only add up to 95.
 *
 *   INFO    → "Here is something the author might want to know."
 *             Purely informational — no action required.
 *             Example: "Detected 12 questions across 3 sections."
 *
 *   HINT    → "Here is a style suggestion."
 *             Non-blocking, non-critical. Used by IDE tooling.
 *             Example: "Consider adding a 'subject:' key for better organisation."
 *
 * DESIGN NOTE — Ordered by severity (ordinal matters):
 * We intentionally declare them from most to least severe so that
 *   severity.ordinal() can be used for sorting and threshold checks.
 *
 * This mirrors the conventions of:
 *   - rustc  (error / warning / note / help)
 *   - clang  (error / warning / note / remark)
 *   - javac  (error / warning / note)
 */
public enum DiagnosticSeverity {

    /**
     * Fatal — compilation cannot produce valid output.
     * Displayed with a red "error" label in terminals that support ANSI colour.
     * Causes the compiler to skip code generation even if it finishes all phases.
     */
    ERROR,

    /**
     * Non-fatal — output is produced but the author should take action.
     * Displayed with a yellow "warning" label.
     */
    WARNING,

    /**
     * Informational — no action required.
     * Displayed with a cyan "info" label. Often suppressed in non-verbose mode.
     */
    INFO,

    /**
     * Style / ergonomics suggestion.
     * Displayed with a blue "hint" label. Only shown in IDE / verbose mode.
     */
    HINT;

    /** Returns true if this severity should block code generation. */
    public boolean isBlocking() {
        return this == ERROR;
    }

    /** Returns a terminal-friendly label string for display. */
    public String label() {
        return switch (this) {
            case ERROR   -> "error";
            case WARNING -> "warning";
            case INFO    -> "info";
            case HINT    -> "hint";
        };
    }
}