# ExamdC

<!-- Badges — update URLs once repo is live -->
[![CI](https://github.com/YOUR_USERNAME/examdc/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/examdc/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/YOUR_USERNAME/examdc?include_prereleases)](https://github.com/YOUR_USERNAME/examdc/releases)
[![GitHub Packages](https://img.shields.io/badge/packages-GitHub-blue?logo=github)](https://github.com/YOUR_USERNAME/examdc/packages)
[![Java 21+](https://img.shields.io/badge/java-11%2B-orange?logo=openjdk)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> A production compiler for the **EXAMD domain-specific language** — converts human-readable `.examd` exam definition files into self-contained, offline-capable HTML exam portals.

---

## What is EXAMD?

EXAMD is a structured plain-text DSL designed for tutors, educators, and exam platforms. Instead of building exam UIs manually or wrestling with Word/PDF templates, you write a `.examd` file:

```text
[EXAM]
title: Class 7 — Science Mid-Term
duration: 90min
total_marks: 50

[SECTION: A]
title: Multiple Choice Questions

[Q: 1]
marks: 4

[STEM]
type: text
content: What is Newton's Second Law of Motion?

[INTERACT]
type: mcq
options:
- A: F = ma
- B: E = mc²
- C: v = u + at
- D: p = mv

[EVALUATE]
type: exact
answer: A
```

**ExamdC compiles this into a zero-dependency offline HTML exam portal** — ready to run in any browser, no server needed.

---

## Features

- **Markdown-style structure** — block headers `[EXAM]`, `[SECTION]`, `[Q]` make files scannable at a glance
- **LaTeX math support** — embed `$$ F = ma $$` or inline `$E = mc^2$` via KaTeX (compiled in, no CDN)
- **Rich question types** — MCQ, MSQ, Fill-in-the-blank, Match, Sequence, Coding, Essay, Draw, and more
- **Zero runtime dependency** — generated HTML is fully self-contained (CSS, JS, fonts all inlined)
- **Span-accurate diagnostics** — every error points to the exact file/line/column with suggested fixes
- **Plugin architecture** — custom scoring rules, UI themes, and output targets via the plugin API
- **TeaVM browser target** — the compiler itself runs in-browser for live preview in ExamdC Studio

---

## Architecture

ExamdC is a **6-phase compiler pipeline**. Each phase has a single responsibility and communicates with the next through a typed contract:

```
 .examd source
      │
      ▼
┌─────────────────────────────────────────────────────┐
│  Phase 1 — Lexer                                    │
│  Raw chars → List<Token> with Spans                 │
│  TokenType (9 types) · Span · LexerException        │
└─────────────────────┬───────────────────────────────┘
                      │ List<Token>
                      ▼
┌─────────────────────────────────────────────────────┐
│  Phase 2 — Parser                                   │
│  Token stream → ExamNode AST                        │
│  RecursiveDescentParser · FuzzyBlockRepair          │
└─────────────────────┬───────────────────────────────┘
                      │ ExamNode (AST root)
                      ▼
┌───────────────────────────┐  ┌──────────────────────┐
│  Phase 3a — Sub-language  │  │  Phase 3b — Semantic  │
│  KaTeXHandler             │  │  Validator            │
│  PrismHandler             │  │  SymbolTable          │
│  MathMLHandler            │  │  12 validation rules  │
└─────────────┬─────────────┘  └──────────┬───────────┘
              └────────────┬──────────────┘
                           │ Validated AST + Diagnostics
                           ▼
┌─────────────────────────────────────────────────────┐
│  Phase 4 — Optimizer                                │
│  DeduplicateContextPass · SectionOrderPass          │
│  Block-hash incremental cache                       │
└─────────────────────┬───────────────────────────────┘
                      │ Optimized AST
                      ▼
┌─────────────────────────────────────────────────────┐
│  Phase 5 — Code Generator                           │
│  HtmlGenerator · JsonGenerator · PrintGenerator     │
│  FNV-32 integrity hash · plugin inlining            │
└─────────────────────┬───────────────────────────────┘
                      │
           ┌──────────┴───────────┐
           ▼                      ▼
   exam.html (offline)   exam-config.json
```

All phases share a `DiagnosticCollector` that accumulates errors and warnings (E001–E020, W001–W009) so the compiler reports everything in one pass.

---

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.6+

```bash
java -version   # openjdk 11 or higher
mvn -version    # Apache Maven 3.6+
```

### Build

```bash
git clone https://github.com/YOUR_USERNAME/examdc.git
cd examdc
mvn package -DskipTests
```

### Run

```bash
# Compile a .examd file → offline HTML exam
java -jar target/examdc-*.jar compile my-exam.examd -o output/

# Validate only (no output generated)
java -jar target/examdc-*.jar check my-exam.examd

# Show the token stream (debug / learning mode)
java -jar target/examdc-*.jar lex my-exam.examd --dump-tokens
```

### Run Tests

```bash
mvn test
```

---

## Project Status

| Phase | Name | Status |
|-------|------|--------|
| 1 | Lexical Analysis | ✅ Complete |
| 2 | Parsing (AST) | 🔄 In progress |
| 3 | Semantic Validation | 📋 Planned |
| 4 | Optimization | 📋 Planned |
| 5 | Code Generation | 📋 Planned |
| 6 | TeaVM Browser Target | 📋 Planned |

---

## Module Structure

```
examdc/
├── pom.xml
└── src/
    ├── main/java/com/examd/compiler/
    │   ├── diagnostics/          # Span, Diagnostic, DiagnosticCollector
    │   ├── lexer/                # TokenType, Token, Lexer, LexerException
    │   ├── parser/               # RecursiveDescentParser, FuzzyBlockRepair
    │   ├── ast/                  # ExamNode, SectionNode, QuestionNode, …
    │   ├── sublang/              # KaTeXHandler, PrismHandler, MathMLHandler
    │   ├── validator/            # SemanticValidator, SymbolTable, rules/
    │   ├── optimizer/            # OptimizationPass, DeduplicateContextPass
    │   ├── generator/            # HtmlGenerator, JsonGenerator, targets/
    │   ├── plugin/               # PluginAPI, PluginLoader
    │   └── core/                 # CompilerPipeline, CompilerOptions, CLI
    └── test/java/com/examd/compiler/
        └── lexer/                # LexerTest (17 tests, Phase 1)
```

---

## Error Reference

ExamdC uses stable error codes so tooling can respond to specific errors programmatically.

| Code | Phase | Description |
|------|-------|-------------|
| E001 | Lexer | Unterminated quoted string |
| E002 | Lexer | Invalid escape sequence |
| E003 | Lexer | Unexpected character or line structure |
| E004 | Parser | Unknown block header |
| E005 | Parser | Missing required block |
| E006 | Parser | Unexpected end of file |
| W001 | Validator | Deprecated key name |
| W002 | Validator | Marks sum mismatch |

Full reference: [docs/error-codes.md](docs/error-codes.md) *(coming in v0.2.0)*

---

## Development Devlog

This project is being built as a 30-day deep-dive into compiler engineering. Each phase is implemented from scratch with full explanations of design decisions.

- **Day 1** — Lexer: 9 token types, state machine, span-accurate errors → [Devlog #1](#)
- **Day 2** — DiagnosticCollector + JUnit 5 test suite → [Devlog #2](#) *(coming soon)*

Follow the journey: [LinkedIn](https://linkedin.com/in/YOUR_PROFILE) · [GitHub Discussions](https://github.com/YOUR_USERNAME/examdc/discussions)

---

## Contributing

Contributions are welcome after the project reaches v0.3.0 (stable parser + validator). Until then, please open a Discussion rather than a PR so design decisions can be discussed first.

1. Fork the repo
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Follow the commit convention: `type(scope): description` (see [CONTRIBUTING.md](CONTRIBUTING.md))
4. Open a PR against `main`

---

## License

MIT © Saiyam — see [LICENSE](LICENSE) for details.

---

*Built as a portfolio compiler engineering project. If you find it useful, a ⭐ is appreciated.*
