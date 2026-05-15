package com.examd.compiler.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DiagnosticCollector — Phase 1 diagnostic system")
class DiagnosticCollectorTest {

    // ── Basic accumulation ────────────────────────────────────────────────

    @Test
    @DisplayName("Empty collector has no errors")
    void emptyCollector() {
        DiagnosticCollector dc = new DiagnosticCollector();
        assertFalse(dc.hasErrors());
        assertEquals(0, dc.errorCount());
        assertEquals(0, dc.totalCount());
        assertTrue(dc.isEmpty());
    }

    @Test
    @DisplayName("Adding an error registers it")
    void addError() {
        DiagnosticCollector dc = new DiagnosticCollector();
        Span span = Span.point("test.examd", 5, 10);
        dc.error("E001", "Unterminated string", span);

        assertTrue(dc.hasErrors());
        assertEquals(1, dc.errorCount());
        assertEquals(1, dc.totalCount());
    }

    @Test
    @DisplayName("Adding a warning does not set hasErrors")
    void warningDoesNotSetHasErrors() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.warning("W001", "Marks mismatch", Span.point("t.examd", 1, 1));

        assertFalse(dc.hasErrors());
        assertEquals(0, dc.errorCount());
        assertEquals(1, dc.totalCount());
    }

    @Test
    @DisplayName("Mixed errors and warnings are all collected")
    void mixedDiagnostics() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "Error one",   Span.point("t.examd", 1, 1));
        dc.warning("W001", "Warning",   Span.point("t.examd", 2, 1));
        dc.error("E003", "Error two",   Span.point("t.examd", 3, 1));
        dc.hint("H001", "Consider X",  Span.point("t.examd", 4, 1));

        assertEquals(2, dc.errorCount());
        assertEquals(4, dc.totalCount());
        assertEquals(2, dc.getErrors().size());
        assertEquals(1, dc.getWarnings().size());
    }

    @Test
    @DisplayName("getErrors() returns only ERROR-severity items")
    void getErrorsFiltered() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "An error", Span.point("f.examd", 1, 1));
        dc.warning("W001", "A warning", Span.point("f.examd", 2, 1));

        assertEquals(1, dc.getErrors().size());
        assertEquals("E001", dc.getErrors().get(0).code);
    }

    @Test
    @DisplayName("getAll() returns diagnostics in insertion order")
    void getAllRetainsOrder() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "First",  Span.point("f.examd", 1, 1));
        dc.error("E002", "Second", Span.point("f.examd", 2, 1));
        dc.error("E003", "Third",  Span.point("f.examd", 3, 1));

        assertEquals("E001", dc.getAll().get(0).code);
        assertEquals("E002", dc.getAll().get(1).code);
        assertEquals("E003", dc.getAll().get(2).code);
    }

    @Test
    @DisplayName("getAll() returns unmodifiable list")
    void getAllIsUnmodifiable() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "Err", Span.point("f.examd", 1, 1));

        assertThrows(UnsupportedOperationException.class, () ->
            dc.getAll().add(Diagnostic.error("E002", "injected", Span.point("f.examd", 2, 1))));
    }

    // ── Diagnostic fields ─────────────────────────────────────────────────

    @Test
    @DisplayName("Diagnostic fields are set correctly")
    void diagnosticFields() {
        Span span = new Span("exam.examd", 5, 3, 5, 20);
        Diagnostic d = Diagnostic.error("E001", "Unterminated string", span,
                                        "Close with a double-quote");
        assertEquals(DiagnosticSeverity.ERROR, d.severity);
        assertEquals("E001", d.code);
        assertEquals("Unterminated string", d.message);
        assertEquals(span, d.span);
        assertEquals("Close with a double-quote", d.suggestion);
        assertTrue(d.hasSuggestion());
        assertTrue(d.isBlocking());
    }

    @Test
    @DisplayName("Warning is not blocking")
    void warningNotBlocking() {
        Diagnostic d = Diagnostic.warning("W001", "Marks mismatch",
                                          Span.point("f.examd", 1, 1));
        assertFalse(d.isBlocking());
        assertFalse(d.hasSuggestion());
    }

    @Test
    @DisplayName("Diagnostic.toString() produces readable one-liner")
    void diagnosticToString() {
        Diagnostic d = Diagnostic.error("E001", "Unterminated string",
                                         Span.point("exam.examd", 5, 10));
        String s = d.toString();
        assertTrue(s.contains("error[E001]"));
        assertTrue(s.contains("exam.examd:5:10"));
        assertTrue(s.contains("Unterminated string"));
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("render() produces non-empty output for errors")
    void renderProducesOutput() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "Unterminated string", new Span("f.examd", 2, 9, 2, 25));

        String[] source = { "[EXAM]", "title: \"unterminated" };
        String output = dc.render(source);

        assertTrue(output.contains("error[E001]"));
        assertTrue(output.contains("f.examd:2:9"));
        assertTrue(output.contains("\"unterminated"));  // the source line
        assertTrue(output.contains("^"));               // the caret
        assertTrue(output.contains("1 error(s)"));
    }

    @Test
    @DisplayName("render() with null sourceLines skips source excerpt")
    void renderWithoutSource() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "Unterminated string", Span.point("f.examd", 2, 9));

        String output = dc.render(null);
        assertTrue(output.contains("error[E001]"));
        // No source excerpt — no "│" character
        assertFalse(output.contains("│"));
    }

    @Test
    @DisplayName("render() includes suggestion when present")
    void renderIncludesSuggestion() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.add(Diagnostic.error("E001", "Unterminated string",
                                Span.point("f.examd", 1, 1),
                                "Close the string with a double-quote"));
        String output = dc.render(null);
        assertTrue(output.contains("hint: Close the string"));
    }

    @Test
    @DisplayName("render() is empty when no diagnostics")
    void renderEmptyWhenNoDiagnostics() {
        DiagnosticCollector dc = new DiagnosticCollector();
        assertEquals("", dc.render(null));
    }

    @Test
    @DisplayName("Summary line shows error and warning counts")
    void renderSummaryLine() {
        DiagnosticCollector dc = new DiagnosticCollector();
        dc.error("E001", "Err 1", Span.point("f.examd", 1, 1));
        dc.error("E002", "Err 2", Span.point("f.examd", 2, 1));
        dc.warning("W001", "Warn", Span.point("f.examd", 3, 1));

        String output = dc.render(null);
        assertTrue(output.contains("2 error(s)"));
        assertTrue(output.contains("1 warning(s)"));
    }

    // ── DiagnosticSeverity ────────────────────────────────────────────────

    @Test
    @DisplayName("ERROR severity is blocking, others are not")
    void severityBlocking() {
        assertTrue(DiagnosticSeverity.ERROR.isBlocking());
        assertFalse(DiagnosticSeverity.WARNING.isBlocking());
        assertFalse(DiagnosticSeverity.INFO.isBlocking());
        assertFalse(DiagnosticSeverity.HINT.isBlocking());
    }

    @Test
    @DisplayName("Severity labels are lowercase strings")
    void severityLabels() {
        assertEquals("error",   DiagnosticSeverity.ERROR.label());
        assertEquals("warning", DiagnosticSeverity.WARNING.label());
        assertEquals("info",    DiagnosticSeverity.INFO.label());
        assertEquals("hint",    DiagnosticSeverity.HINT.label());
    }

    // ── Null safety ───────────────────────────────────────────────────────

    @Test
    @DisplayName("add(null) throws IllegalArgumentException")
    void addNullThrows() {
        DiagnosticCollector dc = new DiagnosticCollector();
        assertThrows(IllegalArgumentException.class, () -> dc.add(null));
    }
}