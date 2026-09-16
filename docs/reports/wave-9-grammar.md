# Wave 9 grammar fixes (report, 2026-09 — historical, not a living spec)

> Point-in-time report. Lasting invariants from this wave (PEG ordering,
> reverted experiments) are referenced by the grammar specs where they still
> apply; the rest is history.

## Shorthand `&.` keyword methods

`implicit_object_call` uses the full `keyword_as_method` set (was an ad-hoc list
missing DEF/WHEN/ANNOTATION): `a.try(&.def)`, `list.map(&.def)`,
`try(&.when)`, `try(&.annotation(x))` — all stdlib shapes, all verified legal.

`macro_interpolation` is deliberately NOT in the name set: `foo .{{x}}` and
`foo(&.{{x}})` are real syntax errors ("expecting IDENT, ... (not '{{')"),
and the alternative had let the bare tower steal `{{a}}.{{b}}` chains
(BARE_COMMAND with callee `{{a}}` + arg `.{{b}}` instead of a flat postfix —
MacroSetter golden). `{{a}}.{{b}}` still parses via the expression tower.

## Chained DOT-call assignment

`point.x_ptr.value = 5.0` needs the LONGEST chain to win: `assignable_call`'s
`dot_call_access` tail swallows `.x_ptr.value` as call+bare-args with no
backtrack. `assignment` therefore has a greedy first alternative built on
`assignable_head` (receiver + ONE no-args DOT segment) + explicit DOT + name +
rest + value. Plain `a.b = v` keeps its old shape via the second alternative.
`tail.list_next = other.value.@head` keeps its flat DOT_CALL chain (verified).

Reverted experiment (do not retry): an `[ASSIGN (bare_command ...)]` tail on
`dot_call_access` itself fixes `@c.last = phi t1, t2` but lets the previous
DOT-call's ASSIGN-branch swallow a following `.@ivar` as a bare arg
(`other.value.@tail` broke; ConditionalAssignment golden). The phi shape parses
via `assignment` anyway. Likewise a bare-tower split (`bare_implicit_object_call`
without interpolation/ivars) unwrapped `&.`-chain `.[1]`/`!` suffixes
(ImplicitObjectCallBracket/BangDotCall goldens) — reverted.

## `for` with clause bodies

`for_statement ::= FOR IDENTIFIER (COMMA IDENTIFIER)* IN expression then_clause
for_body END`, `for_body ::= statement_list | (when_clause | in_clause |
macro_control)+`. Covers `{% for a, b in NS %}` multi-target loops and
`{% for %}`-generated `in`/`when` case branches (token.cr, tracing.cr).
A bare `{% for %}` directly in `case` (no `{% begin %}` wrapper) is ILLEGAL in
real Crystal — the grammar still accepts that union (context-insensitive).

## Comparison RHS reaches `not_expression`

`!!a != !!b`, `a == !b` are legal but `!` never starts a `range_expression`,
so the RHS `!` dangled (restrictions.cr, method_lookup.cr). Both towers
(`comparison_expression`, `bare_comparison_expression`) now continue with
`not_expression`.

## Golden harness nondeterminism (verified 2026-09-14, kept in test comment)

Same file parses to two well-formed shapes depending on suite execution order
(trailing-newline PSI + DOT_CALL_ACCESS-vs-IMPLICIT_OBJECT_CALL on identical
input); green file-by-file, red in suite. This is GrammarKit GPUB memo
nondeterminism leaking across tests in one JVM (pinning `gpub.max.level=6000`,
max-workers=1, and regen-goldens do not fix it — the flip follows order, not
content). CI runs goldens non-blocking (`-PgoldenOnly=true`).

## Open gaps (verified legal at wave 9, deferred — recheck against current
grammar before working; wave 10 did not claim them)

- Named args in index (`a[0, foo: 1]`), ternary in index (`a[b ? c : d]`),
  space-call paren-first (`foo (1), 2`), bare-in-bare first arg
  (`exec new_request method, ...`).
- `{{ @type <= T }}` (needs LTE/GTE in MACRO_INTERPOLATION), `{% for %}`
  directly in case (illegal bare vs legal wrapped form), macro-`for` bodies
  that are statement_lists containing `when` (case-branch generation inside a
  plain `def` — `statement`'s `!WHEN` guard rejects them).

Goldens: ShorthandKeywordMethods, UnionTypeAsIdentifier, LibIncludeExtend,
MacroGeneratedMembers, MacroForMultiTarget, IvarInterpolationAccess,
InterpolatedMethodSegment, ReturnAssignValue, NotComparisonRhs,
ChainedDotAssign, GroupedRescueModifier, TypeIndexArgs, IndexMiscShapes,
RespondsToCall, MacroInterpTokens (all 0 PsiErrorElement).
