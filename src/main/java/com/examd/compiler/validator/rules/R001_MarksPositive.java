package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * R001 — marks must be a positive integer.
 *
 * Checks: every [Q: N] must have a numeric marks value greater than 0.
 *
 * E011: marks key absent                → "Question Q:1 has no marks key"
 * E011: marks value is non-numeric      → "marks value 'abc' is not a number"
 * W002: marks value is 0               → warning (unusual but valid for ungraded Qs)
 */
public final class R001_MarksPositive implements ValidationRule<QuestionNode> {

    @Override public Class<QuestionNode> nodeType()     { return QuestionNode.class; }
    @Override public String ruleId()                    { return "R001"; }
    @Override public String description()               { return "marks must be a positive integer on every question"; }

    @Override
    public void validate(QuestionNode node, ValidationContext ctx) {
        String marksStr = node.metadata.get("marks");

        if (marksStr == null || marksStr.isBlank()) {
            ctx.diagnostics.error("E011",
                "Question [Q: " + node.id + "] has no 'marks' key",
                node.getSpan(),
                "Add 'marks: 4' (or the appropriate value) inside the [Q: " + node.id + "] block");
            return;
        }

        int marks;
        try {
            marks = Integer.parseInt(marksStr.trim());
        } catch (NumberFormatException e) {
            ctx.diagnostics.error("E011",
                "marks value '" + marksStr + "' in [Q: " + node.id + "] is not an integer",
                node.getSpan(),
                "Use a whole number, e.g. 'marks: 4'");
            return;
        }

        if (marks < 0) {
            ctx.diagnostics.error("E011",
                "marks value " + marks + " in [Q: " + node.id + "] is negative",
                node.getSpan(),
                "marks must be 0 or a positive integer");
        } else if (marks == 0) {
            ctx.diagnostics.warning("W002",
                "marks is 0 in [Q: " + node.id + "] — is this intentional?",
                node.getSpan(),
                "Set marks to a positive integer, or remove this question if ungraded");
        }
    }
}
