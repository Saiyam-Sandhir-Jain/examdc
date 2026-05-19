package com.examd.compiler.validator;

import com.examd.compiler.diagnostics.Span;
import java.util.*;

/**
 * SymbolTable — tracks every declared identifier in the exam and detects duplicates.
 *
 * ═══════════════════════════════════════════════════════════════
 *  WHAT IS A SYMBOL TABLE?
 * ═══════════════════════════════════════════════════════════════
 *
 * In general-purpose language compilers, the symbol table maps variable
 * names to their types, scope, and memory locations:
 *
 *   "x" → {type: int, scope: method_foo, address: 0x40}
 *   "y" → {type: String, scope: class_Bar, address: 0x80}
 *
 * In ExamdC, we don't have variables — we have named identifiers:
 *   - Question IDs:    "1", "2", "3a" — must be unique within a section
 *   - Section IDs:     "A", "B", "C"  — must be unique within the exam
 *   - Context IDs:     "RC1", "data1" — declared at any scope level
 *   - context_ref values — must resolve to a declared Context ID
 *
 * The symbol table answers three questions:
 *   1. Has this ID been declared before? (duplicate detection)
 *   2. Has this context ID been declared? (reference resolution)
 *   3. Where was it first declared? (for pointing at the original in errors)
 *
 * ═══════════════════════════════════════════════════════════════
 *  TWO-PASS VALIDATION
 * ═══════════════════════════════════════════════════════════════
 *
 * Context references can appear before declarations in the source:
 *
 *   [Q: 1]
 *   context_ref: RC1        ← referenced HERE
 *   ...
 *
 *   [CONTEXT: RC1]          ← but declared later
 *   ...
 *
 * To handle this, SemanticValidator runs in two passes:
 *
 *   Pass 1 — COLLECT: walk the entire tree, calling symbolTable.register*()
 *             for every declaration. No rule firing.
 *
 *   Pass 2 — VALIDATE: walk again, fire all rules. By now the symbol table
 *             is fully populated, so any reference can be resolved.
 *
 * The SymbolTable is built during Pass 1 and queried during Pass 2.
 */
public final class SymbolTable {

    // ── Declared symbols ──────────────────────────────────────────────────

    /**
     * Maps context ID → the Span of its [CONTEXT: id] declaration.
     * Used in Pass 1 (register) and Pass 2 (resolve references).
     */
    private final Map<String, Span> contextDeclarations = new LinkedHashMap<>();

    /**
     * Maps "sectionId::questionId" → Span of the [Q: id] declaration.
     * The scope prefix prevents [Q: 1] in Section A conflicting with [Q: 1] in Section B.
     */
    private final Map<String, Span> questionDeclarations = new LinkedHashMap<>();

    /**
     * Maps section ID → Span of the [SECTION: id] declaration.
     */
    private final Map<String, Span> sectionDeclarations = new LinkedHashMap<>();

    // ── Pass 1 — Registration ─────────────────────────────────────────────

    /**
     * Registers a context declaration.
     *
     * @return The Span of the PREVIOUS declaration if this is a duplicate, or null if new.
     */
    public Span registerContext(String id, Span declaredAt) {
        return contextDeclarations.putIfAbsent(id, declaredAt);
    }

    /**
     * Registers a question within a section scope.
     * Scope key: "sectionId::questionId"  (or "::questionId" for flat exams)
     *
     * @return The Span of the previous declaration if duplicate, or null if new.
     */
    public Span registerQuestion(String sectionId, String questionId, Span declaredAt) {
        String key = sectionId + "::" + questionId;
        return questionDeclarations.putIfAbsent(key, declaredAt);
    }

    /**
     * Registers a section declaration.
     *
     * @return The Span of the previous declaration if duplicate, or null if new.
     */
    public Span registerSection(String sectionId, Span declaredAt) {
        return sectionDeclarations.putIfAbsent(sectionId, declaredAt);
    }

    // ── Pass 2 — Resolution ───────────────────────────────────────────────

    /**
     * Returns true if a [CONTEXT: id] with the given ID has been declared anywhere
     * in the exam (at exam scope, section scope, or question scope).
     */
    public boolean isContextDeclared(String id) {
        return contextDeclarations.containsKey(id);
    }

    /**
     * Returns the Span of a context declaration, or null if not declared.
     * Used to point at the original declaration in "did you mean?" messages.
     */
    public Span contextSpan(String id) {
        return contextDeclarations.get(id);
    }

    /** Returns all declared context IDs — useful for "did you mean?" suggestions. */
    public Set<String> allContextIds() {
        return Collections.unmodifiableSet(contextDeclarations.keySet());
    }

    /** Returns all declared section IDs. */
    public Set<String> allSectionIds() {
        return Collections.unmodifiableSet(sectionDeclarations.keySet());
    }

    // ── Debug ─────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "SymbolTable{contexts=" + contextDeclarations.keySet()
               + ", sections=" + sectionDeclarations.keySet()
               + ", questions=" + questionDeclarations.size() + "}";
    }
}
