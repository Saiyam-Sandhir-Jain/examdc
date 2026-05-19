package com.examd.compiler.validator.rules;

import com.examd.compiler.ast.AstNode;
import com.examd.compiler.validator.ValidationContext;

/**
 * ValidationRule — the contract for a single, focused semantic check.
 *
 * ═══════════════════════════════════════════════════════════════
 *  RULE-BASED ARCHITECTURE — WHY IT MATTERS
 * ═══════════════════════════════════════════════════════════════
 *
 * We could put all validation logic in one big SemanticValidator class:
 *
 *   class SemanticValidator {
 *       void visitQuestion(QuestionNode q) {
 *           if (q.stem == null) emit error...
 *           if (q.interact == null) emit error...
 *           if (q.evaluate == null) emit error...
 *           if (q.marks() is not numeric) emit error...
 *           if (q.marks() <= 0) emit error...
 *           // 20 more checks...
 *       }
 *   }
 *
 * This gets unmaintainable fast. Each rule becomes one line in a wall of code.
 * Adding a new rule means editing a large class. Disabling a rule for a specific
 * exam type requires conditional logic everywhere.
 *
 * WITH the ValidationRule interface:
 *
 *   class R002_StemRequired implements ValidationRule<QuestionNode> { ... }
 *   class R001_MarksPositive  implements ValidationRule<QuestionNode> { ... }
 *
 * Each rule is:
 *   - One file, one concern
 *   - Independently testable
 *   - Easy to enable/disable per exam profile
 *   - Easy to document (the class name IS the rule name)
 *
 * The SemanticValidator just loops over registered rules and fires them.
 *
 * ═══════════════════════════════════════════════════════════════
 *  GENERIC TYPE PARAMETER T
 * ═══════════════════════════════════════════════════════════════
 *
 * ValidationRule<T extends AstNode> means each rule declares which node
 * type it applies to via nodeType(). The SemanticValidator uses this
 * to only fire the rule on matching nodes:
 *
 *   for (ValidationRule<?> rule : rules) {
 *       if (rule.nodeType().isInstance(node)) {
 *           ((ValidationRule<AstNode>) rule).validate(node, ctx);
 *       }
 *   }
 *
 * This avoids every rule having to cast from AstNode to its specific type.
 *
 * @param <T> The AST node type this rule validates.
 */
public interface ValidationRule<T extends AstNode> {

    /**
     * Returns the node type this rule applies to.
     * The SemanticValidator only invokes validate() when the current node
     * is an instance of this class.
     *
     * Example: return QuestionNode.class;
     */
    Class<T> nodeType();

    /**
     * Returns the stable rule identifier, e.g. "R002".
     * Used in log output and for rule-level enable/disable configuration.
     */
    String ruleId();

    /**
     * Returns a one-line description of what this rule checks.
     * Used in documentation and verbose diagnostic output.
     */
    String description();

    /**
     * Runs the rule against the given node.
     * Any violations are added to ctx.diagnostics — this method never throws.
     *
     * @param node The AST node to validate (guaranteed to be instanceof T).
     * @param ctx  The shared validation context (diagnostics, symbolTable, exam).
     */
    void validate(T node, ValidationContext ctx);
}
