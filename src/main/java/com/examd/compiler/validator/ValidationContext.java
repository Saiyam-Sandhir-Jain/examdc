package com.examd.compiler.validator;

import com.examd.compiler.ast.ExamNode;
import com.examd.compiler.diagnostics.DiagnosticCollector;

/**
 * ValidationContext — the "world" a ValidationRule sees when it fires.
 *
 * WHY THIS EXISTS:
 * Every rule needs access to at least two things:
 *   1. The DiagnosticCollector — to emit errors and warnings
 *   2. The SymbolTable — to look up declared IDs (context refs, question IDs)
 *
 * Some rules also need the global ExamNode (e.g. the total_marks sum rule
 * needs to know the declared total). Rather than passing all three as
 * separate arguments to every rule method, we bundle them in one object.
 *
 * This also makes it easy to add more context later (e.g. CompilerOptions,
 * a cache of previously compiled blocks) without changing every rule's signature.
 *
 * IMMUTABILITY:
 * The context object itself is immutable — it just holds references to mutable
 * objects (DiagnosticCollector and SymbolTable). Rules add to the collector
 * and the symbol table through those references, not by replacing them.
 */
public final class ValidationContext {

    /** Shared diagnostic accumulator — rules call dc.error() / dc.warning(). */
    public final DiagnosticCollector diagnostics;

    /**
     * Symbol table populated in the first pass of SemanticValidator.
     * Rules use it to resolve references and detect duplicates.
     */
    public final SymbolTable symbolTable;

    /**
     * The root ExamNode — available to rules that need global information
     * (e.g. declared total_marks, exam-level contexts).
     */
    public final ExamNode exam;

    public ValidationContext(DiagnosticCollector diagnostics,
                             SymbolTable symbolTable,
                             ExamNode exam) {
        this.diagnostics = diagnostics;
        this.symbolTable = symbolTable;
        this.exam        = exam;
    }
}
