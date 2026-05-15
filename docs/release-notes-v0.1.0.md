## ExamdC v0.1.0 — Phase 1: Lexical Analysis

> First release of ExamdC — a production compiler for the EXAMD DSL.

This release completes **Phase 1** of a 6-phase compiler pipeline: the Lexer.

---

### What's included

**Core lexer pipeline (4 classes):**
- `Span` — immutable source location (file, line, column) carried through the entire pipeline
- `TokenType` — 9-type vocabulary enum: `BLOCK_HEADER`, `KEY`, `VALUE_SCALAR`, `VALUE_PIPE`, `INDENT_LINE`, `LIST_ITEM`, `COMMENT`, `BLANK`, `EOF`
- `Token` — immutable `(type, lexeme, span)` triple
- `Lexer` — line-by-line state machine with 3 states: `NORMAL`, `IN_PIPE`, `IN_LIST`

**Verified behaviour:**
- Block headers: `[EXAM]`, `[SECTION: A]`, `[Q: 1]`, `[STEM]`, `[INTERACT]`, `[EVALUATE]`
- Key-value pairs: bare, quoted, integer, duration values
- Pipe scalars: `content: |` with multi-line indented content → correct `INDENT_LINE` tokens
- Pipe base-indentation auto-detected from first indented line
- List items: plain (`- text`) and labeled (`- A: text`)
- Error E001: unterminated quoted string at exact span

**Test coverage:** 17 JUnit 5 tests, all passing on Java 11, 17, 21.

---

### Download

| Artifact | Description |
|----------|-------------|
| `examdc-0.1.0-standalone.jar` | Fat JAR — run with `java -jar examdc-0.1.0-standalone.jar` |
| GitHub Packages | `com.examd:examdc:0.1.0` via Maven |

---

### What's next — v0.2.0

Phase 2: Recursive descent parser — converts the token stream into an `ExamNode` AST with full error recovery.

---

**Full changelog:** [CHANGELOG.md](https://github.com/YOUR_USERNAME/examdc/blob/main/CHANGELOG.md)
