package com.examd.compiler.lexer;

import com.examd.compiler.diagnostics.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexer — the first real stage of the EXAMD compiler.
 *
 * This class takes raw `.examd` source text and converts it into
 * structured tokens that the parser can understand.
 *
 * The parser never works directly with characters.
 * It only sees tokens produced here.
 *
 * Example:
 *
 *   Raw source:
 *     title: Physics Test
 *
 *   Turns into:
 *     KEY("title")
 *     VALUE_SCALAR("Physics Test")
 *
 * The lexer does NOT care whether:
 *   - the title is semantically valid
 *   - sections are in the correct order
 *   - required fields exist
 *
 * That comes later during parsing + validation.
 *
 * The lexer only answers one question:
 *
 *   "What kind of thing is this text?"
 *
 * ───────────────────────────────────────────────────────────────
 * WHY THIS LEXER IS LINE-BASED
 * ───────────────────────────────────────────────────────────────
 *
 * Most programming languages need character-by-character lexers because
 * syntax can become deeply nested and tokens can span many lines.
 *
 * EXAMD was intentionally designed to avoid that complexity.
 *
 * Almost every construct begins at the start of a line:
 *
 *   [SECTION: A]
 *   title: Physics
 *   - Option A
 *
 * That means we can process the file one line at a time instead of
 * building a huge streaming lexer with dozens of states.
 *
 * The result is:
 *   - simpler code
 *   - easier debugging
 *   - cleaner parser logic
 *   - easier TeaVM/WebAssembly portability
 *
 * Pipe blocks (`content: |`) are the only true multiline construct,
 * so the lexer only needs minimal state tracking.
 *
 * ───────────────────────────────────────────────────────────────
 * THE THREE STATES
 * ───────────────────────────────────────────────────────────────
 *
 * NORMAL
 *   Default mode.
 *   We're reading regular EXAMD syntax.
 *
 * IN_PIPE
 *   We're inside a multiline pipe block:
 *
 *     content: |
 *       line 1
 *       line 2
 *
 *   Every indented line becomes an INDENT_LINE token until indentation drops.
 *
 * IN_LIST
 *   We're inside a YAML-style list:
 *
 *     options:
 *       - A
 *       - B
 *
 *   Each `- item` becomes a LIST_ITEM token.
 *
 * The lexer constantly switches between these states while scanning lines.
 *
 * ───────────────────────────────────────────────────────────────
 * TEAVM NOTE
 * ───────────────────────────────────────────────────────────────
 *
 * TeaVM supports java.util.regex.* but not java.nio.file.*.
 *
 * That's why this lexer accepts the source as a String instead of
 * reading files directly.
 *
 * File I/O belongs outside the compiler core.
 */
public final class Lexer {

    // ───────────────────────────────────────────────────────────
    // Lexer State Machine
    // ───────────────────────────────────────────────────────────

    /**
     * Represents the current lexer mode.
     *
     * Most of the time we're in NORMAL mode.
     *
     * The only times we leave NORMAL are:
     *   - while reading pipe blocks
     *   - while reading list blocks
     *
     * The parser never sees these states.
     * They're purely internal lexer context.
     */
    private enum State {
        NORMAL,
        IN_PIPE,
        IN_LIST
    }

    // ───────────────────────────────────────────────────────────
    // Regex Patterns
    // ───────────────────────────────────────────────────────────

    /**
     * Detects block headers:
     *
     *   [EXAM]
     *   [SECTION: A]
     *   [QUESTION: q1]
     *
     * Group 1 contains everything inside the brackets.
     *
     * I precompile patterns because Pattern.compile() is relatively expensive.
     * Doing it once up-front is much cheaper than recompiling on every line.
     */
    private static final Pattern BLOCK_HEADER_PATTERN =
        Pattern.compile(
            "^\\[([A-Z][A-Z0-9_]*(?:\\s*:\\s*[\\w][\\w\\-]*)?)\\]\\s*$",
            Pattern.CASE_INSENSITIVE
        );

    /**
     * Detects key-value lines:
     *
     *   title: Physics
     *   marks: 4
     *   content: |
     *
     * Group 1 → key
     * Group 2 → value
     */
    private static final Pattern KEY_PATTERN =
        Pattern.compile("^([\\w][\\w_-]*):\\s*(.*)$");

    /**
     * Detects YAML-style list items:
     *
     *   - Option A
     *   - Paris
     *   - correct: true
     *
     * Group 1 contains the content after "- ".
     */
    private static final Pattern LIST_ITEM_PATTERN =
        Pattern.compile("^\\s*-\\s+(.+)$");

    /**
     * Detects comment lines.
     *
     * Comments can appear almost anywhere,
     * so they get checked very early during lexing.
     */
    private static final Pattern COMMENT_PATTERN =
        Pattern.compile("^\\s*#(.*)$");

    /**
     * Detects empty / whitespace-only lines.
     *
     * Blank lines matter more than they seem.
     *
     * They:
     *   - terminate pipe blocks
     *   - terminate lists
     *   - preserve structure for the parser
     */
    private static final Pattern BLANK_PATTERN =
        Pattern.compile("^\\s*$");

    // ───────────────────────────────────────────────────────────
    // Instance State
    // ───────────────────────────────────────────────────────────

    /**
     * Source filename used inside Span objects.
     *
     * Examples:
     *   "physics.examd"
     *   "<stdin>"
     */
    private final String filename;

    /**
     * Entire source file split into individual lines.
     *
     * The lexer walks this array sequentially.
     */
    private final String[] lines;

    /**
     * Tokens produced so far.
     *
     * This eventually becomes the parser's input stream.
     */
    private final List<Token> tokens = new ArrayList<>();

    /**
     * Current lexer mode.
     */
    private State state = State.NORMAL;

    /**
     * Base indentation level for pipe blocks.
     *
     * Example:
     *
     *   content: |
     *       hello
     *       world
     *
     * Base indent = 4
     */
    private int pipeBaseIndent = 0;

    /**
     * True immediately after encountering VALUE_PIPE.
     *
     * The first actual content line determines the indentation level
     * for the entire pipe block.
     */
    private boolean awaitingPipeFirstLine = false;

    // ───────────────────────────────────────────────────────────
    // Constructor
    // ───────────────────────────────────────────────────────────

    /**
     * Creates a lexer instance for a source file.
     *
     * I normalize line endings here because Windows/Linux/macOS all
     * handle newlines differently:
     *
     *   Windows → \r\n
     *   Linux   → \n
     *   Old Mac → \r
     *
     * Internally the compiler only uses \n.
     */
    public Lexer(String filename, String source) {
        this.filename = filename;

        this.lines = source
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n", -1);
    }

    // ───────────────────────────────────────────────────────────
    // Public API
    // ───────────────────────────────────────────────────────────

    /**
     * Main lexer entry point.
     *
     * This walks through the source file line-by-line and feeds each
     * line into processLine().
     *
     * At the very end we always append an EOF token.
     *
     * Why?
     *
     * Because parsers become dramatically simpler when they can always
     * safely expect a guaranteed "end" token instead of constantly
     * checking array bounds.
     */
    public List<Token> tokenize() {

        for (int i = 0; i < lines.length; i++) {
            processLine(i + 1, lines[i]);
        }

        // If the file ended while inside a list/pipe block,
        // that's perfectly valid — we just close the state.
        if (state == State.IN_PIPE || state == State.IN_LIST) {
            state = State.NORMAL;
        }

        tokens.add(Token.eof(filename, lines.length + 1));

        return tokens;
    }

    // ───────────────────────────────────────────────────────────
    // Core Line Processor
    // ───────────────────────────────────────────────────────────

    /**
     * The heart of the lexer.
     *
     * Every line in the source file eventually passes through here.
     *
     * The order of checks is EXTREMELY important.
     *
     * Example:
     *
     *   # hello
     *
     * technically also contains characters matching other patterns,
     * but comments should always win first.
     *
     * Likewise blank lines must be handled before state logic because
     * blank lines terminate lists + pipe blocks.
     *
     * General flow:
     *
     *   comment?
     *   blank?
     *   state-specific handling?
     *   block header?
     *   list item?
     *   key/value?
     *   otherwise → error
     *
     * Lexer bugs often come from incorrect ordering,
     * which is why this structure is intentionally explicit.
     */
    private void processLine(int lineNum, String rawLine) {

        // ───────────────────────────────────────────────────────
        // 1. Comments
        // ───────────────────────────────────────────────────────

        if (COMMENT_PATTERN.matcher(rawLine).matches()) {

            Span span = Span.line(filename, lineNum, rawLine.length());

            tokens.add(
                new Token(TokenType.COMMENT, rawLine.trim(), span)
            );

            return;
        }

        // ───────────────────────────────────────────────────────
        // 2. Blank Lines
        // ───────────────────────────────────────────────────────

        if (BLANK_PATTERN.matcher(rawLine).matches()) {

            Span span = Span.point(filename, lineNum, 1);

            tokens.add(
                new Token(TokenType.BLANK, "", span)
            );

            // Blank lines terminate open blocks.
            if (state == State.IN_PIPE || state == State.IN_LIST) {
                state = State.NORMAL;
            }

            return;
        }

        // ───────────────────────────────────────────────────────
        // 3A. IN_PIPE Handling
        // ───────────────────────────────────────────────────────

        if (state == State.IN_PIPE) {

            int leadingSpaces = countLeadingSpaces(rawLine);

            if (awaitingPipeFirstLine) {

                // First line determines base indentation.
                if (leadingSpaces == 0) {

                    // No indentation means the pipe block is effectively empty.
                    state = State.NORMAL;
                    awaitingPipeFirstLine = false;

                } else {

                    pipeBaseIndent = leadingSpaces;
                    awaitingPipeFirstLine = false;

                    String content =
                        rawLine.substring(pipeBaseIndent);

                    Span span =
                        Span.line(filename, lineNum, rawLine.length());

                    tokens.add(
                        new Token(TokenType.INDENT_LINE, content, span)
                    );

                    return;
                }

            } else if (leadingSpaces >= pipeBaseIndent) {

                String content =
                    rawLine.substring(pipeBaseIndent);

                Span span =
                    Span.line(filename, lineNum, rawLine.length());

                tokens.add(
                    new Token(TokenType.INDENT_LINE, content, span)
                );

                return;

            } else {

                // Indentation dropped → pipe block ended.
                state = State.NORMAL;
            }
        }

        // ───────────────────────────────────────────────────────
        // 3B. IN_LIST Handling
        // ───────────────────────────────────────────────────────

        if (state == State.IN_LIST) {

            Matcher listMatcher =
                LIST_ITEM_PATTERN.matcher(rawLine);

            if (listMatcher.matches()) {

                String content =
                    listMatcher.group(1).trim();

                Span span =
                    Span.line(filename, lineNum, rawLine.length());

                tokens.add(
                    new Token(TokenType.LIST_ITEM, content, span)
                );

                return;

            } else {

                // Any non-list line exits list mode.
                state = State.NORMAL;
            }
        }

        // ───────────────────────────────────────────────────────
        // 4. Block Headers
        // ───────────────────────────────────────────────────────

        Matcher headerMatcher =
            BLOCK_HEADER_PATTERN.matcher(rawLine.trim());

        if (headerMatcher.matches()) {

            state = State.NORMAL;

            String headerContent =
                headerMatcher.group(1).trim();

            Span span =
                Span.line(filename, lineNum, rawLine.length());

            tokens.add(
                new Token(TokenType.BLOCK_HEADER, headerContent, span)
            );

            return;
        }

        // ───────────────────────────────────────────────────────
        // 5. List Items
        // ───────────────────────────────────────────────────────

        Matcher listMatcher =
            LIST_ITEM_PATTERN.matcher(rawLine);

        if (listMatcher.matches()) {

            String content =
                listMatcher.group(1).trim();

            Span span =
                Span.line(filename, lineNum, rawLine.length());

            tokens.add(
                new Token(TokenType.LIST_ITEM, content, span)
            );

            state = State.IN_LIST;

            return;
        }

        // ───────────────────────────────────────────────────────
        // 6. Key / Value Pairs
        // ───────────────────────────────────────────────────────

        Matcher keyMatcher =
            KEY_PATTERN.matcher(rawLine);

        if (keyMatcher.matches()) {

            String keyName =
                keyMatcher.group(1);

            String rawValue =
                keyMatcher.group(2).trim();

            Span keySpan =
                new Span(
                    filename,
                    lineNum,
                    1,
                    lineNum,
                    keyName.length()
                );

            tokens.add(
                new Token(TokenType.KEY, keyName, keySpan)
            );

            // ── Pipe Scalar ────────────────────────────────────

            if (rawValue.equals("|")) {

                Span pipeSpan =
                    new Span(
                        filename,
                        lineNum,
                        keyName.length() + 2,
                        lineNum,
                        keyName.length() + 3
                    );

                tokens.add(
                    new Token(TokenType.VALUE_PIPE, "|", pipeSpan)
                );

                awaitingPipeFirstLine = true;
                pipeBaseIndent = 0;
                state = State.IN_PIPE;
            }

            // ── Empty Scalar ───────────────────────────────────

            else if (rawValue.isEmpty()) {

                int valueCol = keyName.length() + 2;

                Span valueSpan =
                    Span.point(filename, lineNum, valueCol);

                tokens.add(
                    new Token(TokenType.VALUE_SCALAR, "", valueSpan)
                );

                // Often followed by a list block.
                state = State.IN_LIST;
            }

            // ── Normal Scalar ──────────────────────────────────

            else {

                int valueCol =
                    rawLine.indexOf(
                        rawValue.charAt(0),
                        keyName.length() + 1
                    );

                Span valueSpan =
                    new Span(
                        filename,
                        lineNum,
                        valueCol + 1,
                        lineNum,
                        rawLine.length()
                    );

                String unquotedValue =
                    stripQuotes(rawValue, lineNum, valueCol);

                tokens.add(
                    new Token(
                        TokenType.VALUE_SCALAR,
                        unquotedValue,
                        valueSpan
                    )
                );

                state = State.NORMAL;
            }

            return;
        }

        // ───────────────────────────────────────────────────────
        // 7. Unknown Content
        // ───────────────────────────────────────────────────────

        /**
         * Eventually this will become a recoverable diagnostic system.
         *
         * For now we throw immediately because during compiler development
         * it's usually better to fail fast and see the exact bad input.
         */
        Span span =
            Span.line(filename, lineNum, rawLine.length());

        throw new LexerException(
            "E003",
            "Unexpected content: '" + rawLine.trim() + "'",
            span
        );
    }

    // ───────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────

    /**
     * Counts leading spaces at the beginning of a line.
     *
     * I intentionally only count literal spaces here.
     *
     * Mixing tabs/spaces in indentation-sensitive syntax becomes painful
     * very quickly, so EXAMD strongly prefers spaces.
     */
    private static int countLeadingSpaces(String line) {

        int count = 0;

        while (
            count < line.length() &&
            line.charAt(count) == ' '
        ) {
            count++;
        }

        return count;
    }

    /**
     * Removes surrounding quotes from scalar values.
     *
     * Examples:
     *
     *   "hello"   → hello
     *   'hello'   → hello
     *   physics   → physics
     *
     * If a quoted string is missing its closing quote,
     * we throw E001 immediately.
     *
     * Only minimal escape handling happens here.
     * More advanced semantic processing belongs later in the compiler.
     */
    private String stripQuotes(
        String raw,
        int lineNum,
        int col
    ) {

        if (raw.isEmpty()) {
            return raw;
        }

        char first = raw.charAt(0);

        if (first == '"' || first == '\'') {

            if (
                raw.length() < 2 ||
                raw.charAt(raw.length() - 1) != first
            ) {

                Span errSpan =
                    new Span(
                        filename,
                        lineNum,
                        col + 1,
                        lineNum,
                        col + raw.length()
                    );

                throw new LexerException(
                    "E001",
                    "Unterminated quoted string: " + raw,
                    errSpan
                );
            }

            String inner =
                raw.substring(1, raw.length() - 1);

            // Minimal escape handling for double quotes.
            if (first == '"') {

                inner = inner
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            }

            return inner;
        }

        return raw;
    }

    // ───────────────────────────────────────────────────────────
    // Debug Utilities
    // ───────────────────────────────────────────────────────────

    /**
     * Pretty-prints the token stream for debugging.
     *
     * During parser development I ended up using this constantly because
     * raw Token objects are annoying to visually scan.
     *
     * Example:
     *
     *   Line  1 │ BLOCK_HEADER    "EXAM"
     *   Line  2 │ KEY             "title"
     *   Line  2 │ VALUE_SCALAR    "Physics Test"
     */
    public static String dumpTokens(List<Token> tokens) {

        StringBuilder sb = new StringBuilder();

        for (Token t : tokens) {

            String lineNum =
                String.format("%4d", t.span.lineStart);

            String typeStr =
                String.format("%-16s", t.type.name());

            String lexeme =
                t.lexeme.length() > 50
                    ? t.lexeme.substring(0, 47) + "…"
                    : t.lexeme;

            sb.append("Line ")
              .append(lineNum)
              .append(" │ ")
              .append(typeStr)
              .append(" \"")
              .append(lexeme)
              .append("\"\n");
        }

        return sb.toString();
    }
}