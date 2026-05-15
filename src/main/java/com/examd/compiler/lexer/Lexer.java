package com.examd.compiler.lexer;

import com.examd.compiler.diagnostics.Diagnostic;
import com.examd.compiler.diagnostics.DiagnosticCollector;
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
 * ERROR RECOVERY
 * ───────────────────────────────────────────────────────────────
 *
 * The lexer never throws. When it finds something wrong, it records
 * the problem into a DiagnosticCollector and keeps going.
 *
 * Why? Because an author with five bad lines in their file should see
 * all five errors at once, not just the first one. Throwing on the
 * first error forces a fix-recompile-fix-recompile cycle that gets
 * tedious fast.
 *
 * Recovery strategy per error type:
 *
 *   E001 (unterminated string)
 *     Treat everything after the opening quote as the value and continue.
 *     The token still gets emitted — it just has raw content instead of
 *     cleaned content. The author gets the error message without losing
 *     the rest of the file.
 *
 *   E003 (unrecognised line)
 *     Skip the line entirely, emit a diagnostic, continue with the next.
 *     The parser may see cascade errors from the missing token, but those
 *     are easier to interpret than a hard crash.
 *
 * After tokenize() returns, call getDiagnostics().hasErrors() to find
 * out if the token stream is clean enough for the parser to use.
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
     * Group 2 → value (may be empty, a scalar, or "|")
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
     * Accumulates every error and warning found during lexing.
     *
     * Never null — if the caller doesn't supply one, the constructor
     * creates a fresh internal collector. Call getDiagnostics() after
     * tokenize() to inspect what was found.
     */
    private final DiagnosticCollector diagnostics;

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
     * for the entire pipe block. We can't know what "indented enough"
     * means until we see that first line.
     */
    private boolean awaitingPipeFirstLine = false;

    // ───────────────────────────────────────────────────────────
    // Constructors
    // ───────────────────────────────────────────────────────────

    /**
     * Creates a lexer that feeds errors into an existing collector.
     *
     * Use this when you want multiple compiler phases to share the
     * same collector — so all errors across all phases appear together.
     *
     * If diagnostics is null, a fresh internal collector is created.
     * Lexing still works, you just can't see the errors from outside.
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
    public Lexer(String filename, String source, DiagnosticCollector diagnostics) {
        this.filename    = filename;
        this.diagnostics = (diagnostics != null) ? diagnostics : new DiagnosticCollector();
        this.lines = source
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n", -1);
    }

    /**
     * Convenience constructor — creates its own internal collector.
     *
     * Good for tests and one-off usages where you just want the token
     * list. Call getDiagnostics() afterwards to check for errors.
     */
    public Lexer(String filename, String source) {
        this(filename, source, new DiagnosticCollector());
    }

    // ───────────────────────────────────────────────────────────
    // Public API
    // ───────────────────────────────────────────────────────────

    /**
     * Returns the diagnostic collector for this lexer.
     *
     * Always non-null. The right pattern after calling tokenize() is:
     *
     *   List<Token> tokens = lexer.tokenize();
     *   if (lexer.getDiagnostics().hasErrors()) {
     *       // show errors, skip parser
     *   }
     */
    public DiagnosticCollector getDiagnostics() {
        return diagnostics;
    }

    /**
     * Main lexer entry point.
     *
     * Walks through the source file line-by-line and feeds each line
     * into processLine(). Never throws — all errors go into the
     * DiagnosticCollector.
     *
     * At the very end we always append an EOF token.
     *
     * Why?
     *
     * Because parsers become dramatically simpler when they can always
     * safely expect a guaranteed "end" token instead of constantly
     * checking array bounds.
     *
     * If too many errors accumulated mid-file, we stop early. The
     * remaining output would just be noise built on broken foundations.
     */
    public List<Token> tokenize() {

        for (int i = 0; i < lines.length; i++) {

            if (diagnostics.hasErrors() &&
                diagnostics.errorCount() >= DiagnosticCollector.MAX_ERRORS) {
                break;
            }

            processLine(i + 1, lines[i]);
        }

        // If the file ended while inside a list or pipe block,
        // that's fine — we just close the state cleanly.
        state = State.NORMAL;

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
     * blank lines terminate lists and pipe blocks.
     *
     * General flow:
     *
     *   comment?
     *   blank?
     *   state-specific handling?
     *   block header?
     *   list item?
     *   key/value?
     *   otherwise → record error, skip line, keep going
     *
     * Lexer bugs often come from incorrect ordering,
     * which is why this structure is intentionally explicit.
     */
    private void processLine(int lineNum, String rawLine) {

        // ───────────────────────────────────────────────────────
        // 1. Comments
        // ───────────────────────────────────────────────────────

        if (COMMENT_PATTERN.matcher(rawLine).matches()) {

            emit(TokenType.COMMENT, rawLine.trim(),
                 Span.line(filename, lineNum, rawLine.length()));

            return;
        }

        // ───────────────────────────────────────────────────────
        // 2. Blank Lines
        // ───────────────────────────────────────────────────────

        if (BLANK_PATTERN.matcher(rawLine).matches()) {

            emit(TokenType.BLANK, "",
                 Span.point(filename, lineNum, 1));

            // Blank lines close whatever block was open.
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

                if (leadingSpaces == 0) {

                    // No indentation on the first line after | means
                    // the pipe block is empty. Exit the state and fall
                    // through so this line gets processed normally.
                    state = State.NORMAL;
                    awaitingPipeFirstLine = false;

                } else {

                    // First indented line — lock in the base indent.
                    pipeBaseIndent = leadingSpaces;
                    awaitingPipeFirstLine = false;

                    emit(TokenType.INDENT_LINE,
                         rawLine.substring(pipeBaseIndent),
                         Span.line(filename, lineNum, rawLine.length()));

                    return;
                }

            } else if (leadingSpaces >= pipeBaseIndent) {

                // Still inside the pipe block — indentation holds.
                emit(TokenType.INDENT_LINE,
                     rawLine.substring(pipeBaseIndent),
                     Span.line(filename, lineNum, rawLine.length()));

                return;

            } else {

                // Indentation dropped — pipe block is over.
                // Fall through and reprocess this line normally.
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

                emit(TokenType.LIST_ITEM,
                     listMatcher.group(1).trim(),
                     Span.line(filename, lineNum, rawLine.length()));

                return;

            } else {

                // Any non-list line exits list mode.
                // Fall through and reprocess this line normally.
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

            emit(TokenType.BLOCK_HEADER,
                 headerMatcher.group(1).trim(),
                 Span.line(filename, lineNum, rawLine.length()));

            return;
        }

        // ───────────────────────────────────────────────────────
        // 5. List Items
        // ───────────────────────────────────────────────────────

        Matcher listMatcher =
            LIST_ITEM_PATTERN.matcher(rawLine);

        if (listMatcher.matches()) {

            emit(TokenType.LIST_ITEM,
                 listMatcher.group(1).trim(),
                 Span.line(filename, lineNum, rawLine.length()));

            state = State.IN_LIST;

            return;
        }

        // ───────────────────────────────────────────────────────
        // 6. Key / Value Pairs
        // ───────────────────────────────────────────────────────

        Matcher keyMatcher =
            KEY_PATTERN.matcher(rawLine);

        if (keyMatcher.matches()) {

            String keyName  = keyMatcher.group(1);
            String rawValue = keyMatcher.group(2).trim();

            Span keySpan = new Span(
                filename, lineNum, 1, lineNum, keyName.length()
            );

            emit(TokenType.KEY, keyName, keySpan);

            // ── Pipe Scalar ────────────────────────────────────

            if (rawValue.equals("|")) {

                int pipeCol = rawLine.indexOf('|', keyName.length());

                Span pipeSpan =
                    Span.point(filename, lineNum, pipeCol + 1);

                emit(TokenType.VALUE_PIPE, "|", pipeSpan);

                awaitingPipeFirstLine = true;
                pipeBaseIndent = 0;
                state = State.IN_PIPE;
            }

            // ── Empty Scalar ───────────────────────────────────

            else if (rawValue.isEmpty()) {

                // Empty value after a key usually means a list follows.
                Span valueSpan =
                    Span.point(filename, lineNum, keyName.length() + 2);

                emit(TokenType.VALUE_SCALAR, "", valueSpan);

                state = State.IN_LIST;
            }

            // ── Normal Scalar ──────────────────────────────────

            else {

                int valueOffset =
                    rawLine.indexOf(rawValue.charAt(0), keyName.length() + 1);

                Span valueSpan = new Span(
                    filename, lineNum,
                    valueOffset + 1, lineNum, rawLine.length()
                );

                // stripQuotes records a diagnostic on bad input instead
                // of throwing, so lexing continues past this line.
                String unquoted = stripQuotes(rawValue, lineNum, valueOffset);

                emit(TokenType.VALUE_SCALAR, unquoted, valueSpan);

                state = State.NORMAL;
            }

            return;
        }

        // ───────────────────────────────────────────────────────
        // 7. Unknown Content
        // ───────────────────────────────────────────────────────

        // We record the problem and skip the line.
        //
        // The token stream won't have anything for this line, which may
        // cause the parser to see cascade errors — but that's a lot more
        // useful than a hard crash with no further information.

        Span errSpan =
            Span.line(filename, lineNum, rawLine.length());

        diagnostics.error(
            "E003",
            "Unexpected content: '" + rawLine.trim() + "'",
            errSpan,
            "Lines must be block headers ([BLOCK]), key-value pairs (key: value), or list items (- item)"
        );
    }

    // ───────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────

    /**
     * Adds a token to the list.
     *
     * A small helper so processLine() doesn't repeat
     * `tokens.add(new Token(...))` everywhere.
     */
    private void emit(TokenType type, String lexeme, Span span) {
        tokens.add(new Token(type, lexeme, span));
    }

    /**
     * Counts leading spaces at the beginning of a line.
     *
     * I intentionally only count literal spaces here.
     *
     * Mixing tabs and spaces in indentation-sensitive syntax becomes
     * painful very quickly, so EXAMD strongly prefers spaces only.
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
     * If a quoted string is missing its closing quote, we record an
     * E001 diagnostic and return whatever came after the opening quote.
     * That way the token still gets emitted with something reasonable
     * and lexing continues rather than stopping here.
     *
     * Only minimal escape handling happens here.
     * More advanced semantic processing belongs later in the pipeline.
     */
    private String stripQuotes(String raw, int lineNum, int col) {

        if (raw.isEmpty()) {
            return raw;
        }

        char first = raw.charAt(0);

        if (first == '"' || first == '\'') {

            if (raw.length() < 2 || raw.charAt(raw.length() - 1) != first) {

                Span errSpan = new Span(
                    filename,
                    lineNum, col + 1,
                    lineNum, col + raw.length()
                );

                diagnostics.error(
                    "E001",
                    "Unterminated quoted string",
                    errSpan,
                    "Close the string with " + first + " on the same line"
                );

                // Best-effort recovery: return everything after the
                // opening quote so the token has something sensible in it.
                return raw.substring(1);
            }

            String inner = raw.substring(1, raw.length() - 1);

            // Minimal escape handling for double-quoted strings.
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