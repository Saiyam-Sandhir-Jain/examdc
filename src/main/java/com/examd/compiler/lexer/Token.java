package com.examd.compiler.lexer;

import com.examd.compiler.diagnostics.Span;

/**
 * Token is the basic unit the lexer produces.
 *
 * Every piece of source text the lexer reads gets turned into a Token.
 * A Token carries three things:
 *
 *   type   → what kind of thing it is (KEY, BLOCK_HEADER, VALUE_SCALAR…)
 *   lexeme → the actual text from the source file
 *   span   → where in the file it came from
 *
 * I never throw away the original text or location because both are
 * needed later — the lexeme for error messages, the span for pointing
 * at the exact line and column where something went wrong.
 *
 * Once the lexer creates a token, nothing should change it. Immutability
 * means I can pass the same token list to multiple pipeline stages without
 * worrying that one stage silently modified something another stage depends on.
 */
public final class Token {

    /** What kind of token this is. */
    public final TokenType type;

    /**
     * The text from the source file this token represents.
     * Some cleanup happens before storing — colons stripped from keys,
     * quotes stripped from quoted strings, "- " stripped from list items.
     * Everything else is stored as-is.
     *
     *   BLOCK_HEADER  →  "[SECTION: A]"
     *   KEY           →  "total_marks"       colon removed
     *   VALUE_SCALAR  →  "Physics Test"      quotes removed if present
     *   VALUE_PIPE    →  "|"
     *   INDENT_LINE   →  "line content"      leading indent stripped
     *   LIST_ITEM     →  "A: Paris"          leading "- " stripped
     *   COMMENT       →  "# a comment"
     *   BLANK         →  ""
     *   EOF           →  ""
     */
    public final String lexeme;

    /** Where this token is in the source file. Never null. */
    public final Span span;

    public Token(TokenType type, String lexeme, Span span) {
        if (type == null)   throw new IllegalArgumentException("Token type cannot be null");
        if (lexeme == null) throw new IllegalArgumentException("Token lexeme cannot be null");
        if (span == null)   throw new IllegalArgumentException("Token span cannot be null");
        this.type   = type;
        this.lexeme = lexeme;
        this.span   = span;
    }

    /** Builds the EOF token that ends every token stream. */
    public static Token eof(String file, int line) {
        return new Token(TokenType.EOF, "", Span.point(file, line, 1));
    }

    /** Shorthand for checking token type — reads cleaner in the parser. */
    public boolean is(TokenType t) {
        return this.type == t;
    }

    /**
     * Case-insensitive lexeme check. Useful for block header names:
     * token.hasLexeme("EXAM") matches whether the source wrote [EXAM] or [exam].
     */
    public boolean hasLexeme(String s) {
        return lexeme.equalsIgnoreCase(s);
    }

    /**
     * Debug output — prints the token in a readable format.
     * I truncate long lexemes at 40 chars so the output doesn't get
     * unreadable when printing a full token stream.
     *
     *   [KEY "total_marks" @ exam.examd:5:1]
     *   [VALUE_SCALAR "Physics Test" @ exam.examd:2:8]
     */
    @Override
    public String toString() {
        String lex = lexeme.length() > 40
                     ? lexeme.substring(0, 37) + "…"
                     : lexeme;
        return "[" + type + " \"" + lex + "\" @ " + span + "]";
    }
}