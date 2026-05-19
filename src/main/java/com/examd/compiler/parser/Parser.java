package com.examd.compiler.parser;

import com.examd.compiler.ast.*;
import com.examd.compiler.diagnostics.*;
import com.examd.compiler.lexer.*;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 *  WHAT IS RECURSIVE DESCENT PARSING?
 * ═══════════════════════════════════════════════════════════════
 *
 * "Recursive descent" means: one method per grammar rule, and those
 * methods call each other to match nested structure.
 *
 * EXAMD grammar (informal):
 *   exam     → [EXAM] metadata (context | section | question)*
 *   section  → [SECTION: id] metadata (context | question)+
 *   question → [Q: id] metadata context? [STEM] [INTERACT] [EVALUATE]
 *   stem     → [STEM] metadata
 *   interact → [INTERACT] metadata options?
 *   evaluate → [EVALUATE] metadata answers?
 *   metadata → (KEY VALUE | KEY PIPE INDENT_LINE+ | LIST_ITEM)*
 *
 * One method per rule:
 *   parseExam(), parseSection(), parseQuestion(),
 *   parseStem(), parseInteract(), parseEvaluate(), parseMetadata()
 *
 * ═══════════════════════════════════════════════════════════════
 *  THE CURSOR PATTERN
 * ═══════════════════════════════════════════════════════════════
 *
 *   tokens: [BLOCK_HEADER] [KEY] [VALUE] [BLOCK_HEADER] ... [EOF]
 *   cursor:                 ^
 *
 *   peek()    → look at current token, do not move
 *   advance() → consume current token, move cursor forward
 *   match()   → advance only if current type matches
 *
 * One-token lookahead is sufficient for EXAMD — block headers always
 * tell us which parse method to call next.
 *
 * ═══════════════════════════════════════════════════════════════
 *  ERROR RECOVERY
 * ═══════════════════════════════════════════════════════════════
 *
 * Strategy: emit a diagnostic, return null or empty node, continue.
 * Panic mode: syncToNextBlock() skips to the next [BLOCK_HEADER] on
 * complete confusion — one bad block never kills the rest of the parse.
 */
public final class Parser {

    private final List<Token> tokens;
    private final DiagnosticCollector diagnostics;
    private int cursor = 0;

    public Parser(List<Token> tokens, DiagnosticCollector diagnostics) {
        if (tokens == null || tokens.isEmpty())
            throw new IllegalArgumentException("Token list cannot be null or empty");
        this.tokens      = tokens;
        this.diagnostics = diagnostics;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Parses the full token stream into an ExamNode AST root.
     * Returns null only if [EXAM] is missing entirely (E004).
     * All other errors are collected; check diagnostics.hasErrors() after.
     */
    public ExamNode parse() {
        skipTrivial();
        if (!isBlockHeader("EXAM")) {
            diagnostics.error("E004",
                "File must begin with an [EXAM] block",
                peek().span,
                "Add [EXAM] as the first non-comment line in the file");
            return null;
        }
        return parseExam();
    }

    // ── Grammar rules ─────────────────────────────────────────────────────

    private ExamNode parseExam() {
        Token header   = advance();                      // consume [EXAM]
        Span  startSpan = header.span;

        Map<String, String> metadata  = parseMetadata();
        List<SectionNode>   sections  = new ArrayList<>();
        List<QuestionNode>  questions = new ArrayList<>();
        List<ContextNode>   contexts  = new ArrayList<>();

        skipTrivial();
        while (!peek().is(TokenType.EOF)) {
            skipTrivial();
            if (peek().is(TokenType.EOF)) break;

            if (isBlockHeaderPrefix("SECTION")) {
                sections.add(parseSection());
            } else if (isBlockHeaderPrefix("Q:")) {
                questions.add(parseQuestion());
            } else if (isBlockHeaderPrefix("CONTEXT:")) {
                contexts.add(parseContext());
            } else {
                diagnostics.error("E004",
                    "Unexpected block at exam level: [" + peek().lexeme + "]",
                    peek().span,
                    "Expected [SECTION: id], [Q: N], or [CONTEXT: id]");
                syncToNextBlock();
            }
        }

        return new ExamNode(metadata, sections, questions, contexts,
                            Span.merge(startSpan, peek().span));
    }

    private SectionNode parseSection() {
        Token  header    = advance();
        String id        = extractBlockId(header.lexeme);
        Span   startSpan = header.span;

        Map<String, String> metadata  = parseMetadata();
        List<QuestionNode>  questions = new ArrayList<>();
        List<ContextNode>   contexts  = new ArrayList<>();

        skipTrivial();
        while (!peek().is(TokenType.EOF)
               && !isBlockHeaderPrefix("SECTION")
               && !isBlockHeader("EXAM")) {
            skipTrivial();
            if (peek().is(TokenType.EOF) || isBlockHeaderPrefix("SECTION") || isBlockHeader("EXAM")) break;

            if (isBlockHeaderPrefix("Q:")) {
                questions.add(parseQuestion());
            } else if (isBlockHeaderPrefix("CONTEXT:")) {
                contexts.add(parseContext());
            } else {
                diagnostics.error("E004",
                    "Unexpected block in [SECTION: " + id + "]: [" + peek().lexeme + "]",
                    peek().span, "Expected [Q: N] or [CONTEXT: id]");
                syncToNextBlock();
            }
        }

        return new SectionNode(id, metadata, questions, contexts,
                               Span.merge(startSpan, peek().span));
    }

    private QuestionNode parseQuestion() {
        Token  header    = advance();
        String id        = extractBlockId(header.lexeme);
        Span   startSpan = header.span;

        Map<String, String> metadata = parseMetadata();
        StemNode      stem     = null;
        InteractNode  interact = null;
        EvaluateNode  evaluate = null;
        List<ContextNode> contexts = new ArrayList<>();

        skipTrivial();
        while (!peek().is(TokenType.EOF)
               && !isBlockHeaderPrefix("Q:")
               && !isBlockHeaderPrefix("SECTION")
               && !isBlockHeader("EXAM")) {
            skipTrivial();
            if (peek().is(TokenType.EOF)
                || isBlockHeaderPrefix("Q:")
                || isBlockHeaderPrefix("SECTION")
                || isBlockHeader("EXAM")) break;

            if (isBlockHeader("STEM")) {
                if (stem != null) {
                    diagnostics.error("E009", "Duplicate [STEM] in [Q: " + id + "]",
                                      peek().span, "Remove the duplicate [STEM] block");
                    syncToNextBlock();
                } else {
                    stem = parseStem();
                }
            } else if (isBlockHeader("INTERACT")) {
                if (interact != null) {
                    diagnostics.error("E009", "Duplicate [INTERACT] in [Q: " + id + "]",
                                      peek().span, null);
                    syncToNextBlock();
                } else {
                    interact = parseInteract();
                }
            } else if (isBlockHeader("EVALUATE")) {
                if (evaluate != null) {
                    diagnostics.error("E009", "Duplicate [EVALUATE] in [Q: " + id + "]",
                                      peek().span, null);
                    syncToNextBlock();
                } else {
                    evaluate = parseEvaluate();
                }
            } else if (isBlockHeaderPrefix("CONTEXT:")) {
                contexts.add(parseContext());
            } else {
                diagnostics.error("E004",
                    "Unexpected block in [Q: " + id + "]: [" + peek().lexeme + "]",
                    peek().span,
                    "Expected [STEM], [INTERACT], [EVALUATE], or [CONTEXT: id]");
                syncToNextBlock();
            }
        }

        return new QuestionNode(id, metadata, stem, interact, evaluate,
                                contexts, Span.merge(startSpan, peek().span));
    }

    private StemNode parseStem() {
        Token header = advance();                        // consume [STEM]
        Map<String, String> metadata = parseMetadata();
        return new StemNode(
            metadata.getOrDefault("type",    ""),
            metadata.getOrDefault("content", ""),
            metadata, header.span);
    }

    private InteractNode parseInteract() {
        Token header = advance();                        // consume [INTERACT]
        Map<String, String> metaRaw = parseMetadata();
        String type = metaRaw.getOrDefault("type", "");
        List<String> options = extractList(metaRaw, "options");
        Map<String, String> cleanMeta = new LinkedHashMap<>(metaRaw);
        cleanMeta.remove("options");
        return new InteractNode(type, options, cleanMeta, header.span);
    }

    private EvaluateNode parseEvaluate() {
        Token header = advance();                        // consume [EVALUATE]
        Map<String, String> metaRaw = parseMetadata();
        String type = metaRaw.getOrDefault("type", "");
        String answerRaw = metaRaw.getOrDefault("answer", "").trim();

        List<String> answers;
        if (answerRaw.contains("\n")) {
            answers = Arrays.asList(answerRaw.split("\n"));
        } else if (!answerRaw.isEmpty()) {
            answers = Collections.singletonList(answerRaw);
        } else {
            answers = Collections.emptyList();
        }

        Map<String, String> cleanMeta = new LinkedHashMap<>(metaRaw);
        cleanMeta.remove("answer");
        return new EvaluateNode(type, answers, cleanMeta, header.span);
    }

    private ContextNode parseContext() {
        Token  header = advance();
        String id     = extractBlockId(header.lexeme);
        Map<String, String> metadata = parseMetadata();
        return new ContextNode(id,
            metadata.getOrDefault("type",    "passage"),
            metadata.getOrDefault("content", ""),
            metadata, header.span);
    }

    // ── Metadata parser ───────────────────────────────────────────────────

    /**
     * Consumes KEY-VALUE pairs (scalar, pipe, list) until the next block header or EOF.
     *
     * KEY + VALUE_SCALAR                 → simple entry
     * KEY + VALUE_PIPE + INDENT_LINE*    → pipe scalar, joined with '\n'
     * KEY + empty VALUE_SCALAR + LIST_ITEM* → list, joined with '\n'
     */
    private Map<String, String> parseMetadata() {
        Map<String, String> meta = new LinkedHashMap<>();

        skipTrivial();
        while (!peek().is(TokenType.EOF) && !peek().is(TokenType.BLOCK_HEADER)) {
            skipTrivial();
            if (peek().is(TokenType.EOF) || peek().is(TokenType.BLOCK_HEADER)) break;

            if (peek().is(TokenType.KEY)) {
                Token  keyTok  = advance();
                String keyName = keyTok.lexeme;

                if (peek().is(TokenType.VALUE_PIPE)) {
                    advance();                           // consume |
                    StringBuilder sb = new StringBuilder();
                    while (peek().is(TokenType.INDENT_LINE)) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(advance().lexeme);
                    }
                    meta.put(keyName, sb.toString());

                } else if (peek().is(TokenType.VALUE_SCALAR)) {
                    String scalar = advance().lexeme;

                    if (scalar.isEmpty()) {
                        // List follows
                        skipTrivial();
                        StringBuilder sb = new StringBuilder();
                        while (peek().is(TokenType.LIST_ITEM)) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(advance().lexeme);
                        }
                        meta.put(keyName, sb.toString());
                    } else {
                        meta.put(keyName, scalar);
                    }
                } else {
                    diagnostics.error("E010",
                        "Key '" + keyName + "' has no value",
                        keyTok.span,
                        "Add a value: " + keyName + ": <value>");
                }

            } else {
                advance(); // skip unexpected token inside metadata
            }
        }

        return meta;
    }

    // ── Cursor helpers ────────────────────────────────────────────────────

    private Token peek() { return tokens.get(cursor); }

    private Token advance() {
        Token t = tokens.get(cursor);
        if (cursor < tokens.size() - 1) cursor++;
        return t;
    }

    private void skipTrivial() {
        while (peek().is(TokenType.BLANK) || peek().is(TokenType.COMMENT)) advance();
    }

    private boolean isBlockHeader(String name) {
        return peek().is(TokenType.BLOCK_HEADER)
               && peek().lexeme.equalsIgnoreCase(name);
    }

    private boolean isBlockHeaderPrefix(String prefix) {
        return peek().is(TokenType.BLOCK_HEADER)
               && peek().lexeme.toUpperCase().startsWith(prefix.toUpperCase());
    }

    private static String extractBlockId(String lexeme) {
        int colon = lexeme.indexOf(':');
        return colon < 0 ? "" : lexeme.substring(colon + 1).trim();
    }

    private static List<String> extractList(Map<String, String> meta, String key) {
        String raw = meta.getOrDefault(key, "").trim();
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }

    /**
     * Panic-mode recovery: advance until the next [BLOCK_HEADER] or EOF.
     *
     * CRITICAL: if the cursor is already ON a block header when this is called
     * (the unrecognised header is what triggered the error), we advance past it
     * first. Without this, the caller's while-loop would never make progress
     * and spin forever.
     */
    private void syncToNextBlock() {
        // If we are sitting on the problematic header itself, consume it first.
        if (peek().is(TokenType.BLOCK_HEADER)) {
            advance();
        }
        // Now skip any body tokens until the next header (or EOF).
        while (!peek().is(TokenType.EOF) && !peek().is(TokenType.BLOCK_HEADER)) {
            advance();
        }
    }

    // ── Debug tree dump ───────────────────────────────────────────────────

    public static String dumpTree(ExamNode exam) {
        if (exam == null) return "<null — parse failed>\n";
        StringBuilder sb = new StringBuilder();
        sb.append(exam).append('\n');

        if (exam.isSectioned()) {
            for (int i = 0; i < exam.sections.size(); i++) {
                SectionNode s = exam.sections.get(i);
                boolean last = i == exam.sections.size() - 1;
                sb.append(last ? "└── " : "├── ").append(s).append('\n');
                String pad = last ? "    " : "│   ";
                for (int j = 0; j < s.questions.size(); j++) {
                    QuestionNode q = s.questions.get(j);
                    boolean lastQ = j == s.questions.size() - 1;
                    sb.append(pad).append(lastQ ? "└── " : "├── ").append(q).append('\n');
                }
            }
        } else {
            for (int i = 0; i < exam.questions.size(); i++) {
                QuestionNode q = exam.questions.get(i);
                boolean last = i == exam.questions.size() - 1;
                sb.append(last ? "└── " : "├── ").append(q).append('\n');
            }
        }
        return sb.toString();
    }
}