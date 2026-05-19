package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * R004 — every [Q: N] must have an [EVALUATE] block.
 *
 * The evaluate block holds the answer key and scoring config.
 * Without it, the runtime cannot check answers or award marks.
 * Manual-grading questions are not an exception — they still need
 * [EVALUATE] with 'type: manual' to signal intent to the runtime.
 *
 * E014: [EVALUATE] block missing
 */
public final class R004_EvaluateRequired implements ValidationRule<QuestionNode> {

    @Override public Class<QuestionNode> nodeType() { return QuestionNode.class; }
    @Override public String ruleId()                { return "R004"; }
    @Override public String description()           { return "[EVALUATE] block is required in every question"; }

    @Override
    public void validate(QuestionNode node, ValidationContext ctx) {
        if (node.evaluate == null) {
            ctx.diagnostics.error("E014",
                "[Q: " + node.id + "] is missing an [EVALUATE] block",
                node.getSpan(),
                "Add an [EVALUATE] block with 'type: exact' and 'answer: <value>'");
        }
    }
}
