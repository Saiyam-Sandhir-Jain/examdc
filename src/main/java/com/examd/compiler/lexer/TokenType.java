package com.examd.compiler.lexer;

/**
 * TokenType is the vocabulary of the EXAMD language.
 *
 * Before the parser can make sense of structure, it needs the raw text
 * broken into labelled chunks. That's what this enum does — it gives
 * every piece of source text a name so the rest of the pipeline never
 * has to look at raw characters again.
 *
 * It's the same idea as classifying words in a sentence:
 *   "Newton"  → NOUN
 *   "runs"    → VERB
 *   "fast"    → ADJECTIVE
 *
 * For EXAMD:
 *   "[EXAM]"  → BLOCK_HEADER
 *   "title:"  → KEY
 *   "Physics" → VALUE_SCALAR
 *
 * I used an enum instead of String constants because Java will warn me
 * if I add a new value here and forget to handle it in a switch statement
 * somewhere in the parser. That's a free safety check I'd lose with strings.
 *
 * One deliberate decision: there's no TITLE_KEY or MARKS_KEY — just KEY.
 * The lexer doesn't know what a key means, only that it looks like one.
 * Whether "title:" is valid inside a [STEM] block is the validator's job,
 * not the lexer's. Keeping these coarse-grained makes the lexer simpler
 * and easier to change later.
 */
public enum TokenType {

    // structural tokens — the main building blocks of any .examd file

    /**
     * A line wrapped in square brackets — marks the start of a new block.
     *
     * Examples in source:
     *   [EXAM]
     *   [SECTION: A]
     *   [Q: 1]
     *   [STEM]
     *
     * The lexeme stores everything inside the brackets: "SECTION: A"
     * The parser pulls out the name and identifier from there.
     */
    BLOCK_HEADER,

    /**
     * A word followed by a colon — the name side of a key-value pair.
     *
     * Examples:
     *   title:
     *   total_marks:
     *   negative-marking:
     *
     * I strip the colon before storing the lexeme, so "title:" becomes "title".
     * Whatever follows on the same line becomes either a VALUE_SCALAR or VALUE_PIPE.
     */
    KEY,

    /**
     * The value on the same line as a key, after the colon.
     *
     *   title: Physics Test   →  lexeme = "Physics Test"
     *   marks: 4              →  lexeme = "4"
     *
     * If the key line ends with nothing after the colon, the lexeme is
     * an empty string — that signals a list is coming on the next lines.
     * Quoted values get their surrounding quotes stripped here.
     */
    VALUE_SCALAR,

    /**
     * The | character that tells the lexer a multi-line value is coming.
     *
     *   content: |
     *     Line one.
     *     Line two.
     *
     * Seeing this switches the lexer into IN_PIPE state. Every indented
     * line after it becomes an INDENT_LINE until the indentation drops back.
     * I borrowed this syntax from YAML — it felt familiar enough that
     * people writing .examd files wouldn't need to look it up.
     */
    VALUE_PIPE,

    /**
     * One indented line inside a pipe block.
     *
     *   content: |
     *     Solve for x:          ←  INDENT_LINE, lexeme = "Solve for x:"
     *     $$ x^2 + 2x = 0 $$    ←  INDENT_LINE, lexeme = "$$ x^2 + 2x = 0 $$"
     *
     * The leading indentation gets stripped before storing — the lexeme
     * is just the content, not the spaces.
     */
    INDENT_LINE,

    /**
     * A line starting with "- " — one item in a list.
     *
     * Covers both plain items and labeled MCQ options:
     *   - Photosynthesis          →  lexeme = "Photosynthesis"
     *   - A: Paris                →  lexeme = "A: Paris"
     *
     * Both look the same to the lexer. The parser figures out later
     * whether it's labeled by checking for a colon after the first word.
     * The "- " prefix gets stripped before storing.
     */
    LIST_ITEM,

    // trivial tokens — no semantic meaning, but worth keeping

    /**
     * A line starting with #.
     *
     *   # This section covers Newton's laws
     *
     * Comments don't mean anything to the compiler, but I still lex them
     * instead of ignoring them outright. Future IDE tooling (like a
     * formatter or a language server) will need to know where they are.
     * The parser filters them out before it starts reading.
     */
    COMMENT,

    /**
     * A line with nothing on it (or only whitespace).
     *
     * Blank lines are just visual breathing room in .examd files — they
     * don't affect the output at all. Same as COMMENT: I keep them for
     * tooling purposes but the parser never sees them.
     *
     * They do one useful thing in the lexer: a blank line terminates
     * an open pipe block or list block and resets state to NORMAL.
     */
    BLANK,

    // control

    /**
     * Marks the end of the token stream.
     *
     * Every stream ends with exactly one EOF. The parser uses it to know
     * when to stop rather than checking for an empty list everywhere.
     * Lexeme is always "", span points to the line after the last line.
     */
    EOF
}