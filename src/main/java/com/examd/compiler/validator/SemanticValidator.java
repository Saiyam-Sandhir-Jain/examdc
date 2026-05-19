package com.examd.compiler.validator;

import com.examd.compiler.ast.*;
import com.examd.compiler.diagnostics.DiagnosticCollector;
import com.examd.compiler.validator.rules.*;

import java.util.List;

/**
 * SemanticValidator — Phase 3 of the ExamdC compiler pipeline.
 *
 * ═══════════════════════════════════════════════════════════════
 *  WHAT IS SEMANTIC ANALYSIS?
 * ═══════════════════════════════════════════════════════════════
 *
 * The Lexer checked CHARACTERS — are these valid tokens?
 * The Parser checked STRUCTURE — do these tokens form a valid tree?
 * The Validator checks MEANING  — does this tree make sense?
 *
 * Examples of things the parser cannot catch (it only sees structure):
 *   - A question that exists but has no [STEM] (parser accepted it — it's optional in grammar)
 *   - A context_ref: RC1 pointing to a [CONTEXT: RC2] that doesn't exist
 *   - marks: abc (valid string token, invalid semantic value)
 *   - total_marks: 100 but questions only add up to 95
 *
 * All of these require understanding WHAT the tokens MEAN — that's semantics.
 *
 * ═══════════════════════════════════════════════════════════════
 *  THE TWO-PASS DESIGN
 * ═══════════════════════════════════════════════════════════════
 *
 * Pass 1 — COLLECT (collectSymbols):
 *   Walk the entire AST. Register every declaration into the SymbolTable.
 *   No diagnostic emitted. Just building the lookup table.
 *
 *   Why? Because a question can reference a context declared later:
 *     [Q: 1]  context_ref: RC1     ← reference on line 10
 *     [CONTEXT: RC1] ...            ← declaration on line 40
 *   Without pass 1, we'd report RC1 as undeclared on line 10.
 *
 * Pass 2 — VALIDATE (validateAll):
 *   Walk the AST again. For every node, fire all registered rules whose
 *   nodeType() matches. Rules emit diagnostics into the shared collector.
 *
 * ═══════════════════════════════════════════════════════════════
 *  IMPLEMENTS AstVisitor
 * ═══════════════════════════════════════════════════════════════
 *
 * SemanticValidator implements AstVisitor — it uses accept() / visitX()
 * for the traversal. The same class handles both passes by switching
 * an internal 'mode' field.
 *
 * In real compilers (GCC, LLVM), multiple visitor passes are common.
 * We do the same but keep it simple with a single class and a mode flag.
 */
public final class SemanticValidator implements AstVisitor {

    // ── Registered rules ──────────────────────────────────────────────────

    private final R001_MarksPositive       r001 = new R001_MarksPositive();
    private final R002_StemRequired        r002 = new R002_StemRequired();
    private final R003_InteractRequired    r003 = new R003_InteractRequired();
    private final R004_EvaluateRequired    r004 = new R004_EvaluateRequired();
    private final R005_AnswerNotEmpty      r005 = new R005_AnswerNotEmpty();
    private final R006_ContextRefResolvable r006 = new R006_ContextRefResolvable();
    private final R007_UniqueQuestionIds   r007 = new R007_UniqueQuestionIds();
    private final R008_TotalMarksMatch     r008 = new R008_TotalMarksMatch();

    // ── State ─────────────────────────────────────────────────────────────

    private enum Mode { COLLECT, VALIDATE }
    private Mode mode = Mode.COLLECT;

    private ValidationContext ctx;

    /** The section ID the cursor is currently inside — used by R007 for scoping. */
    private String currentSectionId = "";

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Runs both passes over the exam AST and returns the DiagnosticCollector.
     *
     * Call this only when the parser produced a non-null ExamNode.
     * Check diagnostics.hasErrors() to decide whether to proceed to Phase 4.
     *
     * @param exam        The root AST node from the Parser.
     * @param diagnostics Shared collector (may already contain Lexer/Parser errors).
     * @return The same DiagnosticCollector, now also containing Phase 3 diagnostics.
     */
    public DiagnosticCollector validate(ExamNode exam, DiagnosticCollector diagnostics) {
        SymbolTable symbolTable = new SymbolTable();
        this.ctx = new ValidationContext(diagnostics, symbolTable, exam);

        // ── Pass 1: collect all symbols ───────────────────────────────────
        mode = Mode.COLLECT;
        exam.accept(this);

        // ── Pass 2: fire all rules ────────────────────────────────────────
        mode = Mode.VALIDATE;
        currentSectionId = "";
        exam.accept(this);

        return diagnostics;
    }

    // ── AstVisitor — called by accept() on each node ──────────────────────

    @Override
    public void visitExam(ExamNode node) {
        if (mode == Mode.COLLECT) {
            // Register exam-level contexts
            for (ContextNode c : node.contexts) c.accept(this);
            // Recurse into sections or flat questions
            for (SectionNode s : node.sections) s.accept(this);
            for (QuestionNode q : node.questions) q.accept(this);

        } else {
            // Validate exam-level rule (R008)
            fireOn(node, r008);
            // Recurse
            for (SectionNode s : node.sections) s.accept(this);
            for (QuestionNode q : node.questions) q.accept(this);
        }
    }

    @Override
    public void visitSection(SectionNode node) {
        String prevSectionId = currentSectionId;
        currentSectionId = node.id;

        if (mode == Mode.COLLECT) {
            ctx.symbolTable.registerSection(node.id, node.getSpan());
            for (ContextNode c : node.contexts)  c.accept(this);
            for (QuestionNode q : node.questions) q.accept(this);

        } else {
            for (ContextNode c : node.contexts)  c.accept(this);
            for (QuestionNode q : node.questions) q.accept(this);
        }

        currentSectionId = prevSectionId;
    }

    @Override
    public void visitQuestion(QuestionNode node) {
        if (mode == Mode.COLLECT) {
            // Register the question ID in the symbol table for R007
            ctx.symbolTable.registerQuestion(currentSectionId, node.id, node.getSpan());
            // Recurse into child nodes (so their contexts get registered too)
            for (ContextNode c : node.contexts) c.accept(this);

        } else {
            // Fire all QuestionNode rules
            r007.setCurrentSectionId(currentSectionId);
            fireOn(node, r001);
            fireOn(node, r002);
            fireOn(node, r003);
            fireOn(node, r004);
            fireOn(node, r006);
            fireOn(node, r007);

            // Recurse into child sub-blocks
            if (node.stem     != null) node.stem.accept(this);
            if (node.interact != null) node.interact.accept(this);
            if (node.evaluate != null) node.evaluate.accept(this);
            for (ContextNode c : node.contexts) c.accept(this);
        }
    }

    @Override
    public void visitStem(StemNode node) {
        // No rules yet on StemNode — placeholder for Phase 5 (sub-language dispatch)
    }

    @Override
    public void visitInteract(InteractNode node) {
        // No rules yet — future R009 (known interact types), R010 (MCQ needs >= 2 options)
    }

    @Override
    public void visitEvaluate(EvaluateNode node) {
        if (mode == Mode.VALIDATE) {
            fireOn(node, r005);
        }
    }

    @Override
    public void visitContext(ContextNode node) {
        if (mode == Mode.COLLECT) {
            // Register the context ID — R006 uses this in Pass 2
            ctx.symbolTable.registerContext(node.id, node.getSpan());
        }
    }

    // ── Rule firing helper ────────────────────────────────────────────────

    /**
     * Fires a rule against a node if the node is an instance of the rule's target type.
     *
     * The unchecked cast is safe here because we've verified nodeType().isInstance(node).
     * This is the central dispatch mechanism — it replaces a sea of instanceof checks.
     */
    @SuppressWarnings("unchecked")
    private <T extends AstNode> void fireOn(AstNode node, ValidationRule<T> rule) {
        if (rule.nodeType().isInstance(node)) {
            rule.validate((T) node, ctx);
        }
    }

    // ── Describe registered rules ─────────────────────────────────────────

    /**
     * Returns a formatted list of all registered rules — useful for --help output
     * and documentation generation.
     */
    public String describeRules() {
        List<ValidationRule<?>> rules = List.of(r001, r002, r003, r004, r005, r006, r007, r008);
        StringBuilder sb = new StringBuilder("Registered validation rules:\n");
        for (ValidationRule<?> r : rules) {
            sb.append(String.format("  %-6s  %s%n", r.ruleId(), r.description()));
        }
        return sb.toString();
    }
}
