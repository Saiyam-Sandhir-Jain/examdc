# Contributing to ExamdC

Thank you for your interest in ExamdC.

The project is currently in active early development (pre-v0.3.0). The architecture is being established phase by phase. During this period, **please open a Discussion before submitting a PR** so design decisions can be aligned first.

---

## Commit Convention

ExamdC uses [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): short description

Longer explanation if needed. Wrap at 72 characters.

Refs: #issue-number
```

**Types:** `feat` · `fix` · `refactor` · `test` · `docs` · `chore` · `perf`

**Scopes:** `lexer` · `parser` · `ast` · `validator` · `sublang` · `optimizer` · `generator` · `plugin` · `core` · `diagnostics` · `ci`

**Examples:**
```
feat(lexer): add INDENT_LINE token type for pipe scalars
fix(parser): handle missing [EVALUATE] block gracefully
test(validator): add rule R007 negative-marking range tests
docs(readme): update Phase 2 status to complete
```

---

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Stable, always compiles and passes all tests |
| `dev` | Integration branch for in-progress phase work |
| `feat/phase-N-name` | Feature branches for each compiler phase |
| `fix/issue-description` | Bug fix branches |

---

## Code Style

- Java 11, no external runtime dependencies (JUnit for tests only)
- Every public class and method has a Javadoc comment explaining **why** it exists, not just **what** it does
- No magic numbers — use named constants or enums
- All error paths must produce a `Span`-attached diagnostic
- TeaVM constraints: no `java.nio.*`, no reflection, no `Thread`

---

## Running Tests

```bash
mvn test                    # all tests
mvn test -pl . -Dtest=LexerTest   # single test class
```
