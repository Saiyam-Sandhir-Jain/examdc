# Changelog

All notable changes to ExamdC are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html)

---

## [Unreleased]

### In Progress
- Phase 2: Recursive descent parser (AST construction)
- DiagnosticCollector: multi-error accumulation

---

## [0.1.0] — 2026-05-14

### Added — Phase 1: Lexical Analysis

**`diagnostics/Span.java`**
- Source location value object carrying `file`, `lineStart`, `colStart`, `lineEnd`, `colEnd`
- `Span.point()` — single-character location factory
- `Span.line()` — full-line span factory
- `Span.merge()` — combines two spans into the smallest enclosing range
- Used by all subsequent phases for span-accurate diagnostics

**`lexer/TokenType.java`**
- 9-type vocabulary enum: `BLOCK_HEADER`, `KEY`, `VALUE_SCALAR`, `VALUE_PIPE`,
  `INDENT_LINE`, `LIST_ITEM`, `COMMENT`, `BLANK`, `EOF`
- Each type documented with regex pattern, examples, and design rationale

**`lexer/Token.java`**
- Immutable triple `(TokenType type, String lexeme, Span span)`
- `Token.eof()` factory for end-of-stream sentinel
- `token.is(TokenType)` and `token.hasLexeme(String)` convenience helpers
- `toString()` produces compact debug representation

**`lexer/LexerException.java`**
- Span-attached exception carrying stable error code (`errorCode` field)
- Used for catastrophic lexer failures; non-fatal errors will use DiagnosticCollector (Day 2)

**`lexer/Lexer.java`**
- Line-by-line state machine with 3 states: `NORMAL`, `IN_PIPE`, `IN_LIST`
- Handles: block headers, key-value pairs, pipe scalars, list items, comments, blanks
- `awaitingPipeFirstLine` flag for correct pipe base-indentation detection
- Quote stripping for double- and single-quoted scalar values
- `Lexer.dumpTokens()` debug utility for human-readable token stream output

**`lexer/LexerTest.java`** (JUnit 5)
- 17 tests covering: block headers, key/value pairs, pipe scalars, list items,
  comments, blanks, error detection, span accuracy, full integration scenario

### Project Setup
- Maven project structure with JUnit Jupiter 5.10.1
- `.gitignore` for Java/Maven/IDE artifacts
- Conventional Commits + semantic versioning from day one

### Verified Behaviours
- `[EXAM]`, `[SECTION: A]`, `[Q: 1]`, `[STEM]`, `[INTERACT]`, `[EVALUATE]` headers tokenize correctly
- Pipe scalar (`content: |`) with multi-line indented content produces correct `INDENT_LINE` tokens
- Pipe base-indentation locked from first indented line via `awaitingPipeFirstLine`
- Error E001 thrown at correct span for unterminated quoted strings
- EOF sentinel always last token in stream
- All tokens carry accurate line/column spans

---

## Versioning Strategy

| Version range | Meaning |
|---------------|---------|
| `0.x.0` | Phase completion milestones (lexer, parser, validator…) |
| `0.x.y` | Bug fixes and minor additions within a phase |
| `1.0.0` | First stable public release — all 6 phases complete and tested |

[Unreleased]: https://github.com/YOUR_USERNAME/examdc/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/YOUR_USERNAME/examdc/releases/tag/v0.1.0
