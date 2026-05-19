package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.ExamNode;
import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.ast.SectionNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * R008 — declared total_marks should equal the computed sum of all question marks.
 *
 * This rule fires on the ExamNode (root) because it requires global knowledge
 * of every question's marks. It cannot fire on individual questions.
 *
 * If total_marks is not declared, this rule does nothing (it's optional).
 *
 * W003: total_marks mismatch — declared 100, computed 95
 *
 * WHY A WARNING, NOT AN ERROR?
 * An author might intentionally declare total_marks: 100 even if the
 * individual questions only add up to 95 (leaving 5 marks for bonus questions
 * added later, or for a curve). It's suspicious but not necessarily wrong.
 * A warning prompts review without blocking compilation.
 */
public final class R008_TotalMarksMatch implements ValidationRule<ExamNode> {

    @Override public Class<ExamNode> nodeType() { return ExamNode.class; }
    @Override public String ruleId()            { return "R008"; }
    @Override public String description()       { return "declared total_marks should equal the sum of all question marks"; }

    @Override
    public void validate(ExamNode node, ValidationContext ctx) {
        String declaredStr = node.metadata.get("total_marks");
        if (declaredStr == null || declaredStr.isBlank()) return;   // not declared — skip

        int declared;
        try {
            declared = Integer.parseInt(declaredStr.trim());
        } catch (NumberFormatException e) {
            // Non-numeric total_marks is caught by a different rule (future R009)
            return;
        }

        int computed = computeTotal(node);
        if (computed == 0) return;   // all marks missing/invalid — R001 already reported those

        if (declared != computed) {
            ctx.diagnostics.warning("W003",
                "total_marks is declared as " + declared
                    + " but the sum of all question marks is " + computed,
                node.getSpan(),
                "Update 'total_marks: " + computed + "' to match, or adjust individual question marks");
        }
    }

    /** Sums marks across all questions — works for both flat and sectioned exams. */
    private int computeTotal(ExamNode exam) {
        if (exam.isSectioned()) {
            return exam.sections.stream()
                .mapToInt(SectionNode::totalMarks)
                .sum();
        }
        return exam.questions.stream()
            .mapToInt(q -> parseMark(q))
            .sum();
    }

    private int parseMark(QuestionNode q) {
        try { return Integer.parseInt(q.marks().trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
