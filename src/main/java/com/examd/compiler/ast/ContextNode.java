package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * ContextNode — a shared passage, data set, or image that multiple questions reference.
 *
 * Maps to a [CONTEXT: RC1] block:
 *
 *   [CONTEXT: RC1]
 *   type: passage
 *   content: |
 *     The kinetic theory of gases states that gas pressure arises from
 *     the collisions of molecules with the walls of a container...
 *
 * Questions reference a context by id:
 *
 *   [Q: 3]
 *   context_ref: RC1
 *   marks: 2
 *   ...
 *
 * The Validator checks that every context_ref points to a declared ContextNode (E015).
 * The Generator inlines or references the context in the HTML output.
 */
public final class ContextNode implements AstNode {

    /** The identifier after the colon: "RC1" in [CONTEXT: RC1]. */
    public final String id;

    public final String type;
    public final String content;
    public final Map<String, String> metadata;

    private final Span span;

    public ContextNode(String id, String type, String content,
                       Map<String, String> metadata, Span span) {
        this.id       = id      != null ? id      : "";
        this.type     = type    != null ? type    : "";
        this.content  = content != null ? content : "";
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.span     = span;
    }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "ContextNode{id=" + id + ", type=" + type + "}";
    }
}
