package com.examd.compiler.lexer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.examd.compiler.lexer.Lexer;
import com.examd.compiler.lexer.LexerException;
import com.examd.compiler.lexer.Token;
import com.examd.compiler.lexer.TokenType;
import com.examd.compiler.diagnostics.Diagnostic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LexerTest — tests for the Lexer of the EXAMD compiler.
 *
 * This file exists for one reason:
 *
 *   "Can the lexer correctly classify source text into tokens?"
 *
 * A lexer is one of those components that feels simple until edge cases
 * start piling up:
 *
 *   - blank lines
 *   - multiline pipe blocks
 *   - quoted strings
 *   - malformed syntax
 *   - indentation issues
 *
 * Tiny lexer bugs eventually create horrifying parser bugs later,
 * so having extremely explicit lexer tests saves enormous debugging time.
 *
 * ───────────────────────────────────────────────────────────────
 * HOW THESE TESTS WORK
 * ───────────────────────────────────────────────────────────────
 *
 * Every test follows the same pattern:
 *
 *   1. Create a small source string
 *   2. Feed it into the lexer
 *   3. Assert the exact tokens produced
 *
 * Example:
 *
 *   Input:
 *     title: Physics
 *
 *   Expected:
 *     KEY("title")
 *     VALUE_SCALAR("Physics")
 *     EOF
 *
 * This style of testing is ideal for lexers because lexers are
 * deterministic:
 *
 *   same input → same token stream every time
 *
 * ───────────────────────────────────────────────────────────────
 * WHAT WE TEST HERE
 * ───────────────────────────────────────────────────────────────
 *
 * 1. Happy path
 *    Valid EXAMD syntax tokenizes correctly.
 *
 * 2. Edge cases
 *    Empty values, blank lines, quotes, multiline blocks.
 *
 * 3. Error handling
 *    Unterminated strings and malformed input throw correctly.
 *
 * 4. Span accuracy
 *    Tokens report the correct source locations.
 *
 * 5. Integration
 *    A complete minimal exam tokenizes end-to-end.
 *
 * These tests are intentionally verbose because lexer bugs are often
 * incredibly annoying to track down later.
 */
@DisplayName("Lexer — Phase 1")
class LexerTest {

    // ───────────────────────────────────────────────────────────
    // Small Test Helpers
    // ───────────────────────────────────────────────────────────

    /**
     * Tiny helper so every test doesn't need to repeatedly write:
     *
     *   new Lexer(...).tokenize()
     *
     * Using a consistent filename also keeps Span assertions predictable.
     */
    private List<Token> lex(String source) {
        return new Lexer("test.examd", source).tokenize();
    }

    /**
     * Shared assertion helper used throughout the file.
     *
     * Why use a helper instead of raw assertEquals everywhere?
     *
     * Because token mismatch errors become MUCH easier to read:
     *
     *   Expected token type KEY but got VALUE_SCALAR
     *   (lexeme: "Physics")
     *
     * instead of cryptic generic assertion failures.
     */
    private void assertToken(
        Token token,
        TokenType expectedType,
        String expectedLexeme
    ) {

        assertEquals(
            expectedType,
            token.type,
            "Expected token type " + expectedType +
            " but got " + token.type +
            " (lexeme: \"" + token.lexeme + "\")"
        );

        assertEquals(
            expectedLexeme,
            token.lexeme,
            "Expected lexeme \"" + expectedLexeme +
            "\" but got \"" + token.lexeme + "\""
        );
    }

    // ───────────────────────────────────────────────────────────
    // 1. Block Header Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Simplest possible block header.
     *
     * If THIS fails,
     * the lexer is fundamentally broken.
     */
    @Test
    @DisplayName("Simple block header [EXAM]")
    void testSimpleBlockHeader() {

        List<Token> tokens = lex("[EXAM]");

        assertToken(
            tokens.get(0),
            TokenType.BLOCK_HEADER,
            "EXAM"
        );

        assertToken(
            tokens.get(1),
            TokenType.EOF,
            ""
        );
    }

    /**
     * Tests block headers containing identifiers:
     *
     *   [SECTION: A]
     *
     * These are heavily used throughout EXAMD.
     */
    @Test
    @DisplayName("Block header with identifier [SECTION: A]")
    void testBlockHeaderWithIdentifier() {

        List<Token> tokens = lex("[SECTION: A]");

        assertToken(
            tokens.get(0),
            TokenType.BLOCK_HEADER,
            "SECTION: A"
        );
    }

    /**
     * Question blocks are one of the most common EXAMD structures,
     * so they deserve their own dedicated test.
     */
    @Test
    @DisplayName("Question block header [Q: 1]")
    void testQuestionBlockHeader() {

        List<Token> tokens = lex("[Q: 1]");

        assertToken(
            tokens.get(0),
            TokenType.BLOCK_HEADER,
            "Q: 1"
        );
    }

    /**
     * Context blocks usually appear in reading comprehension passages.
     */
    @Test
    @DisplayName("Context block header [CONTEXT: RC1]")
    void testContextBlockHeader() {

        List<Token> tokens = lex("[CONTEXT: RC1]");

        assertToken(
            tokens.get(0),
            TokenType.BLOCK_HEADER,
            "CONTEXT: RC1"
        );
    }

    /**
     * Some blocks don't need identifiers.
     *
     * Example:
     *   [STEM]
     */
    @Test
    @DisplayName("STEM block header (no identifier)")
    void testStemBlockHeader() {

        List<Token> tokens = lex("[STEM]");

        assertToken(
            tokens.get(0),
            TokenType.BLOCK_HEADER,
            "STEM"
        );
    }

    // ───────────────────────────────────────────────────────────
    // 2. Key / Value Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Basic scalar value test.
     *
     * Probably the single most common EXAMD syntax pattern.
     */
    @Test
    @DisplayName("Simple key-value: title: Physics Test")
    void testSimpleKeyValue() {

        List<Token> tokens =
            lex("title: Physics Test");

        assertToken(tokens.get(0), TokenType.KEY, "title");

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "Physics Test"
        );

        assertToken(tokens.get(2), TokenType.EOF, "");
    }

    /**
     * Numeric-looking values are still lexed as strings.
     *
     * Type interpretation happens later during validation/parsing.
     */
    @Test
    @DisplayName("Integer value: marks: 4")
    void testIntegerValue() {

        List<Token> tokens = lex("marks: 4");

        assertToken(tokens.get(0), TokenType.KEY, "marks");

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "4"
        );
    }

    /**
     * Duration syntax is extremely common in exams.
     */
    @Test
    @DisplayName("Duration value: duration: 30min")
    void testDurationValue() {

        List<Token> tokens =
            lex("duration: 30min");

        assertToken(
            tokens.get(0),
            TokenType.KEY,
            "duration"
        );

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "30min"
        );
    }

    /**
     * Quotes should disappear after lexing.
     *
     * The parser shouldn't care whether the original source used quotes.
     */
    @Test
    @DisplayName("Quoted value strips quotes")
    void testQuotedValue() {

        List<Token> tokens =
            lex("content: \"What is Newton's Second Law?\"");

        assertToken(
            tokens.get(0),
            TokenType.KEY,
            "content"
        );

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "What is Newton's Second Law?"
        );
    }

    /**
     * Underscores are valid inside EXAMD keys.
     */
    @Test
    @DisplayName("Underscored key: total_marks")
    void testUnderscoredKey() {

        List<Token> tokens =
            lex("total_marks: 50");

        assertToken(
            tokens.get(0),
            TokenType.KEY,
            "total_marks"
        );

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "50"
        );
    }

    /**
     * Hyphenated keys are also valid.
     */
    @Test
    @DisplayName("Hyphenated key: negative-marking")
    void testHyphenatedKey() {

        List<Token> tokens =
            lex("negative-marking: 0.25");

        assertToken(
            tokens.get(0),
            TokenType.KEY,
            "negative-marking"
        );

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "0.25"
        );
    }

    // ───────────────────────────────────────────────────────────
    // 3. Pipe Scalar Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Pipe scalars are the only real multiline structure in EXAMD.
     *
     * Example:
     *
     *   content: |
     *     hello
     *     world
     *
     * Every indented line becomes an INDENT_LINE token.
     */
    @Test
    @DisplayName("Pipe scalar: content: | with two indented lines")
    void testPipeScalar() {

        String source =
            "content: |\n" +
            "  Line one.\n" +
            "  Line two.\n";

        List<Token> tokens = lex(source);

        assertToken(tokens.get(0), TokenType.KEY, "content");

        assertToken(
            tokens.get(1),
            TokenType.VALUE_PIPE,
            "|"
        );

        assertToken(
            tokens.get(2),
            TokenType.INDENT_LINE,
            "Line one."
        );

        assertToken(
            tokens.get(3),
            TokenType.INDENT_LINE,
            "Line two."
        );
    }

    /**
     * Blank lines terminate pipe blocks.
     *
     * This test protects against a very common multiline-state bug:
     * accidentally continuing to consume lines forever.
     */
    @Test
    @DisplayName("Pipe scalar ends at blank line")
    void testPipeScalarEndsAtBlank() {

        String source =
            "content: |\n" +
            "  Line one.\n" +
            "\n" +
            "marks: 4\n";

        List<Token> tokens = lex(source);

        assertToken(tokens.get(0), TokenType.KEY, "content");

        assertToken(
            tokens.get(1),
            TokenType.VALUE_PIPE,
            "|"
        );

        assertToken(
            tokens.get(2),
            TokenType.INDENT_LINE,
            "Line one."
        );

        assertToken(
            tokens.get(3),
            TokenType.BLANK,
            ""
        );

        assertToken(
            tokens.get(4),
            TokenType.KEY,
            "marks"
        );

        assertToken(
            tokens.get(5),
            TokenType.VALUE_SCALAR,
            "4"
        );
    }

    // ───────────────────────────────────────────────────────────
    // 4. List Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Basic YAML-style list support.
     */
    @Test
    @DisplayName("Plain list items")
    void testPlainListItems() {

        String source =
            "options:\n" +
            "- Photosynthesis\n" +
            "- Respiration\n";

        List<Token> tokens = lex(source);

        assertToken(tokens.get(0), TokenType.KEY, "options");

        // Empty scalar before list begins.
        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            ""
        );

        assertToken(
            tokens.get(2),
            TokenType.LIST_ITEM,
            "Photosynthesis"
        );

        assertToken(
            tokens.get(3),
            TokenType.LIST_ITEM,
            "Respiration"
        );
    }

    /**
     * MCQ options are usually written as labeled list items:
     *
     *   - A: Paris
     *   - B: London
     */
    @Test
    @DisplayName("Labeled list items (MCQ options)")
    void testLabeledListItems() {

        String source =
            "options:\n" +
            "- A: Paris\n" +
            "- B: London\n" +
            "- C: Berlin\n";

        List<Token> tokens = lex(source);

        assertToken(tokens.get(0), TokenType.KEY, "options");

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            ""
        );

        assertToken(
            tokens.get(2),
            TokenType.LIST_ITEM,
            "A: Paris"
        );

        assertToken(
            tokens.get(3),
            TokenType.LIST_ITEM,
            "B: London"
        );

        assertToken(
            tokens.get(4),
            TokenType.LIST_ITEM,
            "C: Berlin"
        );
    }

    // ───────────────────────────────────────────────────────────
    // 5. Comment + Blank Line Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Comments should still become tokens.
     *
     * That gives future compiler stages the option to:
     *   - preserve comments
     *   - generate docs
     *   - build formatter tools
     */
    @Test
    @DisplayName("Comment lines are lexed but labelled COMMENT")
    void testCommentLine() {

        String source =
            "# This is a comment\n" +
            "title: Physics\n";

        List<Token> tokens = lex(source);

        assertEquals(
            TokenType.COMMENT,
            tokens.get(0).type
        );

        assertToken(
            tokens.get(1),
            TokenType.KEY,
            "title"
        );
    }

    /**
     * Blank lines are explicit tokens.
     *
     * This makes preserving source structure much easier later.
     */
    @Test
    @DisplayName("Blank lines emit BLANK token")
    void testBlankLine() {

        String source =
            "title: Physics\n" +
            "\n" +
            "marks: 4\n";

        List<Token> tokens = lex(source);

        assertToken(tokens.get(0), TokenType.KEY, "title");

        assertToken(
            tokens.get(1),
            TokenType.VALUE_SCALAR,
            "Physics"
        );

        assertToken(
            tokens.get(2),
            TokenType.BLANK,
            ""
        );

        assertToken(
            tokens.get(3),
            TokenType.KEY,
            "marks"
        );
    }

    // ───────────────────────────────────────────────────────────
    // 6. Error Handling Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Unterminated quoted strings should immediately fail.
     *
     * Recovering from broken string syntax tends to create confusing
     * downstream parser errors, so failing early is cleaner.
     */
    @Test
    @DisplayName("Unterminated quoted string emits E001 diagnostic")
    void testUnterminatedQuote() {
        Lexer lexer = new Lexer("test.examd", "content: \"unterminated string");
        lexer.tokenize();

        assertTrue(lexer.getDiagnostics().hasErrors());
        assertEquals("E001", lexer.getDiagnostics().getErrors().get(0).code);
    }

    // ───────────────────────────────────────────────────────────
    // 7. Span Accuracy Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Tokens should report correct line numbers.
     *
     * Diagnostics become useless if spans are wrong.
     */
    @Test
    @DisplayName("Block header span is on line 1")
    void testBlockHeaderSpan() {

        List<Token> tokens = lex("[EXAM]\n");

        Token header = tokens.get(0);

        assertEquals(1, header.span.lineStart);
    }

    /**
     * Multi-line files are where span bugs usually appear.
     */
    @Test
    @DisplayName("Span line numbers are correct across multiple lines")
    void testSpanLineNumbers() {

        String source =
            "[EXAM]\n" +
            "title: Physics\n" +
            "marks: 4\n";

        List<Token> tokens = lex(source);

        assertEquals(1, tokens.get(0).span.lineStart);
        assertEquals(2, tokens.get(1).span.lineStart);
        assertEquals(2, tokens.get(2).span.lineStart);
        assertEquals(3, tokens.get(3).span.lineStart);
    }

    // ───────────────────────────────────────────────────────────
    // 8. Full Integration Test
    // ───────────────────────────────────────────────────────────

    /**
     * Full end-to-end lexer integration test.
     *
     * Instead of isolated syntax fragments,
     * this feeds a realistic minimal exam into the lexer.
     *
     * These tests are incredibly valuable because many lexer bugs only
     * appear when multiple constructs interact together.
     */
    @Test
    @DisplayName("Complete minimal exam tokenizes correctly")
    void testMinimalExam() {

        String source =
            "[EXAM]\n" +
            "title: Physics Test\n" +
            "duration: 30min\n" +
            "\n" +
            "[SECTION: A]\n" +
            "title: Multiple Choice\n" +
            "\n" +
            "[Q: 1]\n" +
            "marks: 4\n" +
            "\n" +
            "[STEM]\n" +
            "type: text\n" +
            "content: What is Newton's Second Law?\n" +
            "\n" +
            "[INTERACT]\n" +
            "type: mcq\n" +
            "options:\n" +
            "- A: F = ma\n" +
            "- B: E = mc2\n" +
            "- C: F = mv\n" +
            "- D: F = mg\n" +
            "\n" +
            "[EVALUATE]\n" +
            "type: exact\n" +
            "answer: A\n";

        List<Token> tokens = lex(source);

        /**
         * Extremely useful while debugging lexer/parser interactions.
         *
         * I usually keep this during active compiler development and
         * remove it later if test output becomes noisy.
         */
        System.out.println("=== Token Stream ===");
        System.out.print(Lexer.dumpTokens(tokens));
        System.out.println("===================");

        assertToken(
            tokens.get(0),
            TokenType.BLOCK_HEADER,
            "EXAM"
        );

        assertToken(
            tokens.get(1),
            TokenType.KEY,
            "title"
        );

        assertToken(
            tokens.get(2),
            TokenType.VALUE_SCALAR,
            "Physics Test"
        );

        /**
         * Stream filtering is cleaner than relying on hardcoded indexes
         * once token streams become large.
         */
        Token sectionHeader = tokens.stream()
            .filter(t ->
                t.type == TokenType.BLOCK_HEADER &&
                t.lexeme.startsWith("SECTION")
            )
            .findFirst()
            .orElseThrow(() ->
                new AssertionError("No SECTION header found")
            );

        assertEquals("SECTION: A", sectionHeader.lexeme);

        long listItemCount = tokens.stream()
            .filter(t -> t.type == TokenType.LIST_ITEM)
            .count();

        assertEquals(
            4,
            listItemCount,
            "Expected 4 MCQ option list items"
        );

        Token answerKey = tokens.stream()
            .filter(t ->
                t.type == TokenType.KEY &&
                t.lexeme.equals("answer")
            )
            .findFirst()
            .orElseThrow(() ->
                new AssertionError("No 'answer' key found")
            );

        int answerIdx = tokens.indexOf(answerKey);

        assertToken(
            tokens.get(answerIdx + 1),
            TokenType.VALUE_SCALAR,
            "A"
        );

        // Final token should ALWAYS be EOF.
        assertToken(
            tokens.get(tokens.size() - 1),
            TokenType.EOF,
            ""
        );
    }

    // ───────────────────────────────────────────────────────────
    // 9. Debug Utility Tests
    // ───────────────────────────────────────────────────────────

    /**
     * Small smoke test for dumpTokens().
     *
     * We don't deeply validate formatting here —
     * just ensure the dump contains expected token names.
     */
    @Test
    @DisplayName("Token dump is readable (smoke test)")
    void testDumpTokens() {

        List<Token> tokens =
            lex("[EXAM]\ntitle: Test\n");

        String dump =
            Lexer.dumpTokens(tokens);

        assertTrue(dump.contains("BLOCK_HEADER"));
        assertTrue(dump.contains("KEY"));
        assertTrue(dump.contains("EOF"));
    }
}