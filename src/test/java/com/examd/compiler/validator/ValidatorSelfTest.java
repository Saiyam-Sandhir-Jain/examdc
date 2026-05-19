package com.examd.compiler.validator;

import com.examd.compiler.ast.ExamNode;
import com.examd.compiler.diagnostics.*;
import com.examd.compiler.lexer.Lexer;
import com.examd.compiler.parser.Parser;

import java.util.List;

/**
 * ValidatorSelfTest — Day 4 validation suite (no JUnit required).
 * Tests all 8 rules: R001–R008, plus SymbolTable and two-pass behaviour.
 */
public class ValidatorSelfTest {
    static int pass = 0, fail = 0;

    // ── Test plumbing ─────────────────────────────────────────────────────

    static DiagnosticCollector validate(String source) {
        DiagnosticCollector dc = new DiagnosticCollector();
        var tokens = new Lexer("t.examd", source, dc).tokenize();
        ExamNode exam = new Parser(tokens, dc).parse();
        if (exam != null) new SemanticValidator().validate(exam, dc);
        return dc;
    }

    static boolean hasError(DiagnosticCollector dc, String code) {
        return dc.getErrors().stream().anyMatch(e -> e.code.equals(code));
    }

    static boolean hasWarning(DiagnosticCollector dc, String code) {
        return dc.getWarnings().stream().anyMatch(w -> w.code.equals(code));
    }

    // ── Canonical complete question template ──────────────────────────────

    static String completeQ(String id, String marks) {
        return "[Q: " + id + "]\nmarks: " + marks + "\n" +
               "[STEM]\ntype: text\ncontent: Q" + id + "?\n" +
               "[INTERACT]\ntype: mcq\noptions:\n- A: Yes\n- B: No\n" +
               "[EVALUATE]\ntype: exact\nanswer: A\n\n";
    }

    static String exam(String body) {
        return "[EXAM]\ntitle: Test Exam\ntotal_marks: 0\n\n" + body;
    }

    static String examWithTotal(int total, String body) {
        return "[EXAM]\ntitle: Test Exam\ntotal_marks: " + total + "\n\n" + body;
    }

    // ═════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.out.println("════════════════════════════════════════");
        System.out.println(" ExamdC — Day 4 Validator Self-Test");
        System.out.println("════════════════════════════════════════");

        // ── R001: marks positive ──────────────────────────────────────────
        section("R001 — marks must be a positive integer");

        var dc = validate(exam(completeQ("1", "4")));
        check("R001: valid marks=4 → no E011", !hasError(dc, "E011"));

        dc = validate(exam(
            "[Q: 1]\n[STEM]\ntype: text\ncontent: Q?\n[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n"));
        check("R001: missing marks key → E011", hasError(dc, "E011"));

        dc = validate(exam(
            "[Q: 1]\nmarks: abc\n[STEM]\ntype: text\ncontent: Q?\n[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n"));
        check("R001: non-numeric marks → E011", hasError(dc, "E011"));

        dc = validate(exam(
            "[Q: 1]\nmarks: -5\n[STEM]\ntype: text\ncontent: Q?\n[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n"));
        check("R001: negative marks → E011", hasError(dc, "E011"));

        dc = validate(exam(
            "[Q: 1]\nmarks: 0\n[STEM]\ntype: text\ncontent: Q?\n[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n"));
        check("R001: marks=0 → W002 warning (not error)", !hasError(dc, "E011") && hasWarning(dc, "W002"));

        // ── R002: stem required ───────────────────────────────────────────
        section("R002 — [STEM] required in every question");

        dc = validate(exam(completeQ("1", "4")));
        check("R002: has stem → no E012", !hasError(dc, "E012"));

        dc = validate(exam(
            "[Q: 1]\nmarks: 4\n[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n"));
        check("R002: missing stem → E012", hasError(dc, "E012"));

        // ── R003: interact required ───────────────────────────────────────
        section("R003 — [INTERACT] required in every question");

        dc = validate(exam(completeQ("1", "4")));
        check("R003: has interact → no E013", !hasError(dc, "E013"));

        dc = validate(exam(
            "[Q: 1]\nmarks: 4\n[STEM]\ntype: text\ncontent: Q?\n[EVALUATE]\ntype: exact\nanswer: A\n"));
        check("R003: missing interact → E013", hasError(dc, "E013"));

        // ── R004: evaluate required ───────────────────────────────────────
        section("R004 — [EVALUATE] required in every question");

        dc = validate(exam(completeQ("1", "4")));
        check("R004: has evaluate → no E014", !hasError(dc, "E014"));

        dc = validate(exam(
            "[Q: 1]\nmarks: 4\n[STEM]\ntype: text\ncontent: Q?\n[INTERACT]\ntype: fib\n"));
        check("R004: missing evaluate → E014", hasError(dc, "E014"));

        // ── R005: answer not empty ────────────────────────────────────────
        section("R005 — auto-graded evaluate must have an answer");

        dc = validate(exam(completeQ("1", "4")));
        check("R005: has answer → no E015", !hasError(dc, "E015"));

        dc = validate(exam(
            "[Q: 1]\nmarks: 4\n[STEM]\ntype: text\ncontent: Q?\n" +
            "[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\n"));
        check("R005: exact type with no answer → E015", hasError(dc, "E015"));

        dc = validate(exam(
            "[Q: 1]\nmarks: 4\n[STEM]\ntype: text\ncontent: Q?\n" +
            "[INTERACT]\ntype: essay\n[EVALUATE]\ntype: manual\n"));
        check("R005: manual type with no answer → no E015", !hasError(dc, "E015"));

        // ── R006: context_ref resolvable ──────────────────────────────────
        section("R006 — context_ref must resolve to a declared [CONTEXT]");

        String withContext =
            "[EXAM]\ntitle: T\n\n" +
            "[CONTEXT: RC1]\ntype: passage\ncontent: A passage.\n\n" +
            "[Q: 1]\nmarks: 4\ncontext_ref: RC1\n" +
            "[STEM]\ntype: text\ncontent: Q?\n" +
            "[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n";
        dc = validate(withContext);
        check("R006: declared context_ref → no E016", !hasError(dc, "E016"));

        String missingContext =
            "[EXAM]\ntitle: T\n\n" +
            "[Q: 1]\nmarks: 4\ncontext_ref: MISSING\n" +
            "[STEM]\ntype: text\ncontent: Q?\n" +
            "[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n";
        dc = validate(missingContext);
        check("R006: undeclared context_ref → E016", hasError(dc, "E016"));
        check("R006: E016 suggestion is present", dc.getErrors().stream()
            .filter(e -> e.code.equals("E016"))
            .anyMatch(Diagnostic::hasSuggestion));

        // ── R006: two-pass — ref before declaration ───────────────────────
        section("R006 — two-pass: ref before declaration (forward reference)");

        String forwardRef =
            "[EXAM]\ntitle: T\n\n" +
            "[Q: 1]\nmarks: 4\ncontext_ref: RC1\n" +     // ref on line 4
            "[STEM]\ntype: text\ncontent: Q?\n" +
            "[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: x\n\n" +
            "[CONTEXT: RC1]\ntype: passage\ncontent: Declared after Q1.\n";  // decl later
        dc = validate(forwardRef);
        check("R006: forward ref resolved correctly → no E016", !hasError(dc, "E016"));

        // ── R007: unique question IDs ─────────────────────────────────────
        section("R007 — question IDs must be unique within scope");

        dc = validate(exam(completeQ("1", "4") + completeQ("2", "4")));
        check("R007: distinct IDs → no E017", !hasError(dc, "E017"));

        String dupQ =
            "[EXAM]\ntitle: T\n\n" +
            completeQ("1", "4") + completeQ("1", "4");
        dc = validate(dupQ);
        check("R007: duplicate Q:1 → E017", hasError(dc, "E017"));

        // ── R007: IDs unique per section (same ID in different sections OK) ─
        String sameIdDiffSection =
            "[EXAM]\ntitle: T\n\n" +
            "[SECTION: A]\ntitle: A\n\n" + completeQ("1", "4") +
            "[SECTION: B]\ntitle: B\n\n" + completeQ("1", "4");
        dc = validate(sameIdDiffSection);
        check("R007: same Q:1 in different sections → no E017", !hasError(dc, "E017"));

        // ── R008: total_marks match ───────────────────────────────────────
        section("R008 — total_marks should match sum of question marks");

        // Exact match: total_marks: 8, two questions of 4 each
        dc = validate(examWithTotal(8, completeQ("1", "4") + completeQ("2", "4")));
        check("R008: total_marks matches sum → no W003", !hasWarning(dc, "W003"));

        // Mismatch: declared 100, actual 8
        dc = validate(examWithTotal(100, completeQ("1", "4") + completeQ("2", "4")));
        check("R008: total_marks mismatch → W003", hasWarning(dc, "W003"));
        check("R008: W003 suggestion is present", dc.getWarnings().stream()
            .filter(w -> w.code.equals("W003"))
            .anyMatch(Diagnostic::hasSuggestion));

        // No total_marks declared — rule should stay silent
        dc = validate("[EXAM]\ntitle: T\n\n" + completeQ("1", "4"));
        check("R008: no total_marks declared → no W003", !hasWarning(dc, "W003"));

        // ── Clean exam → zero diagnostics ─────────────────────────────────
        section("Clean exam produces zero diagnostics");

        String clean =
            "[EXAM]\ntitle: Physics Mid-Term\nduration: 90min\ntotal_marks: 8\n\n" +
            "[SECTION: A]\ntitle: MCQ\n\n" +
            completeQ("1", "4") + completeQ("2", "4");
        dc = validate(clean);
        check("Clean exam: no errors",   !dc.hasErrors());
        check("Clean exam: no warnings", dc.getWarnings().isEmpty());
        check("Clean exam: empty diagnostics", dc.isEmpty());

        // ── SymbolTable ───────────────────────────────────────────────────
        section("SymbolTable unit tests");

        SymbolTable st = new SymbolTable();
        var s1 = com.examd.compiler.diagnostics.Span.point("t.examd", 1, 1);
        var s2 = com.examd.compiler.diagnostics.Span.point("t.examd", 5, 1);

        check("ST: first context registration → null (new)", st.registerContext("RC1", s1) == null);
        check("ST: duplicate context → returns prior span", st.registerContext("RC1", s2) == s1);
        check("ST: isContextDeclared RC1 → true",  st.isContextDeclared("RC1"));
        check("ST: isContextDeclared RC2 → false", !st.isContextDeclared("RC2"));
        check("ST: contextSpan RC1 → s1", st.contextSpan("RC1") == s1);
        check("ST: allContextIds contains RC1", st.allContextIds().contains("RC1"));

        check("ST: first Q registration → null", st.registerQuestion("A", "1", s1) == null);
        check("ST: duplicate Q same section → prior span", st.registerQuestion("A", "1", s2) == s1);
        check("ST: Q1 in section B → null (different scope)", st.registerQuestion("B", "1", s1) == null);

        // ── SemanticValidator.describeRules() ─────────────────────────────
        section("SemanticValidator infrastructure");

        SemanticValidator sv = new SemanticValidator();
        String rules = sv.describeRules();
        check("describeRules lists R001", rules.contains("R001"));
        check("describeRules lists R008", rules.contains("R008"));

        // ── Results ───────────────────────────────────────────────────────
        System.out.println("\n════════════════════════════════════════");
        System.out.println(" Results: " + pass + " passed, " + fail + " failed");
        System.out.println("════════════════════════════════════════");
        System.exit(fail > 0 ? 1 : 0);
    }

    static void section(String name) {
        System.out.println("\n── " + name + " " + "─".repeat(Math.max(0, 38 - name.length())));
    }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); pass++; }
        else       { System.out.println("  ✗ FAIL: " + name); fail++; }
    }
}
