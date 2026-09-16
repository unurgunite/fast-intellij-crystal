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
reference-graph resolved 83% → 93% (120700 refs: 106068 → 112374;
unresolved 13618 → 2890). Text table now indexes visibility-prefixed
defs/macros (`private macro interpret_check_args`), `fun` bindings
(`LibC#strlen` + bare, alias-aware, def-beats-fun), and type fields
(`Point#x`, type-body-only); bare `CrystalReference` resolves macro calls
via `CrystalMacroIndex`, DOT-calls resolve `fun`/fields (same-file libs;
stdlib via text table). Harness marks `asm`/`w` noise; aggregate and
structure tests share one live parse-error walk (order-independent, no
stale-TSV flake). Remaining unresolved is FFI noise by construction
(`icmp`, libc `fun`), macro-generated names, and 2 EOF-at-length parse
artifacts proven pre-existing on the pre-wave-10 HEAD. Full suite 931
green, spotless green (detekt fails identically on clean HEAD —
environment issue, `PluginEnabler` init). Wave-10 grammar details in
`docs/specs/wave-10-grammar.md`.

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
