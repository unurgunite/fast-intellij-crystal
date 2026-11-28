# Inspection false-positive fix plan (post-1.5.0)

Source: a fresh inspection export over a large multi-crate Crystal workspace
(the export lives outside this repo; raw XML is machine output and git-ignored).
Counts in that run: **86 argument-count + 224 type-mismatch + 58 unused-variable
= 368 records**. Records are not independent defects: a handful of resolver
mechanisms produce most of them.

External code samples below are **generalised** (namespace `NS`, class `Thing`,
method `collect`) — no third-party project names.

## How to read a record

Every record must be triaged into exactly one bucket before any code changes:

1. **Plugin false positive** — the code is valid Crystal; fix the resolver.
2. **Analysed-code defect** — the call really is wrong; leave it alone.
3. **Unknown / needs repro** — write a failing unit test first, then decide.

A record with a malformed `got` string (unbalanced parens/braces, a union in a
generic slot, a truncated namespace, a whole NamedTuple/Hash where an element
type belongs) is *always* bucket 1: the type renderer is broken, not the code.

## Mechanisms (root causes)

### M1 — `::` split eats generic arguments (≈10 records)
`CrystalReceiverNames.direct` and `receiverClassNameOf` take the last `::`
segment of a whole rendered type. `Set(NS::Thing)` becomes `Thing)`,
`Array(NS::A::Token)` becomes `Token)`.
Sites: `type/CrystalCallReturnTypeResolver.kt` (`CrystalReceiverNames.direct`),
`type/CrystalExpressionTypeResolver.kt` (`receiverClassNameOf`).
Fix: `::`-trim only the head before the first `(`, keep the generic tail.
Affected shapes: `x = Set(NS::Thing).new` passed where `Set(NS::Thing)` is
expected; `tokens.reverse!` where `tokens : Array(NS::A::Token)`.

### M2 — hash literal unions land in one generic slot (3 records)
`CrystalCollectionTypeResolver.resolveHashLiteral` joins key/value types without
`distinctBy`, so `{"a" => "b", "c" => 10}` renders as
`Hash(String | String, String | Int32)` instead of `Hash(String, String | Int32)`.
Fix: dedupe like `resolveArrayLiteral` already does.

### M3 — dot-call method name is the receiver (≈10 records)
`CrystalCallReturnTypeResolver.extractMethodName` returns the *first* child
identifier. For `obj.keys` that first child is `obj`, so the lookup runs for
`obj` and returns `obj`'s own annotated type, not `keys`'s return type.
Sites: `extractMethodName`, used by `resolveMethodCallReturnType`.
Fix: when the expression is a dot-call, the method name is the identifier after
the `DOT`.

### M4 — project-wide name-only return-type lottery (≈20 records)
`CrystalCallReturnTypeResolver.lookupReturnType` returns the first annotated
return type for a bare name anywhere in the project, with no scope preference.
`path = write(...)` picks an unrelated `def write(...) : Nil` from another crate
→ `path : Nil` → `[path]` → `Array(Nil)`.
Sites: `lookupReturnType`, `lookupReturnTypeInClass`.
Fix: order candidates same-file first (then same-enclosing-type), fall back to
the project-wide first hit only when nothing local matches. Cheap, and it
removes the "first unrelated overload wins" family without dropping legit
cross-file annotations.

### M5 — ternary/`if` type falls back to the condition (≈7 records)
`CrystalControlFlowTypeResolver.resolveCompoundExpression` falls through to
`firstChild` when both ternary branches are unresolved, so `flag ? A_CONST :
TABLE[i]` is typed as the condition (`Bool`).
Fix: if it *is* a ternary and neither branch resolves, return unknown instead of
the condition.

### M6 — loop back-edge reads (unused-variable, ≥1 confirmed)
`cursor = start + size` in a loop body is read by the loop *condition* on the
next iteration; the dataflow does not model the back edge, so `cursor` is
reported unused.
Site: `inspections/CrystalBlockExitReads.kt` (`addBlockRepeatReads` handles
`CrystalBlock`/`CrystalProcLiteral` bodies but not `while`/`until` conditions).
Fix: for a `while`/`until` statement, a body write whose name is read in the
loop condition is live.

## Deferred (diagnosed, needs its own investigation branch)

- **Argument↔parameter pairing** (≈12 records, e.g. `expected 'String', got
  'Int32'` for a status-literal arg; two named/defaulted params reported
  swapped). Candidate lookup is name-only project-wide for implicit-receiver
  calls, so an unrelated 2-param overload matches. Scoping to the enclosing
  type is a feature, not a one-liner; defer.
- **Stdlib union parameter truncation** (≈20 records, `JSON::Any.new(hash)`).
  The stub-derived parameter union drops `Hash(...)`/`Array(...)` members and is
  truncated. Needs a look at stub parameter extraction; defer.
- **Highlight cost on a hot file** (perceived slowness). The file parses with
  zero `PsiErrorElement` (verified via `sweepTree`), so cost is in the
  inspection/inference pass, not the grammar. Needs a timed harness + construct
  bisection; defer, do not guess.

## Branches (all cut from `v1.5.0`, no version in the name)

Ordered by (records removed) / (risk × size).

| # | branch | mechanisms | test home |
|---|--------|-----------|-----------|
| 1 | `fix/type-string-qualified-receiver` | M1, M2 | `CrystalTypeCheckGenericsTest`, `CrystalTypeCheckLiteralsTest` |
| 2 | `fix/dotcall-method-name` | M3 | `CrystalCallReturnTypeTest`, `CrystalDotCallReceiverClassTest` |
| 3 | `fix/return-type-scope` | M4 | `CrystalCallReturnTypeTest` (same-file wins over other file) |
| 4 | `fix/ternary-unknown-branch` | M5 | `CrystalTypeCheckLiteralsTest` / new ternary test |
| 5 | `fix/unused-loop-backedge` | M6 | `CrystalUnusedVariableInspectionTest` |
| — | `docs/inspection-fix-plan` | this file | — |

Each branch: one focused change + a positive test (no false positive) and, where
relevant, a negative test (a real mismatch is still reported). Branch is merged
into `v1.5.0` only via PR; **no merge is performed here**.

## Verification protocol per branch

1. Write/adjust a unit test that fails on `v1.5.0` for the reported shape.
2. Apply the smallest change that makes it pass.
3. Run the touched test classes + the full `test` task; no existing test may
   regress.
4. Commit, push, open PR against `v1.5.0`.
