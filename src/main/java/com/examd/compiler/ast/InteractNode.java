package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * InteractNode — the interaction element the student engages with.
 *
 * Maps to an [INTERACT] block:
 *
 *   [INTERACT]
 *   type: mcq
 *   options:
 *   - A: F = ma
 *   - B: E = mc²
 *   - C: F = mv
 *   - D: F = mg
 *
 * The `type` field drives everything downstream:
 *   mcq       → single-choice, options list required
 *   msq       → multi-select, options list required
 *   fib       → fill-in-the-blank, no options
 *   match     → match-the-columns, two option lists
 *   sequence  → drag-to-order, options list
 *   coding    → code editor, language key required
 *   essay     → long-form text area
 *   draw      → canvas drawing
 *   upload    → file upload
 *
 * The Validator (Phase 3) checks type-specific rules (E013, E014).
 * The Parser just collects what's there.
 */
public final class InteractNode implements AstNode {

    public final String type;

    /**
     * The options list — relevant for mcq, msq, sequence, match.
     * Each string is the LIST_ITEM lexeme exactly as lexed.
     * For labeled options ("A: Paris"), the label is included: "A: Paris".
     * The Validator splits label from content when needed.
     */
    public final List<String> options;

    /** All key-value pairs from the [INTERACT] block. */
    public final Map<String, String> metadata;

    private final Span span;

    public InteractNode(String type, List<String> options,
                        Map<String, String> metadata, Span span) {
        this.type     = type    != null ? type    : "";
        this.options  = Collections.unmodifiableList(new ArrayList<>(options));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.span     = span;
    }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "InteractNode{type=" + type + ", options=" + options.size() + "}";
    }
}
