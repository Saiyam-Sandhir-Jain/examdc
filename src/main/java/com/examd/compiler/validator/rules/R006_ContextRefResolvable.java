package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.QuestionNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * R006 — context_ref must resolve to a declared [CONTEXT: id] block.
 *
 * This is the canonical example of a rule that REQUIRES the two-pass approach.
 * The context_ref may appear before the [CONTEXT] declaration in the source,
 * so we can only check it after Pass 1 has fully populated the SymbolTable.
 *
 * E016: context_ref 'RC1' not declared anywhere in the exam
 * Suggestion: lists all declared context IDs as candidates.
 */
public final class R006_ContextRefResolvable implements ValidationRule<QuestionNode> {

    @Override public Class<QuestionNode> nodeType() { return QuestionNode.class; }
    @Override public String ruleId()                { return "R006"; }
    @Override public String description()           { return "context_ref must resolve to a declared [CONTEXT: id]"; }

    @Override
    public void validate(QuestionNode node, ValidationContext ctx) {
        String ref = node.metadata.get("context_ref");
        if (ref == null || ref.isBlank()) return;   // no ref — nothing to check

        if (!ctx.symbolTable.isContextDeclared(ref)) {
            String declared = ctx.symbolTable.allContextIds().toString();
            String suggestion = declared.equals("[]")
                ? "No [CONTEXT] blocks have been declared in this exam"
                : "Declared context IDs: " + declared;

            ctx.diagnostics.error("E016",
                "context_ref '" + ref + "' in [Q: " + node.id + "] is not declared",
                node.getSpan(),
                suggestion);
        }
    }
}
