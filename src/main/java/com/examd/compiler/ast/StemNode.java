package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * StemNode — the body of a question: what the student reads/sees.
 *
 * Maps to a [STEM] block in EXAMD source:
 *
 *   [STEM]
 *   type: text
 *   content: What is Newton's Second Law of Motion?
 *
 * or with a pipe scalar:
 *
 *   [STEM]
 *   type: text
 *   content: |
 *     A body of mass $m$ experiences a net force $F$.
 *     Express $F$ in terms of $m$ and acceleration $a$.
 *
 * The `content` field holds the final assembled string —
 * pipe block lines are joined with '\n' by the parser.
 */
public final class StemNode implements AstNode {

    /**
     * The stem type string as written in the source.
     * Known values: "text", "image", "audio", "code", "diagram"
     * Unknown values are reported by the Validator (E011), not the Parser.
     */
    public final String type;

    /**
     * The assembled content string.
     * For pipe scalars, lines are joined with '\n'.
     * For inline scalars, this is the single-line value.
     * May be empty if the author omitted the content key (Validator emits E012).
     */
    public final String content;

    /**
     * All key-value pairs from the [STEM] block, in source order.
     * Includes "type" and "content" plus any extension keys.
     * The validator checks which keys are valid for each stem type.
     */
    public final Map<String, String> metadata;

    private final Span span;

    public StemNode(String type, String content, Map<String, String> metadata, Span span) {
        this.type     = type     != null ? type     : "";
        this.content  = content  != null ? content  : "";
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.span     = span;
    }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "StemNode{type=" + type + ", content=" + content.substring(0, Math.min(30, content.length())) + "…}";
    }
}
