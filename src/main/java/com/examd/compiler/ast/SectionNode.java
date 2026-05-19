package com.examd.compiler.ast;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * SectionNode — a named group of questions inside the exam.
 *
 * Maps to a [SECTION: A] block and all [Q: N] blocks that follow it
 * before the next [SECTION] or end of [EXAM]:
 *
 *   [SECTION: A]
 *   title: Multiple Choice Questions
 *   marks_per_question: 4
 *
 *   [Q: 1] ... [Q: 2] ... [Q: 3] ...
 *
 * A section may also contain [CONTEXT: id] blocks that are shared
 * across all questions in the section.
 *
 * FLAT vs SECTIONED EXAMS:
 * Some exams have no [SECTION] blocks — questions sit directly under [EXAM].
 * The parser handles both cases:
 *   - If [SECTION] blocks are present, questions are attached to sections.
 *   - If no [SECTION] blocks, all questions go into ExamNode.questions directly.
 *
 * The Validator enforces that you don't mix both styles (E008).
 */
public final class SectionNode implements AstNode {

    /** The section identifier: "A" in [SECTION: A]. May be "" for unnumbered sections. */
    public final String id;

    /** Key-value metadata from the [SECTION] block itself. */
    public final Map<String, String> metadata;

    /** All questions in this section, in source order. */
    public final List<QuestionNode> questions;

    /** Context blocks declared at section scope. */
    public final List<ContextNode> contexts;

    private final Span span;

    public SectionNode(String id,
                       Map<String, String> metadata,
                       List<QuestionNode> questions,
                       List<ContextNode> contexts,
                       Span span) {
        this.id        = id != null ? id : "";
        this.metadata  = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.questions = Collections.unmodifiableList(new ArrayList<>(questions));
        this.contexts  = Collections.unmodifiableList(new ArrayList<>(contexts));
        this.span      = span;
    }

    /** Total marks across all questions in this section. */
    public int totalMarks() {
        return questions.stream()
            .mapToInt(q -> {
                try { return Integer.parseInt(q.marks()); }
                catch (NumberFormatException e) { return 0; }
            })
            .sum();
    }

    @Override public Span getSpan() { return span; }

    @Override public String toString() {
        return "SectionNode{id=" + id + ", questions=" + questions.size() + "}";
    }
}
