package com.examd.compiler.parser;

import com.examd.compiler.ast.*;
import com.examd.compiler.diagnostics.*;
import com.examd.compiler.lexer.*;
import java.util.*;

public class ParserSelfTest {
    static int pass = 0, fail = 0;

    static ExamNode parse(String source) {
        DiagnosticCollector dc = new DiagnosticCollector();
        List<Token> tokens = new Lexer("t.examd", source, dc).tokenize();
        return new Parser(tokens, dc).parse();
    }

    static ExamNode parseClean(String source) {
        DiagnosticCollector dc = new DiagnosticCollector();
        List<Token> tokens = new Lexer("t.examd", source, dc).tokenize();
        ExamNode exam = new Parser(tokens, dc).parse();
        if (dc.hasErrors()) { fail("Clean parse had errors: " + dc.renderCompact()); }
        return exam;
    }

    static DiagnosticCollector parseGetDiag(String source) {
        DiagnosticCollector dc = new DiagnosticCollector();
        List<Token> tokens = new Lexer("t.examd", source, dc).tokenize();
        new Parser(tokens, dc).parse();
        return dc;
    }

    public static void main(String[] args) {
        System.out.println("════════════════════════════════════════");
        System.out.println(" ExamdC — Parser Self-Test Suite");
        System.out.println("════════════════════════════════════════\n");

        section("EXAM NODE — ROOT PARSING");
        String minExam = "[EXAM]\ntitle: Physics Test\nduration: 30min\n";
        ExamNode e = parseClean(minExam);
        check("ExamNode not null", e != null);
        check("title metadata", e != null && e.title().equals("Physics Test"));
        check("duration metadata", e != null && e.duration().equals("30min"));
        check("empty sections (flat)", e != null && e.sections.isEmpty());
        check("empty questions (no Q blocks)", e != null && e.questions.isEmpty());

        section("FLAT EXAM — DIRECT QUESTIONS");
        String flatExam =
            "[EXAM]\ntitle: Quick Quiz\n\n" +
            "[Q: 1]\nmarks: 4\n" +
            "[STEM]\ntype: text\ncontent: What is gravity?\n" +
            "[INTERACT]\ntype: mcq\noptions:\n- A: 9.8\n- B: 10.0\n" +
            "[EVALUATE]\ntype: exact\nanswer: A\n\n" +
            "[Q: 2]\nmarks: 2\n" +
            "[STEM]\ntype: text\ncontent: Speed of light?\n" +
            "[INTERACT]\ntype: fib\n" +
            "[EVALUATE]\ntype: exact\nanswer: 3e8\n";
        ExamNode flat = parseClean(flatExam);
        check("Flat: 2 questions", flat != null && flat.questions.size() == 2);
        check("Flat: isFlat()", flat != null && flat.isFlat());
        check("Q1 id=1", flat != null && flat.questions.get(0).id.equals("1"));
        check("Q1 marks=4", flat != null && flat.questions.get(0).marks().equals("4"));
        check("Q1 complete", flat != null && flat.questions.get(0).isComplete());
        check("Q1 stem not null", flat != null && flat.questions.get(0).stem != null);
        check("Q1 stem type=text", flat != null && flat.questions.get(0).stem.type.equals("text"));
        check("Q1 stem content", flat != null && flat.questions.get(0).stem.content.equals("What is gravity?"));
        check("Q1 interact type=mcq", flat != null && flat.questions.get(0).interact.type.equals("mcq"));
        check("Q1 options size=2", flat != null && flat.questions.get(0).interact.options.size() == 2);
        check("Q1 option A", flat != null && flat.questions.get(0).interact.options.get(0).equals("A: 9.8"));
        check("Q1 evaluate type=exact", flat != null && flat.questions.get(0).evaluate.type.equals("exact"));
        check("Q1 answer=A", flat != null && flat.questions.get(0).evaluate.primaryAnswer().equals("A"));
        check("Q2 id=2", flat != null && flat.questions.get(1).id.equals("2"));

        section("SECTIONED EXAM");
        String sectExam =
            "[EXAM]\ntitle: Mid-Term\ntotal_marks: 50\n\n" +
            "[SECTION: A]\ntitle: MCQ\n\n" +
            "[Q: 1]\nmarks: 4\n" +
            "[STEM]\ntype: text\ncontent: Q1?\n" +
            "[INTERACT]\ntype: mcq\noptions:\n- A: Yes\n- B: No\n" +
            "[EVALUATE]\ntype: exact\nanswer: A\n\n" +
            "[SECTION: B]\ntitle: Short Answer\n\n" +
            "[Q: 2]\nmarks: 6\n" +
            "[STEM]\ntype: text\ncontent: Explain gravity.\n" +
            "[INTERACT]\ntype: essay\n" +
            "[EVALUATE]\ntype: manual\n";
        ExamNode sect = parseClean(sectExam);
        check("Sectioned: 2 sections", sect != null && sect.sections.size() == 2);
        check("Sectioned: isSectioned()", sect != null && sect.isSectioned());
        check("Section A id", sect != null && sect.sections.get(0).id.equals("A"));
        check("Section B id", sect != null && sect.sections.get(1).id.equals("B"));
        check("Section A: 1 question", sect != null && sect.sections.get(0).questions.size() == 1);
        check("Section B: 1 question", sect != null && sect.sections.get(1).questions.size() == 1);
        check("total_marks metadata", sect != null && sect.totalMarks() == 50);
        check("questionCount()=2", sect != null && sect.questionCount() == 2);
        check("Section A total marks", sect != null && sect.sections.get(0).totalMarks() == 4);

        section("PIPE SCALAR CONTENT");
        String pipeExam =
            "[EXAM]\ntitle: T\n\n" +
            "[Q: 1]\nmarks: 2\n" +
            "[STEM]\ntype: text\ncontent: |\n  Line one.\n  Line two.\n" +
            "[INTERACT]\ntype: fib\n" +
            "[EVALUATE]\ntype: exact\nanswer: 42\n";
        ExamNode pe = parseClean(pipeExam);
        String content = pe != null ? pe.questions.get(0).stem.content : "";
        check("Pipe content joins lines", content.contains("Line one.") && content.contains("Line two."));
        check("Pipe content has newline", content.contains("\n"));

        section("CONTEXT NODE");
        String ctxExam =
            "[EXAM]\ntitle: RC Exam\n\n" +
            "[CONTEXT: RC1]\ntype: passage\ncontent: |\n  A long passage here.\n\n" +
            "[Q: 1]\nmarks: 2\n" +
            "[STEM]\ntype: text\ncontent: Q about passage.\n" +
            "[INTERACT]\ntype: mcq\noptions:\n- A: Yes\n- B: No\n" +
            "[EVALUATE]\ntype: exact\nanswer: A\n";
        ExamNode cx = parseClean(ctxExam);
        check("Context at exam level", cx != null && cx.contexts.size() == 1);
        check("Context id=RC1", cx != null && cx.contexts.get(0).id.equals("RC1"));
        check("Context type=passage", cx != null && cx.contexts.get(0).type.equals("passage"));

        section("MULTI-ANSWER EVALUATE");
        String msqExam =
            "[EXAM]\ntitle: T\n\n" +
            "[Q: 1]\nmarks: 4\n" +
            "[STEM]\ntype: text\ncontent: Select all correct.\n" +
            "[INTERACT]\ntype: msq\noptions:\n- A: Newton\n- B: Einstein\n- C: Hawking\n" +
            "[EVALUATE]\ntype: partial\nanswer:\n- A\n- C\n";
        ExamNode msq = parseClean(msqExam);
        check("MSQ: 2 answers", msq != null && msq.questions.get(0).evaluate.answers.size() == 2);
        check("MSQ: answer A", msq != null && msq.questions.get(0).evaluate.answers.contains("A"));
        check("MSQ: answer C", msq != null && msq.questions.get(0).evaluate.answers.contains("C"));

        section("ERROR RECOVERY");
        // Missing [EXAM] header
        DiagnosticCollector dc1 = parseGetDiag("title: Test\n");
        check("E004: missing [EXAM] header", dc1.hasErrors() && dc1.getErrors().get(0).code.equals("E004"));

        // Unknown block at exam level
        DiagnosticCollector dc2 = parseGetDiag("[EXAM]\ntitle: T\n[UNKNOWN_BLOCK]\n");
        check("E004: unknown block emitted", dc2.hasErrors());

        // Duplicate STEM
        DiagnosticCollector dc3 = parseGetDiag(
            "[EXAM]\ntitle: T\n[Q: 1]\nmarks: 2\n" +
            "[STEM]\ntype: text\ncontent: First.\n" +
            "[STEM]\ntype: text\ncontent: Duplicate.\n" +
            "[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: X\n");
        check("E009: duplicate STEM", dc3.hasErrors() && dc3.getErrors().stream().anyMatch(d -> d.code.equals("E009")));

        section("SPAN ACCURACY");
        ExamNode sp = parseClean("[EXAM]\ntitle: T\n\n[Q: 1]\nmarks: 2\n[STEM]\ntype: text\ncontent: Q.\n[INTERACT]\ntype: fib\n[EVALUATE]\ntype: exact\nanswer: X\n");
        check("ExamNode span starts line 1", sp != null && sp.getSpan().lineStart == 1);
        check("QuestionNode span starts line 4", sp != null && sp.questions.get(0).getSpan().lineStart == 4);

        section("TREE DUMP");
        System.out.println("\nTree dump of sectioned exam:");
        System.out.print(Parser.dumpTree(sect));
        System.out.println("\nTree dump of null (error case):");
        System.out.print(Parser.dumpTree(null));
        check("dumpTree non-null works", sect != null && Parser.dumpTree(sect).contains("SectionNode"));
        check("dumpTree null returns message", Parser.dumpTree(null).contains("null"));

        System.out.println("\n════════════════════════════════════════");
        System.out.println(" Results: " + pass + " passed, " + fail + " failed");
        System.out.println("════════════════════════════════════════");
        System.exit(fail > 0 ? 1 : 0);
    }

    static void section(String name) {
        System.out.println("\n── " + name + " " + "─".repeat(Math.max(0, 35 - name.length())));
    }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); pass++; }
        else       { System.out.println("  ✗ FAIL: " + name); fail++; }
    }

    static void fail(String msg) { System.out.println("  ✗ FAIL: " + msg); fail++; }
}