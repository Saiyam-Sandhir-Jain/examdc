package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * R002 — every [Q: N] must have a [STEM] block.
 *
 * The stem is what the student reads/sees. Without it, the question
 * has no body and the generated exam would show a blank question slot.
 *
 * E012: [STEM] block missing
 */
public final class R002_StemRequired implements ValidationRule<QuestionNode> {

    @Override public Class<QuestionNode> nodeType() { return QuestionNode.class; }
    @Override public String ruleId()                { return "R002"; }
    @Override public String description()           { return "[STEM] block is required in every question"; }

    @Override
    public void validate(QuestionNode node, ValidationContext ctx) {
        if (node.stem == null) {
            ctx.diagnostics.error("E012",
                "[Q: " + node.id + "] is missing a [STEM] block",
                node.getSpan(),
                "Add a [STEM] block with at least 'type: text' and 'content: <question text>'");
        }
    }
}
