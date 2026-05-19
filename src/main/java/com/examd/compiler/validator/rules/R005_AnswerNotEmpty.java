package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.EvaluateNode;
import com.examd.compiler.validator.ValidationContext;
import java.util.Set;

/**
 * R005 — answer must be non-empty for auto-graded types.
 *
 * Types that require an answer: exact, range, partial, msq.
 * Types that do NOT require an answer: manual, custom.
 *
 * WHY THIS IS A SEPARATE RULE FROM R004:
 * R004 checks that the [EVALUATE] block exists at all.
 * R005 checks that the block's content is meaningful.
 * Separate concerns → separate rules → easier to understand failures.
 *
 * E015: auto-graded question has no answer
 */
public final class R005_AnswerNotEmpty implements ValidationRule<EvaluateNode> {

    /** Evaluate types that are auto-graded and require an answer key. */
    private static final Set<String> AUTO_GRADED = Set.of(
        "exact", "range", "partial", "msq"
    );

    /** Types that are intentionally answer-free. */
    private static final Set<String> MANUAL_TYPES = Set.of(
        "manual", "custom"
    );

    @Override public Class<EvaluateNode> nodeType() { return EvaluateNode.class; }
    @Override public String ruleId()                { return "R005"; }
    @Override public String description()           { return "auto-graded [EVALUATE] blocks must have a non-empty answer"; }

    @Override
    public void validate(EvaluateNode node, ValidationContext ctx) {
        // Unknown type → let a different rule (R006) handle it
        if (node.type.isBlank()) return;
        if (MANUAL_TYPES.contains(node.type.toLowerCase())) return;

        if (AUTO_GRADED.contains(node.type.toLowerCase()) && node.answers.isEmpty()) {
            ctx.diagnostics.error("E015",
                "[EVALUATE] type '" + node.type + "' requires an 'answer' key but none was found",
                node.getSpan(),
                "Add 'answer: <value>' (or a list for multi-answer types)");
        }
    }
}
