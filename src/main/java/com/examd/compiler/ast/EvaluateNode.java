package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * EvaluateNode — the answer key and scoring configuration for a question.
 *
 * Maps to an [EVALUATE] block:
 *
 *   [EVALUATE]
 *   type: exact
 *   answer: A
 *
 * or for partial scoring:
 *
 *   [EVALUATE]
 *   type: partial
 *   answer:
 *   - A
 *   - C
 *   marks_per_correct: 2
 *   penalty_per_wrong: 0.5
 *
 * The `type` field determines how the answer is checked at runtime:
 *   exact    → answer must match exactly
 *   range    → numeric range: answer: 9.8..10.2
 *   partial  → multi-answer with partial credit
 *   custom   → scored by a plugin (plugin_id key required)
 *   manual   → human-graded (essay, draw)
 */
public final class EvaluateNode implements AstNode {

    public void accept(AstVisitor v) { v.visitEvaluate(this); }

    public final String type;

    /**
     * The answer(s). For single-answer types, this has one element.
     * For multi-answer types (msq, partial), this has multiple elements.
     * For manual/custom types, this may be empty.
     */
    public final List<String> answers;

    /** All key-value pairs from the [EVALUATE] block. */
    public final Map<String, String> metadata;

    private final Span span;

    public EvaluateNode(String type, List<String> answers,
                        Map<String, String> metadata, Span span) {
        this.type     = type    != null ? type    : "";
        this.answers  = Collections.unmodifiableList(new ArrayList<>(answers));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.span     = span;
    }

    /** Convenience: returns the first answer or "" if empty. */
    public String primaryAnswer() {
        return answers.isEmpty() ? "" : answers.get(0);
    }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "EvaluateNode{type=" + type + ", answers=" + answers + "}";
    }
}
