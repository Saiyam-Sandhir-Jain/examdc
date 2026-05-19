package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.diagnostics.Span;
import com.examd.compiler.validator.ValidationContext;

/**
 * R007 — question IDs must be unique within their scope (section or exam).
 *
 * This rule fires during Pass 2 (validate). By that point, Pass 1 has already
 * registered all question IDs via symbolTable.registerQuestion(). If registerQuestion()
 * returned a non-null value, the ID was already taken.
 *
 * WHY we check in Pass 2 rather than using the Pass 1 return value:
 * Pass 1 is purely a collection phase — we don't emit diagnostics there.
 * This keeps the two passes cleanly separated: collect, then validate.
 * The duplicate detection works by re-calling registerQuestion() in Pass 2
 * and checking whether it returns the prior Span.
 *
 * Scope: within a [SECTION]. Two questions in different sections may share IDs.
 *
 * E017: duplicate question ID within the same section
 */
public final class R007_UniqueQuestionIds implements ValidationRule<QuestionNode> {

    /** The section ID this rule is scoped to — set by SemanticValidator before firing. */
    private String currentSectionId = "";

    public void setCurrentSectionId(String id) { this.currentSectionId = id; }

    @Override public Class<QuestionNode> nodeType() { return QuestionNode.class; }
    @Override public String ruleId()                { return "R007"; }
    @Override public String description()           { return "question IDs must be unique within a section"; }

    @Override
    public void validate(QuestionNode node, ValidationContext ctx) {
        // Re-register — if a prior declaration exists, registerQuestion returns its Span
        Span prior = ctx.symbolTable.registerQuestion(currentSectionId, node.id, node.getSpan());
        if (prior != null && !prior.equals(node.getSpan())) {
            ctx.diagnostics.error("E017",
                "Duplicate question ID '" + node.id + "' in section '" + currentSectionId + "'",
                node.getSpan(),
                "First declared at " + prior + " — use a unique ID for each question");
        }
    }
}
