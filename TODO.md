# TODO — IntelliJ Crystal Plugin

Open work, ordered by priority. History lives in git, not here: when an item
is done, collapse it into one line under `Done` and delete the details.

## Open

### P1 — Lexer: `def %` operator methods break stdlib parsing (correctness)

In `Crystal.flex` the bare percent-literal rule `"%" [\(\[\{<|]` turns `%(...)`
into `PERCENT_LITERAL_BEGIN`, so `def %(other)` (e.g. `struct Float`'s
`modulo`/`remainder` delegation) never reaches the `PERCENT` operator-method-name
token. This aborts parsing of everything after `struct Float` in `float.cr`, so
`struct Float64` / `Float64::INFINITY` are never captured (and `float.cr` gets no
semantic highlighting). Crystal itself supports BOTH `def %` (operator) and bare
`%(...)` percent literals, disambiguating by parser context — our JFlex lexer needs
the equivalent: track "expecting method name after DEF/MACRO" and emit `PERCENT`
(not `PERCENT_LITERAL_BEGIN`) for `%`+delimiter in that state. Regression surface:
164 bare `%(...)` percent literals in the stdlib must keep working.

### P1 — Audit remaining stdlib parse breaks

After the `def %` fix, re-run a full stdlib VFS walk and report per-file symbol
counts; fix any other operator methods (`[]`, `[]?`, `==`, `<<`, etc.) or constructs
that still abort file parsing.

Status 2026-09-16 (wave 10 + stdlib-scan follow-ups, uncommitted on
`feature/ci-infrastructure`): parse errors 85 → 2 files (2172 files);
reference-graph resolved 83% → 94% (120700 refs: 106068 → 113531;
unresolved 13618 → 1968 actionable). Text table now indexes visibility-prefixed
defs/macros (`private macro interpret_check_args`), `fun` bindings
(`LibC#strlen` + bare, alias-aware, def-beats-fun), type fields
(`Point#x`, type-body-only), and enum member predicates (`Color#red?`,
CamelCase → `crystalUnderscore`, ALL-CAPS included, alias members included);
`getter?`/`property?`/`!`/`class_*` expand per the exact object/properties.cr
matrix (predicate-only readers, `property?` writer, `!` triple); lone-`end`
anchored balance (no phantom closes from strings/same-line `end`), `}` never
pops type frames (`CONST = {` literals), `; end` one-liners net to zero;
bare `CrystalReference` resolves macro calls via `CrystalMacroIndex`,
DOT-calls resolve `fun`/fields/enum predicates (same-file libs; stdlib via
text table; `Color::Red.red?` via enclosing enum, bare `Color.red?` stays
unresolved — invalid Crystal). Harness marks `asm`/`w` noise; aggregate and
structure tests share one live parse-error walk (order-independent, no
stale-TSV flake). Structure: 6921 types, 22251 methods, 102712 calls (95953
resolved, 1562 unresolved). Remaining unresolved is FFI noise by construction
(`icmp`, libc `fun`, LLVM primitives), macro-generated names, regex-harness
blind spots (bare `nil_if_read`/`control_nest`/`arena` vs real `?`/qualified
defs), and 2 EOF-at-length parse artifacts proven pre-existing on the
pre-wave-10 HEAD. Full suite 958 green, spotless green, detekt green
(2.0.0-alpha.6, Gradle 9.6.1; non-method matchers extracted to file level,
`crystalUnderscore` → `CrystalNameUtils`, scan key emission → `ScanKeys`).
Test-suite trim: `StdlibGraphToolTest` (0 asserts, diagnostic dump) excluded
from `test`, stays runnable via manual `stdlib*` tasks; `CrossFileGoto` (5),
`SyntaxHighlighterFactory` (1), `NavigationItem` (3) merged into their
neighbours and deleted; 4 `ProvidersTest` dups removed (pipeline coverage in
`TypeAnnotationTest` strengthened instead); new `DotCallReceiverTest` (8),
`LocalScopeResolveTest` (5), `StdlibFileResolveTest` (7), `crystalUnderscore`
moved to `PsiUtilsTest` + 4 cases, union/depth `inferTypeList` tests (2).
Wave-10 grammar details in `docs/reports/wave-10-grammar.md`.
Docs restructure: `docs/specs/` holds living behavior only (10 specs +
`README.md` index); test conventions → `docs/TESTING.md` (count fixed,
suite/golden layout documented); waves → `docs/reports/` (marked historical);
code-style decision → `docs/decision-log/`; ECR tutorial deleted, IDE sketch →
`docs/proposals/` as UNIMPLEMENTED; stale plans rewritten to behavior
(string-interp matrix, type-inference Implemented-vs-roadmap, find-usages
split out of block-highlighting, hover/completion deduped); CONTRIBUTING no
longer cites untracked `AGENTS.md`; BNF wave comments point at `reports/`.

### P2 — Implement Members

Standard IDE expectation, moderate effort:

- [ ] **Discover abstract methods** from parent classes/modules
- [ ] **Generate implementing stubs** with correct method signatures
- [ ] **Register OverrideImplement action** in plugin.xml

### P3 — Inlay Hints

High visibility, moderate effort, inference already in place (literals,
collections, unions):

- [ ] **Implement InlayHintsProvider** — show inferred types on variables inline
  in the editor.

### P4 — Crystal Shards support

Large effort, needs index/scope design. Valuable, but correctness (P1) and
standard IDE features (P2–P3) come first:

- [ ] **Parse shard.yml** — extract dependency declarations
- [ ] **Index lib/ directory** — include shard sources in StubIndex
- [ ] **Dependency-aware completion** — suggest types/methods from installed shards

## Done

- Scope-aware rename (35 tests in `CrystalRename{PsiNameIdentifierOwner,Resolve,BlockParameter}Test`;
  `PsiNameIdentifierOwner` on variable references/parameters/assignments; resolve promotion;
  `@`/`@@` prefix preserved; validator accepts prefixed identifiers).
- Type inference: literals, array/hash/tuple shapes, control-flow unions;
  union-aware dot-call resolution (`inferTypeList` across all members).
- Stdlib Go to Definition via bounded VFS scan cache + background warmup
  (`CrystalStdlibCacheWarmup`); platform default stub builder (hand-rolled lexer
  stub builder removed — the platform never calls custom builders);
  `CrystalHighlightErrorFilter` early-returns for non-Crystal files.
- Parser performance: ternary triple-parse O(3^depth) → single alternative;
  `def Type.name` PEG shadow fixed; whole-stdlib parse errors 509 → 136 files
  with regression tests per sweep.
- CI hygiene: `spotlessCheck` + `detekt` green (manual refactors, no threshold
  tuning); large test classes split; parser goldens run non-blocking.

## Environment notes (not actionable, do not chase with code edits)

- `CrystalParserTest` golden files are non-deterministic on JDK 21 + grammar-kit
  (lazy parser-table construction over seed-randomized map order; verified by
  experiment — `pin` made it worse, JDK 17 unavailable, grammar-kit 2024.x not in
  the plugin repo). CI runs them non-blocking. Real fix: JDK 17 for tests or newer
  grammar-kit.
- GrammarKit `MAX_RECURSION_LEVEL` (1000) is baked into the generated parser;
  deeply-nested multi-alternative unions can still exceed it (flat
  `type_union_member` chain mitigates the common cases).
- `def x` without `end` is only valid inside macro bodies; the grammar tolerates it
  via optional method body.
- Residual: single zero-width error at `{` opening a very large method-body hash
  (deterministic, pre-existing, fast). One error node; surrounding definitions
  still indexed.
