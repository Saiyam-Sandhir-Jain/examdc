package com.examd.compiler.diagnostics;

/**
 * Span tracks exactly where in a .examd file something lives.
 *
 * I added this because I got tired of error messages that just say
 * "something went wrong" with no location. Every token, AST node, and
 * error in this compiler carries a Span so we always know the file,
 * line, and column — enough to draw the caret under the bad text:
 *
 *   Error [E004] at physics_test.examd:47:1
 *   47 │  [SETCION: A]
 *      │  ^^^^^^^^^^^^ Unknown block header. Did you mean [SECTION]?
 *
 * I store both start and end (not just a line number) because you need
 * the full range to underline a multi-character token. A line number
 * alone gets you to the right row — the column gets you to the exact
 * character.
 *
 * Spans are immutable. Once the lexer stamps a location onto a token,
 * nothing should change it. If you need a span that covers two tokens,
 * use Span.merge() — it creates a new one rather than modifying either.
 */
public final class Span {

    /** Source filename, e.g. "physics.examd" or "<stdin>" */
    public final String file;

    /** Line where this span starts. 1-indexed — same as what editors show. */
    public final int lineStart;

    /** Column where this span starts. 1-indexed. */
    public final int colStart;

    /** Line where this span ends (inclusive). */
    public final int lineEnd;

    /** Column where this span ends (inclusive). */
    public final int colEnd;

    // Main constructor — just stores the five values, nothing else.
    public Span(String file, int lineStart, int colStart, int lineEnd, int colEnd) {
        this.file      = file;
        this.lineStart = lineStart;
        this.colStart  = colStart;
        this.lineEnd   = lineEnd;
        this.colEnd    = colEnd;
    }

    /**
     * For when you're pointing at exactly one character.
     * Saves typing new Span(f, line, col, line, col) everywhere.
     */
    public static Span point(String file, int line, int col) {
        return new Span(file, line, col, line, col);
    }

    /**
     * For when a token occupies a whole line (which is most of EXAMD).
     * The lexer calls this constantly.
     */
    public static Span line(String file, int lineNum, int lineLength) {
        return new Span(file, lineNum, 1, lineNum, lineLength);
    }

    /**
     * Merges two spans into the smallest span covering both.
     * The parser uses this when building AST nodes — the node's location
     * should stretch from its first token to its last.
     */
    public static Span merge(Span a, Span b) {
        int ls = Math.min(a.lineStart, b.lineStart);
        int cs = (ls == a.lineStart) ? a.colStart : b.colStart;
        int le = Math.max(a.lineEnd, b.lineEnd);
        int ce = (le == a.lineEnd) ? a.colEnd : b.colEnd;
        return new Span(a.file, ls, cs, le, ce);
    }

    /**
     * Short location string — "physics.examd:47:3".
     * This is the format terminals and IDEs understand, so I use it
     * in all error messages.
     */
    @Override
    public String toString() {
        return file + ":" + lineStart + ":" + colStart;
    }

    /**
     * Full range — "physics.examd:47:3–47:14".
     * More verbose than toString(), useful when debugging and I want
     * to see exactly how wide a span is.
     */
    public String toRangeString() {
        if (lineStart == lineEnd) {
            return file + ":" + lineStart + ":" + colStart + "–" + colEnd;
        }
        return file + ":" + lineStart + ":" + colStart
               + "–" + lineEnd + ":" + colEnd;
    }
}