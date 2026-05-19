package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * ExamNode — the root node of the ExamdC Abstract Syntax Tree.
 *
 * Produced by the Parser when it successfully processes a [EXAM] block
 * and all its children. Every subsequent compiler phase begins here.
 *
 * An exam can be structured in two ways:
 *
 * SECTIONED (recommended for long exams):
 *
 *   ExamNode
 *   ├── metadata: {title: "Physics Test", duration: "90min"}
 *   ├── sections: [SectionNode("A"), SectionNode("B")]
 *   │   └── each SectionNode contains QuestionNodes
 *   └── questions: []    ← empty when sections are used
 *
 * FLAT (for short exams without sections):
 *
 *   ExamNode
 *   ├── metadata: {title: "Quick Quiz", duration: "15min"}
 *   ├── sections: []     ← empty when no [SECTION] blocks
 *   └── questions: [QuestionNode("1"), QuestionNode("2"), ...]
 *
 * CONTEXTS at exam scope are declared before any section/question
 * and are visible to all questions in the exam.
 *
 * NULL SAFETY:
 * sections and questions are always non-null lists (may be empty).
 * metadata is always non-null.
 * This node is the "happy path" result — if the parser could not
 * produce an ExamNode, it returns null and the pipeline stops.
 */
public final class ExamNode implements AstNode {

    /** Key-value pairs from the [EXAM] block itself. */
    public final Map<String, String> metadata;

    /**
     * Top-level sections. Empty if the exam is flat (no [SECTION] blocks).
     * Use isSectioned() to distinguish the two formats cleanly.
     */
    public final List<SectionNode> sections;

    /**
     * Top-level questions (flat exam only). Empty if sections are used.
     */
    public final List<QuestionNode> questions;

    /** Context blocks declared at exam scope. */
    public final List<ContextNode> contexts;

    private final Span span;

    public ExamNode(Map<String, String> metadata,
                    List<SectionNode> sections,
                    List<QuestionNode> questions,
                    List<ContextNode> contexts,
                    Span span) {
        this.metadata  = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.sections  = Collections.unmodifiableList(new ArrayList<>(sections));
        this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
        this.contexts  = Collections.unmodifiableList(new ArrayList<>(contexts));
        this.span      = span;
    }

    // ── Convenience ───────────────────────────────────────────────────────

    public boolean isSectioned()  { return !sections.isEmpty(); }
    public boolean isFlat()       { return sections.isEmpty(); }

    public String title()    { return metadata.getOrDefault("title",    ""); }
    public String duration() { return metadata.getOrDefault("duration", ""); }

    /** Total number of questions across all sections (or flat list). */
    public int questionCount() {
        if (isSectioned()) {
            return sections.stream().mapToInt(s -> s.questions.size()).sum();
        }
        return questions.size();
    }

    /** Total declared marks. 0 if total_marks key is absent or non-numeric. */
    public int totalMarks() {
        String v = metadata.getOrDefault("total_marks", "0");
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "ExamNode{title=\"" + title() + "\""
               + ", sections=" + sections.size()
               + ", questions=" + questionCount() + "}";
    }
}
