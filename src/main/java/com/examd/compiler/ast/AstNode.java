package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;

/**
 * AstNode — the common interface for every node in the ExamdC Abstract Syntax Tree.
 *
 * After the Lexer runs, we have a flat list of tokens. The list has no
 * visible structure — it's just one token after another. The parser's
 * job is to find the TREE hidden inside that flat list.
 *
 * Consider this EXAMD source:
 *
 *   [EXAM]
 *   title: Physics Test
 *
 *   [SECTION: A]
 *     [Q: 1]
 *     marks: 4
 *     [STEM] ...
 *     [INTERACT] ...
 *     [EVALUATE] ...
 *
 * The flat token list is: BLOCK_HEADER KEY VALUE BLOCK_HEADER BLOCK_HEADER KEY ...
 * The AST is:
 *
 *   ExamNode("Physics Test")
 *   └── SectionNode("A")
 *       └── QuestionNode("1")
 *           ├── metadata: {marks: "4"}
 *           ├── StemNode(type="text", content="...")
 *           ├── InteractNode(type="mcq", options=[...])
 *           └── EvaluateNode(type="exact", answer="A")
 *
 * The tree is what ALL subsequent phases operate on:
 *   - Validator walks the tree checking rules
 *   - Optimizer rewrites subtrees
 *   - Generator traverses the tree emitting HTML
 *
 * WHY AN INTERFACE?
 * All AST nodes share one guarantee: they know their Span (where they
 * came from in the source). An interface enforces this contract without
 * forcing all nodes to share a base class. This keeps the hierarchy flat
 * and makes the Visitor pattern easy to add.
 *
 * VISITOR PATTERN:
 * Each phase that needs to walk the tree implements AstVisitor and
 * passes itself to node.accept(). The node calls back with its concrete
 * type — visitExam(this), visitSection(this), etc. — so Java dispatches
 * to the right method. accept() is declared here so the compiler enforces
 * that every node implements it.
 */
public interface AstNode {

    /**
     * Returns the source location of this node.
     *
     * For block nodes (ExamNode, SectionNode, QuestionNode), the span
     * covers from the opening [BLOCK_HEADER] to the last token of the block.
     *
     * For leaf nodes (StemNode, EvaluateNode), the span covers the
     * entire [STEM] or [EVALUATE] sub-block.
     *
     * Never returns null.
     */
    Span getSpan();

    /**
     * Accepts a visitor and dispatches to the correct visit method.
     * Every concrete node implements this as a one-liner:
     *
     *   public void accept(AstVisitor v) { v.visitExam(this); }
     *
     * Declared here so the compiler enforces it on every node —
     * a node that forgets accept() won't compile.
     */
    void accept(AstVisitor v);

    /**
     * Returns the human-readable node kind for debug output.
     * Example: "ExamNode", "SectionNode", "QuestionNode"
     */
    default String kind() {
        return getClass().getSimpleName();
    }
}