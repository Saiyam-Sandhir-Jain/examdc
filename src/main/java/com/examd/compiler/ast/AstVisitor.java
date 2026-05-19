package com.examd.compiler.ast;

/**
 * AstVisitor — the Visitor pattern for traversing the ExamdC AST.
 *
 * ═══════════════════════════════════════════════════════════════
 *  WHY THE VISITOR PATTERN?
 * ═══════════════════════════════════════════════════════════════
 *
 * We have 7 node types and will have many operations on the tree:
 *   - Semantic validation 
 *   - Optimization passes 
 *   - HTML code generation 
 *   - JSON export 
 *   - Pretty-printing for debug (any time)
 *
 * WITHOUT the Visitor pattern, you'd put all operations on the nodes:
 *
 *   class ExamNode {
 *       void validate() { ... }       // mixes concerns
 *       String toHtml()  { ... }       // node knows about HTML?
 *       String toJson()  { ... }       // node knows about JSON?
 *   }
 *
 * The nodes would grow without bound as we add operations. Worse, adding
 * a new operation (e.g. "export to PDF") means touching every node class.
 *
 * WITH the Visitor pattern, each operation lives in its own class:
 *
 *   class SemanticValidator implements AstVisitor { ... }
 *   class HtmlGenerator       implements AstVisitor { ... }
 *   class JsonExporter         implements AstVisitor { ... }
 *
 * Nodes stay simple value objects. Operations are isolated. Adding a new
 * operation = add one new class; no existing code changes.
 *
 * ═══════════════════════════════════════════════════════════════
 *  HOW IT WORKS — The Double Dispatch
 * ═══════════════════════════════════════════════════════════════
 *
 * The pattern uses two virtual method calls — "double dispatch":
 *
 *   Step 1 — Caller calls node.accept(visitor):
 *     examNode.accept(validator);
 *
 *   Step 2 — Node calls visitor.visitExam(this):
 *     // In ExamNode.accept():
 *     public void accept(AstVisitor v) { v.visitExam(this); }
 *
 * Why the indirection? Because Java method dispatch is based on the
 * static type of the receiver. If you call visitor.visit(node) where
 * node is typed as AstNode, Java can't pick the right overload.
 * By having the node call the visitor with a concrete 'this', it
 * provides the correct type — the visitor gets visitExam(), not
 * a generic visit(AstNode).
 *
 * ═══════════════════════════════════════════════════════════════
 *  DEFAULT METHODS — making visitors optional-field-friendly
 * ═══════════════════════════════════════════════════════════════
 *
 * Each visit method has a default no-op implementation. This lets a
 * visitor (e.g. HtmlGenerator) only override the nodes it cares about,
 * without being forced to implement visitContext() if contexts don't
 * affect its output.
 */
public interface AstVisitor {

    /** Called when visiting the root [EXAM] node. */
    default void visitExam(ExamNode node)         {}

    /** Called when visiting a [SECTION: id] node. */
    default void visitSection(SectionNode node)   {}

    /** Called when visiting a [Q: N] node. */
    default void visitQuestion(QuestionNode node) {}

    /** Called when visiting a [STEM] node. */
    default void visitStem(StemNode node)         {}

    /** Called when visiting an [INTERACT] node. */
    default void visitInteract(InteractNode node) {}

    /** Called when visiting an [EVALUATE] node. */
    default void visitEvaluate(EvaluateNode node) {}

    /** Called when visiting a [CONTEXT: id] node. */
    default void visitContext(ContextNode node)   {}
}
