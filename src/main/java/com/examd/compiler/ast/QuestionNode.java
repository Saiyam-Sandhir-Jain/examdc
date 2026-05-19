package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * QuestionNode — one complete question in the exam.
 *
 * Maps to a [Q: N] block and its child blocks:
 *
 *   [Q: 1]
 *   marks: 4
 *   negative_marking: 1
 *   context_ref: RC1
 *
 *   [STEM]
 *   type: text
 *   content: What is Newton's Second Law?
 *
 *   [INTERACT]
 *   type: mcq
 *   options:
 *   - A: F = ma
 *   - B: E = mc²
 *
 *   [EVALUATE]
 *   type: exact
 *   answer: A
 *
 * DESIGN NOTE — why nested block objects instead of a flat map?
 * We could store everything as Map<String, Object> but that loses the type
 * structure that subsequent phases rely on. The Validator can call
 * question.stem.type directly rather than digging through a map. Type safety
 * from the AST forward is the whole point of building an AST.
 *
 * OPTIONAL FIELDS:
 * stem, interact, evaluate may be null if the source is malformed.
 * The Validator emits E005/E006/E007 for missing required sub-blocks.
 * Phases after validation only run if the Validator passed, so they can
 * assume non-null. The Parser does not assume non-null.
 */
public final class QuestionNode implements AstNode {
    public void accept(AstVisitor v) { v.visitQuestion(this); }

    /** The question identifier: "1" in [Q: 1]. */
    public final String id;

    /**
     * Key-value metadata from the [Q: N] block itself (marks, negative_marking, etc.).
     * Does NOT include the metadata of child blocks (stem/interact/evaluate).
     */
    public final Map<String, String> metadata;

    /** The [STEM] child block. Null if missing (Validator reports E005). */
    public final StemNode stem;

    /** The [INTERACT] child block. Null if missing (Validator reports E006). */
    public final InteractNode interact;

    /** The [EVALUATE] child block. Null if missing (Validator reports E007). */
    public final EvaluateNode evaluate;

    /**
     * Optional [CONTEXT: id] blocks declared inside this question.
     * Usually contexts are declared at section or exam level, but
     * question-scoped contexts are valid.
     */
    public final List<ContextNode> contexts;

    private final Span span;

    public QuestionNode(String id,
                        Map<String, String> metadata,
                        StemNode stem,
                        InteractNode interact,
                        EvaluateNode evaluate,
                        List<ContextNode> contexts,
                        Span span) {
        this.id       = id != null ? id : "";
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.stem     = stem;
        this.interact = interact;
        this.evaluate = evaluate;
        this.contexts = Collections.unmodifiableList(new ArrayList<>(contexts));
        this.span     = span;
    }

    /** Convenience: returns the marks value or "0" if not specified. */
    public String marks() { return metadata.getOrDefault("marks", "0"); }

    /** True only if all three required sub-blocks are present. */
    public boolean isComplete() { return stem != null && interact != null && evaluate != null; }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "QuestionNode{id=" + id + ", marks=" + marks()
               + ", complete=" + isComplete() + "}";
    }
}
