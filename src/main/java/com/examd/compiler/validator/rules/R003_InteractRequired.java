package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * R003 — every [Q: N] must have an [INTERACT] block.
 *
 * The interact block defines what the student does (selects, types, draws…).
 * Without it, the question renderer does not know what widget to show.
 *
 * E013: [INTERACT] block missing
 */
public final class R003_InteractRequired implements ValidationRule<QuestionNode> {

    @Override public Class<QuestionNode> nodeType() { return QuestionNode.class; }
    @Override public String ruleId()                { return "R003"; }
    @Override public String description()           { return "[INTERACT] block is required in every question"; }

    @Override
    public void validate(QuestionNode node, ValidationContext ctx) {
        if (node.interact == null) {
            ctx.diagnostics.error("E013",
                "[Q: " + node.id + "] is missing an [INTERACT] block",
                node.getSpan(),
                "Add an [INTERACT] block with at least 'type: mcq' (or fib, essay, etc.)");
        }
    }
}
