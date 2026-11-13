# TODO — Fast Crystal

Open work, ordered by priority. History lives in git, not here: when an item
is done, collapse it into one line under `Done` and delete the details.

## Open

### P1 — Crystal Shards support

Large effort, needs index/scope design:

- [ ] **Parse `shard.yml`** — extract dependency declarations
- [ ] **Index `lib/`** — include shard sources in StubIndex, respecting
      `shard.lock`
- [ ] **Dependency-aware resolution** — prefer shard sources over the stdlib
      fallback when both define a name

### P2 — 2026.3 in the verification matrix

`build.gradle.kts` targets IdeaIU/RubyMine RELEASE 2026.1 and 2026.2 only
(`untilBuild = "262.*"`). The 263 line renamed the `DapBreakpointsDescription`
constructor (fixed reflectively in 1.2.2, and the RubyMine test-runner bundle is
now an optional dependency), so adding 263 to `pluginVerification` is the
remaining step before widening `untilBuild`.

### P3 — Parser golden determinism

`CrystalParserTest` goldens are non-deterministic on JDK 21 + GrammarKit (lazy
parser-table construction over seed-randomized map order), which is why they run
non-blocking. Unblock them by testing on JDK 17 or moving to a newer
GrammarKit — see the environment note below.

## Done

- **`def %` lexer fix** — `%` right after `def`/`macro` is the modulo operator,
  not a percent literal (`afterDef` flag + `yypushback(1)`); `float.cr` parses
  with 0 errors.
- **Implement Members** — abstract-method discovery in parents plus stub generation.
- **Inlay Hints** — inferred types for variables and parameters.
- **Stdlib parse/resolve sweep** — parse errors 85 → 2 files (2172 files, both
  EOF-at-length artifacts), reference graph resolved 94%.
- **Live-index stub mismatch** (`Stub count (7) doesn't match stubbed node length
  (12)`) — same-file parsing proven deterministic; fixed by stub-version bump
  2 → 3. Rule: bump `getStubVersion()` on any grammar/lexer change altering stub
  tree shape.
- **Scope-aware rename** — resolve promotion, `@`/`@@` prefix preserved,
  validator accepts prefixed identifiers.
- **Type inference** — literals, array/hash/tuple shapes, control-flow unions,
  union-aware dot-call resolution.
- **Stdlib Go to Definition** via bounded VFS scan; platform default stub builder
  (the hand-rolled one was never called); `CrystalHighlightErrorFilter`
  early-returns for non-Crystal files.
- **Parser performance** — ternary triple-parse O(3^depth) → single alternative;
  `def Type.name` PEG shadow fixed.
- **CI hygiene** — `spotlessCheck` + `detekt` green (manual refactors, no
  threshold tuning); large test classes split.
- **Docs restructure** — living behavior in `docs/specs/`, conventions in
  `docs/TESTING.md`, wave reports in `docs/reports/` (marked historical). The
  unimplemented ECR sketch and the single-file `decision-log/` were dropped; the
  Code Style decision now lives in `ARCHITECTURE.md`'s invariants.
- **Architecture waves 0–3** — `editor/` extracted, `psi/{references,stdlib,util}`
  split, leaf `type/` kernel, cycles dead, `ARCHITECTURE.md` module map updated.

## Environment notes (not actionable, do not chase with code edits)

- GrammarKit `MAX_RECURSION_LEVEL` (1000) is baked into the generated parser;
  deeply-nested multi-alternative unions can still exceed it (flat
  `type_union_member` chain mitigates the common cases).
- `def x` without `end` is only valid inside macro bodies; the grammar tolerates
  it via an optional method body.
- Residual: single zero-width error at `{` opening a very large method-body hash
  (deterministic, pre-existing, fast). One error node; surrounding definitions
  still indexed.
- Test suite: 1289 tests in `test`, 142 parser goldens via `-PgoldenOnly=true`.
