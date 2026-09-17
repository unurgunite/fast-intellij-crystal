# Changelog

All notable changes to the Fast Crystal Plugin for JetBrains IDEs will be documented in this file.

## [1.1.0] — Unreleased

### Added

- **Implement Members (Code → Generate)** — new `generation/` package with
  `CrystalImplementMethodsHandler` registered via
  `com.intellij.codeInsight.implementMethod`: discovers unimplemented
  `abstract def` methods across the ancestor chain (superclass + includes,
  nearest-concrete-wins, name + arity identity) and generates stubs raising
  `NotImplementedError` with copied signatures (params, return type, forall).
  Macro-generated names are skipped. Covered by
  `CrystalImplementMembersTest` (11 tests).
- **Inlay Hints (Settings → Editor → Inlay Hints → Crystal)** — new `inlay/`
  package with `CrystalInlayHintsProvider` on the declarative (stateless)
  API: inferred local-variable types render as `: Type` after unannotated
  assignments (`x = 1` → `: Int32`), reusing `CrystalTypeInference`. Skips
  annotated names, `@ivar`s, `Nil`, and nested RHS assignments. Covered by
  `CrystalInlayHintsTest` (11 tests, incl. factory wiring).

### Removed

- **Thin `completion/` delegates** — `LookupBuilders.getParameterSignature` /
  `getReturnType` / `extractParameterName` and
  `RecordCompletion.findRecordDefinition` / `extractRecordFields` forwarded to
  `type/` verbatim with zero outside callers; deleted, internal call sites use
  `CrystalMethodLookup` / `CrystalRecordLookup` / `psi.util` directly.

## [1.0.1] — 2026-09-17

### Fixed

- **Stdlib-graph CI green on fresh runners** — the `stdlib*` Gradle tasks
  skipped `prepareTestSandbox`, so on runners without a warm build cache the
  IntelliJ test framework booted against an empty plugin sandbox and died
  with `ClassNotFoundException: CrystalFileType` before the first test ran.
  The tasks now depend on `prepareTestSandbox` like the `test` task does.
  Also fixed: IntelliJ Platform Gradle plugin build services break the
  configuration cache, so the stdlib-graph workflow runs with
  `--no-configuration-cache`, and now uploads JUnit/HTML diagnostics on
  failure instead of leaving a truncated console log.

### Changed

- **Docs follow the new package layout** — `README.md` Architecture section
  names the `type/` kernel and `editor/` package; all five mermaid diagrams
  re-rendered (`architecture` now shows the one-way `type/` ← consumers graph
  and the `run/` → `debugger/` service edge; `completion`/`inspections`
  nodes point at `type/` classes; `spec-run` shows the
  `CrystalDebugStateFactory` service; stale warmup node removed from
  `resolution`).

## [1.0.0] — 2026-09-13

### Fixed

- **Lint toolchain fixed and zero-findings** — detekt `2.0.0-alpha.4` →
  `2.0.0-alpha.6`, Gradle wrapper 9.4.1 → 9.6.1, ktlint pinned to 1.8.0
  explicitly in both spotless blocks (was unpinned, drifted between
  machines). All 10 findings fixed by extraction, not suppression:
  non-method DOT-call matchers (`matchFunInLibBody`, `matchMemberInTypeBody`
  + `matchFieldLeaf`, `matchEnumConstantInTypeBody`,
  `matchEnumValueReceiver` + `enumQualifiedName`/`matchQualifiedEnumConstant`)
  moved to file level; `resolveClassMember` fallback extracted to
  `resolveNonMethodMember`; dead `findLibBodyInIndex` stub deleted;
  `crystalUnderscore` → new `CrystalNameUtils`; scan key emission
  (`addSymbol`/`addMethodSymbol`/`addGenKeys`/`directlyInType`/`parseDefSig`)
  → new `CrystalStdlibScanKeys`.
- **Test-suite trim (945 → 958, all green)** — `StdlibGraphToolTest` (1072
  lines, 0 asserts, diagnostic dump) excluded from `test`, stays runnable via
  the manual `stdlibParseErrors`/`stdlibBuildGraph`/`stdlibStructure`/
  `stdlibCheckFile` tasks; `CrystalCrossFileGotoTest` (5),
  `CrystalSyntaxHighlighterFactoryTest` (1), `CrystalNavigationItemTest` (3)
  merged into neighbouring files and deleted; 4 `CrystalProvidersTest` cases
  duplicating the completion pipeline removed (pipeline assertions in
  `CrystalCompletionTypeAnnotationTest` strengthened instead). New coverage
  for previously indirect-only code: `CrystalDotCallReceiverTest` (8),
  `CrystalLocalScopeResolveTest` (5), `CrystalStdlibFileResolveTest` (7),
  `crystalUnderscore` moved to `CrystalPsiUtilsTest` (+4 edge cases), union
  members + depth-budget `inferTypeList` tests (2).
- **Architecture waves 0–3 (all package cycles dead)** — wave 0: 4 unused
  cross-package imports removed. Wave 1: `editor/` (13 files) + rehomed
  `TodoIndexer`→`highlighting`, `TemplateContextType`→`editor`,
  `SpecSourceRootConfigurator`→`project`, `StdlibCacheWarmup`→`sdk`,
  `SingleQuoteStringInspection`→`inspections` (+12 `plugin.xml` FQNs).
  Wave 2: `psi/{references,stdlib,util}` + `navigation/{parameterinfo}`
  (ivar cluster merged into `psi/`; 2 `parameterInfo` FQNs fixed).
  Wave 3: leaf `type/` kernel (inference, resolvers, call-shape model,
  `MethodLookup`, `RecordLookup`, `psi.util.extractParameterName`);
  `completion/` keeps thin delegates; `run↔debugger` via the
  `CrystalDebugStateFactory` application service. Dependency direction is now
  one-way (`type/` ← `completion/`/`inspections`/`psi/`/`navigation/`;
  `psi/` never imports `navigation/`). `ARCHITECTURE.md` module map updated.
- **Docs restructure (no more mixed-genre `docs/specs/`)** — `specs/` holds
  living behavior only (10 specs + `README.md` index); test conventions →
  `docs/TESTING.md` (stale count fixed, suite/golden layout documented); wave
  9/10 → `docs/reports/` marked historical (open-gaps list flagged for
  recheck); code-style decision → `docs/decision-log/`; ECR language tutorial
  deleted (incl. a stray German intro), IDE design sketch preserved in
  `docs/proposals/` as UNIMPLEMENTED; stale implementation plans rewritten to
  pure behavior (string-interpolation matrix, type-inference
  Implemented-vs-roadmap, find-usages split out of block-highlighting,
  hover/completion namespace overlap deduped). `CONTRIBUTING.md` cites tracked
  docs instead of untracked `AGENTS.md`; BNF wave comments point at
  `reports/`; `ARCHITECTURE.md` index updated. BNF change is comment-only —
  generated parser byte-identical.
- **Stdlib parse breaks wave 10 (85 → 2 files, 2172 files)** — macro-heavy
  shapes, all verified legal with crystal 1.21.0: `**`/`<<`/`>>`/`//` in
  string interpolation (xml.cr); macro-split def signatures via
  `method_variant_header` (indexable/mutable.cr `map!`); `do`/`end` +
  keywords + NEWLINE in `{{ }}`/`#{}` (`{{ x.map do ... end.splat }}`,
  `{{ if ... else ... end }}`); heredoc inside `{% %}` as flat tokens
  (`{% raise <<-TXT }}`); escaped `\{%`/`\{{` as body text (big_int.cr,
  llvm.cr, ecr/macros.cr); comma-transparent macro controls in lists
  (`{% begin %} [...] {% end %}`, `yield({...})`); expression-before-tokens
  in `macro_array_tail` (`ENV["B"]`); postfix modifier on multi-assign
  (`a, b = b, a if c`); `{{...}}*` splat params; unclosed-macro fallback
  rule (macro TEXT with `{{...}}` params). Ten regression goldens, all with
  zero `PsiErrorElement`. Remaining `spec/helpers/string.cr` +
  `syntax/parser.cr` are EOF-at-length artifacts proven pre-existing on the
  pre-wave-10 HEAD. Details in `docs/reports/wave-10-grammar.md`.

### Added

- **Test coverage marathon (+281 tests, 717 → 998)** — every previously untested subsystem now has
  fixture or unit tests: Go to Class/Symbol contributors, instance-variable finder and references
  searcher, all four completion providers, run-configuration producer/factories/options round-trip,
  `CrystalRunState` command-line building, DAP debug args and runner routing, folding builder,
  structure view, brace matcher, commenter, code-block support, single-quote inspection, syntax
  highlighter mappings and factory, RegExp host, type-compatibility matrix, SDK detector, stdlib
  resolver/library provider, settings persistence, rename guards, navigation items, file type/icons,
  spec-test locator and line markers, definition finder, structure-view factory, spec command-line
  building, lldb-dap candidate lookup, real `crystal tool format` round-trips, SM console
  properties wiring, and project-generator helpers. Weak tests with zero assertions were
  rewritten with real assertions. (`./gradlew test` runs 906; 92 parser goldens run separately.)
- **Shared `CrystalCommandLine` helper** — argument splitting and `KEY=VALUE` env parsing used by
  run, spec and debug states, extracted from three duplicated inline implementations (no behavior
  change). `CrystalTestRunState.buildCommandLine`, `CrystalDebugAdapterDescriptor.findLldbDapCandidate`
  and `CrystalFormattingService.formatStdin` extracted the same way for testability.
- **CI runs parser goldens as a non-blocking step** — `CrystalParserTest` is excluded from the
  default `./gradlew test` (environment-flaky on JDK 21, see TODO.md) and runs via
  `./gradlew test -PgoldenOnly=true` with `continue-on-error` plus a known-flaky-set warning.

### Fixed

- **RegExp key namespace collision crashed `verifyPlugin`** — six highlighter keys were registered
  under the platform-owned `REGEXP.*` external names with different fallbacks, so whichever side
  initialized second died in `TextAttributesKey.mergeKeys` (order-dependent: green locally, red in
  CI's `buildSearchableOptions`). Keys now live in `CRYSTAL_REGEXP.*` with identical fallbacks,
  plus a reflection-based regression test pinning the `CRYSTAL_` namespace.
- **`verifyPlugin` muted for `TemplateWordInPluginName`** — "Fast Crystal Plugin" keeps the word
  "Plugin" by council decision; the verifier's naming-style check is muted via `freeArgs`.
- **Removed dead lexer stub builder** — the 286-line `CrystalStubBuilder` and its
  `languageStubDefinition` registration were never invoked by the platform (verified with
  a file-write probe: zero calls during stub indexing; stubs come from `DefaultStubBuilder`).
  Also fixed the stale `CrystalDotCallReference` comment that credited the builder with
  skipping stdlib files.
- **Stale `HighlightErrorFilter` test case** — `def foo(bar,)` parses cleanly (trailing commas are
  legal per the `TrailingCommas` golden), so the unhandled-error test asserted on zero error
  elements. Switched to `def foo(,)`, which genuinely produces one `PsiErrorElement` passing
  through the filter. The filter itself was innocent.
- **`findSpecName` never found spec names** — strings parse as `STRING_EXPRESSION` composites, never
  as bare `STRING_LITERAL` siblings, so single-spec configs were always named `spec: line N` instead
  of `spec: <name>`. Now resolves via `CrystalStringExpression`.
- **`workingDirectory` fallback was dead** — the `project.basePath` fallback never fired because the
  stored default is `""`, not null. Blank is now treated as unset.
- **`CrystalTestLocator` threw on out-of-range lines** — `getLineStartOffset(line - 1)` is now clamped
  to the document instead of throwing.
- **`CrystalNamesValidator` accepted invalid names** — setter `=` suffix was rejected while interior
  `?`/`!` (e.g. `a?b`, `@foo?`) was accepted. Now a single trailing `?`/`!`/`=` is allowed, interior
  markers are not.
- **`isUnderContentRoot` compares on directory boundaries** — `/proj-other/spec` is no longer
  misdetected as living under the `/proj` content root (plain `startsWith` matched the prefix),
  which would have made the spec-root configurator register a folder outside the module.
- **`CrystalInstanceVarFinder` missed assignments and property declarations** — `@x = 1` parses as
  `ASSIGNMENT > INSTANCE_VAR_ACCESS` (the leaf check never fired) and `@size : Int32` parses as
  `PROPERTY_DECLARATION > INSTANCE_VAR_ACCESS` (the direct-token lookup missed). Both now recognized,
  so Go to Definition on `@name` resolves again.

### Changed

- **Replace deprecated `DefaultLiveTemplatesProvider`** — replaced the deprecated `DefaultLiveTemplatesProvider` class-based implementation with the declarative `<defaultLiveTemplates file="..."/>` extension point, aligning with current IntelliJ Platform API conventions.
- **Replace deprecated `supportsPossessiveQuantifiers()`** — updated `CrystalRegExpLanguageHost` to use the new `supportsPossessiveQuantifiers(RegExpElement)` overload, replacing the deprecated no-args version.
- **Replace deprecated `FileChooserDescriptorFactory.createSingleFileDescriptor()`** — migrated to `singleFile()` and `singleFile().withExtensionFilter()` across settings, project wizard, and run configuration.
- **Replace deprecated `addBrowseFolderListener(title, desc, project, descriptor)`** — migrated to `TextBrowseFolderListener(descriptor.withTitle(...).withDescription(...), project)` pattern.
- **Replace deprecated `ReadAction.run()`** — migrated to `ReadAction.runBlocking()` in Find Usages handler.
- **Replace deprecated `GeneratorPeerImpl.getComponent()`** — moved panel construction logic into `getComponent(TextFieldWithBrowseButton, Runnable)`.
- **Remove code style settings page** — `crystal tool format` is the canonical Crystal formatter with zero configurable options; a Code Style page with inert settings would mislead users. Removed the `langCodeStyleSettingsProvider` EP registration, `CrystalCodeStyleSettingsProvider`, and all associated tests and specs. The `CrystalFormattingService` (Ctrl+Alt+L → `crystal tool format`) remains unchanged.

### Fixed

- **Go to Definition / Completion broke for the entire standard library** — `additionalLibraryRootsProvider` (which loads the Crystal stdlib as an indexed `SyntheticLibrary` so stdlib PSI/types resolve and navigation works) had been disabled to stop an infinite workspace-model save loop. The loop was caused by `CrystalStdlibLibraryProvider.isCrystalProject()` reading the workspace model (`ModuleManager`/`ModuleRootManager`) from inside `getAdditionalProjectLibraries`, which the platform invokes under the write-intent lock — re-entering the model caused the loop. The provider now detects Crystal projects with model-free checks only (`shard.yml` / a `.cr` child in the project base path) and returns a stable, correctly `equals`/`hashCode`-ed `SyntheticLibrary`, so stdlib navigation is restored without the loop.
- **Stdlib Go to Definition landed on whitespace / unresolved for indented and nested definitions** — the bounded stdlib text-scan (`buildStdlibData`) recorded each definition's offset at the *whole-match* start (the leading whitespace of the line, because the regex begins with `^\s*`) instead of the *name identifier* start, so `materialize` (`findElementAt`) returned a `PsiWhiteSpaceImpl` (or a keyword) rather than the definition. It also keyed nested types wrong: `struct Info` inside `class File` was stored as `Info::Info` instead of `File::Info`, so qualified navigation (`File::Info`, `File::Type`, …) missed entirely. Offsets now use the captured name group's start, and the namespace stack stores full qualified names (`File::Info`), so `File::Info` resolves to `struct Info` in `file/info.cr` and indented methods/enums/structs resolve to their real definition.
- **Stdlib background cache crashed / blocked the IDE** — the stdlib symbol cache is built by walking all ~2154 stdlib files and parsing each one. `buildStdlibData` now wraps each file's `PsiManager.findFile` + PSI walk in its own short `ReadAction`, so it never holds a multi-minute read lock (which previously starved indexer writes and hung the IDE on the first Ctrl+Click). The background warmup (`CrystalStdlibCacheWarmup`) now runs on a dedicated low-priority daemon thread so it never freezes the UI.
- **False "Unterminated char literal" on `'-'` / `'0'` inside string interpolation** — the lexer's interpolation/macro-interpolation/macro-control states did not recognize `CHAR_LITERAL`, so valid char literals inside `"...#{...}"` were emitted as `BAD_CHARACTER` and flagged by `CrystalSingleQuoteStringInspection`. Added `CHAR_LITERAL` recognition to all three interpolation states.
- **Parse errors on `case result = ... in ...`** — Crystal's pattern-matching `case <expr> in <pattern>` syntax (with an assignment in the case head) was not accepted. `case_statement` now accepts `[assignment | expression]` as the head, matching `case x = compute in Tuple(Int32, Bool)`.
- **Find Usages / Go to Definition for project constants** — `DEFAULT_CREATE_PERMISSIONS`, `Math::PI`, etc. were not valid Find Usages targets and could not be navigated to. `CrystalConstantAssignment` is now a `CrystalNamedElement` backed by a `CrystalConstantAssignmentStub`, indexed via a new `CrystalConstantIndex`, and resolved by `CrystalReference` + the stdlib cache. (Stdlib constants continue to resolve via the bounded stdlib cache; project constants resolve via the index.)
- **Go to Definition for instance calls on an untyped receiver** — `rule.auto_fixable?` (where `rule` is an untyped parameter) now navigates to the project method definition. `CrystalDotCallReference` falls back to a project-scoped name-only lookup (via `CrystalMethodIndex`, which contains only project methods since stdlib files are not stubbed) when the receiver type is unknown. This enables local navigation without the cross-project/stdlib false positives the strict design otherwise forbids.
- **Block parameter rename/reference resolution** — block parameters (e.g. `|ola|` in `each do |ola| ... end`) are now resolved as references, so Rename (from definition or usage site), Go to Definition, and Find Usages work and stay in sync across the block body. Previously they were only highlighted (Annotator) and suggested (Completion) but had no reference link, so renaming one occurrence did not rename the other. Fixed by teaching `CrystalReference.resolveLocal()` to check `CrystalBlock.parameterList`, mirroring the existing method/macro parameter resolution.

- **Instance/class variable `@` completion** — typing `@` (or `@@`) inside a method now suggests all instance (`@name`) and class (`@@name`) variables of the enclosing class, and the auto-popup appears as the sigil is typed. Previously a bare `@`/`@@` produced no suggestions because the lexer emits a standalone `AT` token and the default prefix matcher ignored the sigil; variables defined in a different method (e.g. `initialize`) were also missing because collection was limited to the current method. Fixed by a sigil-aware prefix matcher, file-level class variable collection (walks all file children including raw `INSTANCE_VAR`/`CLASS_VAR` tokens from error-recovery parse states), nested-class isolation, and auto-popup triggering via `TypedHandlerDelegate.checkAutoPopup`.

- **Stdlib constant navigation (uppercase / non-home-file)** — `DEFAULT_CREATE_PERMISSIONS` and `Math::PI` now resolve into the stdlib. The bounded stdlib fallback scan was previously gated to lowercase method names only, so constants (which have no canonical home file, e.g. `DEFAULT_CREATE_PERMISSIONS` lives in `file.cr`; or live in a subdirectory, e.g. `Math::PI` in `math/math.cr`) were skipped. The fallback now runs for any precise per-file miss.

- **Argument-count inspection for bare commands** — `greet "Hans"` (bare command call) now triggers the missing-required-argument check. Bare commands (`CrystalBareCommandExpression`) were not dispatched to the inspection, so required-argument errors on bare calls were silently missed.

- **Stdlib type aliases resolve even when their file has a localized parse error** — `Bytes` (alias of `Slice(UInt8)` in `slice.cr`) and similar aliases/enums/classes now navigate correctly. Previously a single unsupported construct elsewhere in a large stdlib file (e.g. `raise Foo` as a `||` operand, which the grammar cannot represent without risking an exponential backtracking freeze) cascaded into a parse error that hid every definition after it, so the alias node was never produced. `buildStdlibData` now runs a tolerant text-based pass over each stdlib file's raw text, discovering top-level `alias`/`class`/`struct`/`module`/`enum`/`lib`/SCREAMING_SNAKE constant definitions and mapping them to the PSI leaf at that offset. This fills only the keys the PSI walk missed, never overriding a correctly-parsed result.

- **`require "..."` navigation** — Ctrl+Click / Ctrl+B on a `require` path now jumps to the target `.cr` file. Resolution mirrors Crystal's require expansion: relative requires (`require "./foo"`, `require "../x/y"`) resolve against the requiring file's directory; bare requires (`require "json"`, `require "compiler/crystal/syntax"`) are searched in the project's own `src/` and root, then installed shards (`lib/<shard>/src`, handling both `src/<rest>.cr` and `src/<shard>/<rest>.cr` layouts), then the Crystal standard library. Candidates tried per base are `p.cr`, `p/index.cr`, and `p/<last-segment>.cr`.

- **Return-type annotation navigation** — the type in a method's return annotation (`def foo : String`) is now a navigable reference (clicking `String` jumps to the `String` class; `def foo : Bytes` jumps to the `Bytes` alias). Previously the type-path reference was suppressed whenever the type_path's grandparent was a named definition (e.g. the enclosing `def`), which incorrectly hid return-type and supertype-clause types. Only the definition's own name (parent is a `CrystalNamedElement`, e.g. `class Foo`, `alias Bytes`) is still suppressed to avoid self-references.

- **Whole-stdlib parse coverage** — reduced parse errors across the full Crystal standard library (2154 files) from ~509 to ~226 files by closing the following grammar gaps:
  - **C-binding `lib` blocks** — untyped C-function parameters (`fun strerror_r(Int, Char*, SizeT)`), string external aliases (`fun ceil_f32 = "llvm.ceil.f32"(...)`), and C type aliases (`type DIR = Void`) now parse.
  - **C function-pointer aliases** — `alias Handler = WCHAR*, WCHAR*, UInt -> Int` (multiple types plus an arrow return) now parse.
  - **`record` definitions** — `record X, a, b do ... end` (block body) and `private record ...` / `private enum ...` / `protected getter ...` (visibility modifiers) now parse.
  - **Property macros** — `getter`/`setter`/`property` and their `?`/`!`/`class_*` variants (e.g. `getter host : Type = default`, `protected getter cache : Hash(...)`) now parse as a dedicated `property_macro` rule instead of bare command calls. Instance-variable Go to Definition was updated to recognize `CrystalPropertyMacro` so `@name` still resolves to a `getter name` declaration.
  - **Proc/function types** — `(Int32 -> Void)` in parameter and return positions parse via the existing `type_reference ::= type_union [ARROW type_union]` rule (the redundant `proc_type` rule was removed).
  - **Multi-alternative unions** — `alias X = A | B | C | D` in parameters/struct members no longer exhaust GrammarKit's fixed `MAX_RECURSION_LEVEL`, by making `type_union` a flat `type_union_member` chain (constant recursion depth) while keeping tuple/named-tuple shapes via the full `type_reference`.
  - **Chained and conditional assignments** — `a = b = c`, `return unless last = other.value.@tail`, and `x = &.as(IO)` (shorthand block with a type-cast argument) now parse.
  - **Type-cast shorthands** — `.as(T)` / `.as?(T)` / `.is_a?(T)` accept a *type* argument (e.g. `.as(self*)`, `.as(self*?)`), so pointer types like `self*` parse; `SELF STAR` is accepted as a type.
  - **Setter via macro interpolation** — `def {{name}}=(value)` (macro-generated setter name) and single-line `def` without an explicit `end` (valid only inside macro bodies) now parse by making the method body optional.
  - **Symbols with `?`/`!`/`[]`/`()`** — `:pos=`, `:closed?`, `:[]`, `:()` now lex as `SYMBOL`.

- **Misc parse coverage (stdlib sweep)** — closed the remaining high-frequency parse gaps, taking whole-stdlib files-with-errors from ~226 down to ~138:
  - **`record` with `do` body / nested `def`** — `record X, a : T do ... end` now parses (the `do` block and any `def`/`macro` inside it were previously unparsed). `record` with no body (`record RecursiveDirectories`) still parses.
  - **`def`/`macro` as a statement** — a `def`/`macro` inside a `do` block, `record` body, or other statement position now parses (previously only top-level/class-member `def` was accepted).
  - **Multi-value `return`/`break`/`next` and multi-arg `yield`** — `return a, b`, `break x, y`, `yield a, b, c` now parse (tuple-style returns / multiple yield args).
  - **`property`/`getter`/`setter` with keyword or parenthesized names** — `property next : T` (`next` is a reserved word used as a property name in the stdlib) and `getter(event_loop : T) do ... end` (name wrapped in parens) now parse.
  - **Shorthand blocks with args/operators** — `&.method { |x| ... }` (shorthand block taking a block) and `&.=== '0'` / `&.unsafe_each { ... }` (operator/regular method shorthand) now parse, fixing many `each`/`map`/`synchronize` call sites.
  - **`Type.new` type-as-receiver** — `Hash(K, V).new`, `Pointer(T*).malloc`, `Container(U).new`, `Pair(B, A).new` now parse. New `type_receiver_expression` rule (`type_path type_arguments &DOT`) lets a generic type be a method-call receiver; the `&DOT` lookahead keeps an ordinary call `Foo(x)` from being misread as a type.
  - **`fun name = external(params)`** — C-binding `fun set_dll = LLVMSetDLL(global : ValueRef, x : Int32)` already parsed correctly inside `lib` (confirmed); no grammar change needed.
  - **Leading-`::` namespace values in bare named arguments** — `getsockopt optname, 0, level: ::Socket::Protocol::TCP` (socket.cr) now parses. The bare-expression tower has no leading-`::` primary, so a new `named_bare_value ::= namespace_access bare_postfix_op* | bare_expression` alternative accepts it after the `name COLON` anchor (the anchor makes stealing `CONSTANT::...` tails impossible — verified: `NamespaceAccess::Inner.inner_method`, `IO::Memory.new`, `"#{Foo::Bar.method}"` keep their expression shape; a positional alternative was tried first and regressed all three). Chains ride `bare_postfix_op*` (flat `NAMESPACE_ACCESS` siblings, same as expression context). New `BareNamespaceValue` golden test; whole-stdlib currently 118 files / 134 errors across 2172 files.
  - **`record` without `do` no longer eats the enclosing definition's `end`** — `record EndOfRequest` inside `module HTTP` consumed the module's `end` (the DO-less `class_body END` alternative matched an empty body + outer `end`), breaking all of http/common.cr past that point. The body now requires `DO` (verified with the real compiler: a DO-less indented `def` after a record is a separate top-level method — `R.new(5).foo` fails with "undefined method 'foo' for R"). Extended `RecordWithDoBlock` golden.
  - **Keywords as names** — keyword call labels (`for: STDIN` — process.cr), `of`/`for` as parameter names (`def self.map(values, of = nil, &)`, `def f(for dst_io : IO)`), `of` as a local/condition target (`if of = node.of`), and `|when|` block params now parse. `named_argument` and `parameter` (via new `param_name`) accept `keyword_as_record_field`; `variable`/`variable_reference` accept `OF`/`WHEN`. A `!WHEN` guard on `statement` keeps `variable_reference`'s new `WHEN` from swallowing the next `when` clause (`when 0 then @x` broke with `got 'then'`). New `KeywordAsName` golden test.
  - **Operator and special method names** — `def !~` (object.cr), ``def ` `` backtick name (process.cr, via `afterDef` lexer state mirroring the `def %` rule), `def annotation` (annotatable.cr, added to `keyword_as_method`), and wrapping `def &{{op.id}}` (primitives.cr, `AMPERSAND macro_interpolation` + `operator_method_name` before `keyword_as_method` in `method_name` per the PEG longer-first rule — bare `&` matched first and left `{{...}}` dangling). New `OperatorMethodNames` golden test.
  - **Shorthand `&.` with keyword methods** — `a.try(&.def)`, `list.map(&.def)`, `try(&.when)`, `try(&.annotation(x))` now parse (`implicit_object_call` uses the full `keyword_as_method` set instead of the old ad-hoc list; verified `foo .{{x}}`/`foo(&.{{x}})` are real syntax errors, so `macro_interpolation` was deliberately dropped from the name set — it had let the bare tower steal `{{a}}.{{b}}` chains). New `ShorthandKeywordMethods` golden.
  - **`union`/`type` as identifiers** — `union.union_types`, bare `union patterns` call, `type` as a local now parse (`UNION`/`TYPE` in `variable`/`variable_reference` + `UNION` as a bare-command callee; single tokens, fail fast elsewhere). New `UnionTypeAsIdentifier` golden.
  - **`include`/`extend` inside `lib`** — `include NodeCommon` in `lib LibXML2` structs now parses (`include_statement | extend_statement` in `lib_member`; distinct leading tokens, no order hazard). New `LibIncludeExtend` golden.
  - **Macro-generated members** — `{{ name.id.upcase }} = {{ i }}` enum members (`enum_constant` accepts `macro_interpolation`) and `fun {{ "foo".id }}(...)` names (`interpolated_name` may start with interpolation; `top_level_fun` uses it; lib `fun` already did). Both verified legal only in macro context — the grammar accepts the union. New `MacroGeneratedMembers` golden.
  - **`{% for a, b in ... %}` multi-target loops + clause bodies** — `for_statement` takes `(COMMA IDENTIFIER)*` and a `for_body` (`statement_list | (when_clause | in_clause | macro_control)+`) so `{% for %}`-generated `in`/`when` branches (token.cr, tracing.cr) parse. New `MacroForMultiTarget` golden.
  - **`@{{ivar}}` / `other.@{{ivar}}`** — new `DOT AT macro_interpolation` alternative in `dot_call_access` (struct.cr; verified `a.@b = 1`/`a.@b(1)` are syntax errors, so no ASSIGN/args tail there). New `IvarInterpolationAccess` golden.
  - **Macro-suffixed method segments** — `LibLLVM.build_{{name}}2(...)` via `interpolated_method_segment` (first in the DOT name set, longer than bare IDENTIFIER). New `InterpolatedMethodSegment` golden.
  - **`return`/`break`/`next` with assignment values** — `return @last = make_fun(...)` (call.cr): the value position tries `assignment` before `expression` in all six statement/expression forms (fails fast without `=`, no shape change). New `ReturnAssignValue` golden.
  - **`!`/`!!` on comparison RHS** — `!!a != !!b`, `a == !b` (restrictions.cr): comparison RHS reaches `not_expression` instead of `range_expression` (both towers; verified no golden shape change — `!=`/`==` RHS with leading `!` previously dangled). New `NotComparisonRhs` golden.
  - **Chained DOT-call assignment** — `point.x_ptr.value = 5.0` via a greedy first alternative in `assignment` (`assignable_head` = receiver + ONE no-args DOT segment + explicit DOT + name + rest + value; plain `a.b = v` keeps its shape through the old alternative — verified by probes). New `ChainedDotAssign` golden.
  - **`rescue` modifier in parens** — `(Process.run(...) rescue nil)` (link.cr): `grouped_expression` takes an optional `RESCUE expression` (narrower than full `postfix_modifier` so `(x) if y` keeps its shape). New `GroupedRescueModifier` golden.
  - **Static/named type indexes** — `UInt8[16]`, `UInt8[LibC::MAX_PATH]`, `NamedTuple(time: Time)` as types via `type_index_args`/`type_index_arg` (`INTEGER | CONSTANT !DOUBLE_COLON | type_reference`; single bare CONSTANT keeps the old raw shape — `Char[SS_SIZE]` golden preserved). New `TypeIndexArgs` golden.
  - **Index misc shapes** — `[] of Int32, T ->` two-type proc arrays stay open (documented gap); `1f32` suffix floats lex as FLOAT_LITERAL (`{DEC_INT} "f" (32|64)`); `$GLOBAL` lexes in INTERPOLATION; `{{ {a: 1} }}`, `{{ (t = 1) ? t : 2 }}`, `{{ list.empty? }}`, `{% x = s.gsub(/a/, "b") %}` need `=`/`{`/`}`/`{{`/`{%`/`^`/`~` tokens in MACRO_INTERPOLATION (added; `<=`-style `{{ @type <= T }}` stays open — needs LTE/GTE tokens). New `IndexMiscShapes` + `MacroInterpTokens` goldens.
  - **`responds_to?` as a plain callee** — `responds_to?(:infinite?)` via RESPONDS_TO in both call-expression callee sets. New `RespondsToCall` golden.
  - **`getter(:sym)` symbol args** — `getter(:break) { ... }` (ast.cr): `SYMBOL_LITERAL` in `bare_property_arg`.
  - **Known open gaps (verified legal, need deeper work)** — named args in index (`a[0, foo: 1]`), ternary in index (`a[b ? c : d]` — `range_expression` commits on `b`), space-call paren-first (`foo (1), 2`), bare-in-bare first arg (`exec new_request method, ...`), `union`-call with dot args, `case` without `when` over macro branches, `{% for %}` directly in case (illegal bare form vs legal `{% begin %}`-wrapped form).
  - Whole-stdlib: 118 files / 134 errors → 85 files / 95 errors across 2172 files.



- **External `lib` definitions navigate** — `LibC` (and other `lib LibC` declarations) now resolve to the external-lib definition, captured by the tolerant stdlib text scan (the `lib` keyword is included alongside `class`/`struct`/`module`/`enum`).

- **Instance-variable resolution to getter/setter/property macros** — `@name` now navigates to `getter name : String` (and `setter`/`property`). The instance-var finder only recognized parenthesized method calls, not bare commands, so macro declarations were missed.

- **Stdlib parse errors on `record` macros, `lib` constants, `{% ... %}` as RHS, and `::`-qualified calls** — these previously produced top-level parse failures that emptied the file's StubIndex and broke every definition inside. `record Name(T), field : Type = default` is now parsed as a `record_definition` (so `array.cr`, `hash.cr`, `string.cr`, etc. parse cleanly); `lib LibC` blocks now accept `CONSTANT = value` constant assignments and `CONSTANT`-named fields; `{% if ... %}` / `{% ... %}` are now valid expression-level (constant/assignment RHS) constructs; `type_reference` array sizes now accept `CONSTANT` (e.g. `Char[TLS_SIZE]`); and `::method(...)` / `::method arg` (`::`-prefixed method calls and bare commands) are now accepted. The existing `record` features (constructor arg-count/type checks, `new` completion, parameter info, Go-to-`new`, `.new` reference, and documentation) were re-routed to read fields from the new `CrystalRecordDefinition` PSI.

- **Union & receiver type inference** — `CrystalTypeInference.inferType` now returns the full union annotation (`Int32 | Nil`) instead of only the first member, via a new `inferTypeList` that returns every candidate type (union members and receiver-derived types). `CrystalDotCallReference`, `CrystalCompletionContributor`, `CrystalExpressionTypeResolver`, and `CrystalDocumentationProvider` now resolve across all union member types, so a union-typed receiver (`x : Apfel | Banane`) resolves `x.foo` to the method on each member rather than just the first. Receiver-method chains (`x = obj.foo`) propagate the inferred receiver type into the method-return lookup.

- **Stale stdlib PSI no longer crashes Go to Definition** — when the shared project reparses a stdlib file, cached stdlib PSI can become a detached cross-provider element that throws `PsiInvalidElementAccessException` on access. `resolveStdlibSymbol` / `resolveStdlibMethod` and the `CrystalClassIndex` / method / constant lookups now detect and drop such unusable elements and re-resolve a live one (rebuilding the bounded stdlib scan at most once per project), instead of returning a broken element.

- **Namespaced stdlib class navigation** — `Crystal::ASTNode`, `IO::FileDescriptor`, `Crystal::System::Dir`, and other qualified stdlib classes now resolve. The bounded stdlib scan previously keyed classes only by simple name, so any `A::B` reference missed. It now keys classes by full qualified name, and the DOT-call reference prefers the qualified name when resolving a `Receiver.method` call so nested classes land in the right file.

- **Stdlib enum-constant navigation** — `File::MatchOptions::DotFiles` / `DotFiles` now resolve to the enum member definition in `file.cr`. Enum constants (`CrystalEnumConstant`) are collected during the stdlib scan and keyed by simple and full qualified name.

- **Wrong-file navigation for bare method calls** — a bare `exists?` call inside `class File` now navigates to `File.exists?` (`file.cr:233`) instead of `Dir.exists?` (`dir.cr:254`). `CrystalReference.resolve()` now prefers, for bare method-call references whose project index misses, the stdlib method defined on the call site's enclosing class.

 - **Wrong-file navigation for namespace-receiver calls** — `Crystal::System::Dir.info` and `Crystal::System::File.exists?` now navigate to the platform implementation (`crystal/system/unix/dir.cr`, `crystal/system/unix/file.cr`) instead of a same-named simple class. `resolveStdlibMethod` now accepts a fully-qualified receiver class and consults the bounded stdlib scan's `Class#method` keys as a fallback so subfile-defined nested-class methods resolve.

 - **Parse error on `def %(other)` (modulo operator)** — `%` immediately after `def`/`macro` is the modulo operator, not a percent literal. The lexer now uses an `afterDef` state flag (with `yypushback(1)`) to return `PERCENT` in that position, so `def %(other)` and similar operator definitions parse instead of failing and emptying the file's navigation index (e.g. `float.cr`).

 - **`out` parameter / argument modifier** — `def foo(out @x : Int32)` and lib calls like `LibGMP.mpf_init(out @mpf)` now parse. `out` was added to `param_prefix` and to `argument` / `bare_argument` (accepting both `out name` and `out @instance_var`).

 - **Annotation navigation (`@[Deprecated]`, `@[Flags]`, `@[Link]`)** — annotation definitions are now collected by the stdlib symbol scan (and the tolerant text scan), so Ctrl+Click on `@[Flags]` / `@[Deprecated]` / `@[Link]` jumps to the annotation definition in `annotations.cr` instead of resolving to `null` or a coincidental same-named class/enum.

 - **Kernel builtin navigation (`raise`, etc.) jumps to the wrong file** — `raise` (and other top-level kernel methods) now resolve to their canonical definition (e.g. `raise.cr`) instead of the first scanned private `def raise` (e.g. `csv/lexer.cr:148`). The stdlib scan prefers top-level definitions for bare method names, and the tolerant text scan captures column-0 `def` builtins even when their file has an unrelated parse error.

 - **Every segment of a qualified type path is now navigable (`IO::Class::FileDescriptor`)** — `CrystalTypePathMixin` now emits one reference per `::` segment, each keyed by its cumulative qualified name (`IO`, `IO::Class`, `IO::Class::FileDescriptor`) and resolved independently, so Ctrl+Click on any segment jumps to the right definition. (Previously only the first segment was navigable.)

  - **Qualified method definitions and `!`/`?` method-name suffixes now parse** — `def Float64.new!`, `def Foo.bar?`, `def Int32.+(other)`, and similar definitions previously failed to parse (the grammar only allowed `def self.method`), which both produced parse errors and left those methods unindexed for navigation. `method_name` now accepts `type_path DOT ...` receivers and `!`/`?` suffixes, fixing `float.cr` (`def Float64.new!`, `def Float64.new!(value) : Float64`) and many other stdlib files.

  - **`def Type.name(params) : ReturnType` with parens + return type now parses** — follow-up to the qualified-definitions fix above: `def Float64.new!(value) : Float64` still errored (`got ':'`). Root cause was PEG shadowing inside `method_name`: the bare `CONSTANT` alternative matched `Float64` before the longer `type_path DOT ...` alternative was tried, so the definition parsed as a method named `Float64` with `.new!(value)` swallowed as an implicit-object-call body, and the trailing ` : Float64` return annotation had nowhere to attach. The `type_path DOT ...` alternative now precedes bare `CONSTANT` (longer match first, per the PEG rule). `float.cr` went from 1 parse error to 0. New parser test `TypeReceiverDef` (type receivers, qualified `Foo::Bar`, bare uppercase `def Foo`, `def self.qux`).

  - **Index stall fixed: ternary expressions no longer parse exponentially** — the 95-99% "Analyzing project" hang is gone at its root. `expression`/`bare_expression` each had three alternatives sharing one prefix (`or_expression [QUESTION [expression COLON expression]]`), so every expression node was parsed up to 3× (O(3^depth) on nesting). A 2.5KB hash-in-`do`-block-in-hash file (`catalyst`'s `formatters/json.cr`) took 191s to parse (whole catalyst index: 709s, laptop on fire); now 94ms. Single-alternative rules with an optional ternary tail; all 91 parser goldens unchanged (tree shape preserved).

  - **StackOverflowError in BackgroundHighlighter fixed (infinite type-inference recursion)** — opening `printer.cr` (and any file with a self-referential assignment like `x = x.to_s`) killed the highlighter/EDT with `resolveType → inferTypeList → inferFromAssignmentList → inferTypeFromExpressionList → resolveType → …`. The `depth` budget in `inferTypeList` never accumulated because every `resolveType` bridge call restarted at depth 0. `CrystalExpressionTypeResolver.resolveType` now carries a ThreadLocal recursion budget (bails to `null` past 16 levels); self/mutually-referential variables simply infer as unknown. Regression tests `testSelfReferentialAssignmentTerminates` / `testMutuallyReferentialAssignmentsTerminate`.

 - **`type_path` call expressions now parse (`float.cr:179`)** — `Float::Printer::Hexfile(self, UInt32).to_f(str) { nil }` (a `::`-qualified call with arguments) previously errored with `got '('`. `method_call_expression` now accepts a `type_path` receiver with call args and an optional block.

  - **Stdlib reference resolution no longer freezes the IDE on every keypress** — the stdlib symbol cache previously PSI-parsed all ~2154 stdlib files (≈71s) into a `PsiElement` map and cached those elements. On any reparse the cached elements went stale, forcing a 71s rebuild that surfaced as a long "Resolving reference" window on every keystroke. The cache now performs a **text-based scan** (regex over raw file text, no PSI parse — sub-second) storing stable `(relativePath, offset)` locations; the `PsiElement` is materialized fresh from the current VFS on each resolve, so it can never go stale and the rebuild disappears (first scan ≈0.9s, cached resolves ≈14ms).

  - **Stdlib symbol table keys methods under their enclosing class (and expands `getter`/`setter`/`property`)** — a text-scan bug popped the namespace stack on *every* `end` (including method bodies), so any method defined after the first `def` in a class (e.g. `String#upcase`, `Array#size`) was attributed to the wrong or no namespace and could not be resolved. The scan now tracks open/close *kinds*, popping the namespace only on a type-def's closing `end`/`}`. It also expands `getter`/`setter`/`property` macro calls into their generated reader/writer method names, so `Array#size`, `Hash#keys`, etc. resolve. Whole-stdlib dot-call resolution rose from 81% to 83% (actionable unresolved dropped 13%→11%; symbol table 21.5k→24.7k entries).

  - **Go to Definition for `self` and literal/expression dot-call receivers** — `self.method` (both instance and class methods) and literal/expression receivers (`"x".upcase`, `[1,2,3].size`, `{}.keys`) now resolve via `CrystalDotCallReference.findReceiver`. It recognizes the `self` keyword (resolving to the enclosing type) and infers the receiver type for literals/expressions via `CrystalExpressionTypeResolver`, stripping generics (`Array(Int32)` → `Array`) so the class name matches the symbol-table key.

  - **Stdlib parse-health massively improved (825 → 509 files with errors; 1683 → 598 errors)** — a batch of grammar fixes closed the dominant parse-error clusters in the standard library so Go to Definition/completion work across far more system files (especially C-binding `lib` blocks). Changes:
    - `lib` external functions (`fun`) now accept annotation usage (`@[ReturnsTwice] fun`), stdcall name decoration (`fun MessageBox@16`), aliasing (`fun chown = __posix_chown(...)`), keyword names (`fun select(...)`), PascalCase/CONSTANT names (`fun LocalFree(...)`), and macro-interpolated names/aliases (`fun initialize_{{name}}_target_info = LLVMInitialize{{target.id}}TargetInfo`).
    - `{% ... %}` macro-control and `{{ ... }}` interpolation are now valid inside `lib` blocks and as `fun`/type names.
    - Command syntax (`foo a, b`) is now accepted as a general expression (was statement-only), fixing `reader = check_char reader, char` and thousands of similar calls inside assignments/arguments.
    - `dot_call_access` now allows `.@ivar` / `.@@cvar` instance/class-variable access.
    - Type names (class/module/struct/enum/alias/record) accept macro-interpolated segments (`module {{mod.id}}`).
    - All compound assignment operators (`||=`, `&&=`, `^=`, `**=`, `//=`, `<<=`, `>>=`, `%=`, `&=`, `|=`) are now parsed.
    - `delegate` and other macro-generated members remain partially unsupported (lexical edge cases around backtick `||` and symbol `=` suffixes); tracked as known residual gaps.

  - **New whole-stdlib structure extractor (`testBuildStructureJson`)** — emits `stdlib-graph/structure.json`: a per-file map of every type (class/module/struct/enum/lib/alias/record/annotation), method (`def`/`fun`) with its owning type, and call site with resolution against the stdlib symbol table. Call status is `resolved` / `unresolved` / `na` (macro-generated noise). Parse errors per file are merged in from `parse_errors.tsv`. Companion summary in `structure_summary.txt`. Across all 2154 files it reports 6909 types, 22093 methods, 101827 calls (86% resolved). This gives a browsable, file-by-file view of which object/method is defined where and where each call resolves.

## [0.1.17] — 2026-07-05

### Enhancements

- **Keyword block highlighting** — cursor on `if`, `else`, `elsif`, `end`, `begin`, `rescue`, `ensure`, `case`, `when`, `def`, `class`, `module`, etc. now highlights all related structural keywords of the enclosing block (e.g. `if`/`elsif`/`else`/`end`). Uses IntelliJ's `CodeBlockSupportHandler` extension point with `AbstractCodeBlockSupportHandler` and a declarative TokenSet-based tree structure for reliable multi-marker highlighting.
- **Variable hover type info** — hovering over a variable (definition or usage) now shows the inferred type in a two-line popup: `String (Variable)` / `my_variable`. Works for local variables, instance variables (`@var`), and in method arguments (`puts x`, `foo(x)`). Uses `CrystalTypeInference` for type resolution.
- **Numeric type linking** — integer types (`Int32`, `Int8`, `UInt64`, etc.) and float types (`Float32`, `Float64`) are now linked to their parent type documentation (`Int` or `Float`) in hover popups, since they don't have individual documentation pages.
- **Hash/tuple type inference** — hash literals (`{"a" => 1}` → `Hash(String, Int32)`) and tuple literals (`{1, "hi"}` → `Tuple(Int32, String)`) now show detailed type parameters in hover popups.
- **Array type deduplication** — mixed arrays like `[1, 2, 3, "lol"]` now show `Array(Int32 | String)` instead of `Array(Int32 | Int32 | Int32 | String)`.
- **Ternary type inference** — ternary expressions (`true ? 1 : nil` → `Int32 | Nil`) now correctly infer types for variable hover, including complex conditions (`true == true ? 123 : "lol"`).
- **Method return type inference from body** — methods without an explicit return type annotation now have their return type inferred from `return` statements and implicit last-expression returns. E.g. `def foo; return "hi"; end` infers `String`, `def bar(x : Int32); x + 1; end` infers `Int32`. Used by variable hover to show the correct type when the variable holds a method's return value.

### Bug Fixes

- **Fix false argument count with operators in call arguments** — `write_to_second_line(cmd + ".ps1", %Q{text})` no longer falsely reports "Missing required argument(s): 'line'" when the first argument contains a binary operator with a literal (e.g. `+ ".ps1"`, `* 2`, `/ 3`). The `binary_op_lookahead` rule now covers all expression-starting token types, preventing the parser from misinterpreting `var + ".ext"` inside parenthesized calls as a bare method call that greedily consumes subsequent arguments.
- **Fix false "Value assigned never used" with conditional reassignment** — `x = ""; if cond; x = "v"; end; puts x` no longer falsely reports the first assignment as unused. The inspection now recognizes that assignments inside conditional branches may not execute, making the initial value a fallback. Applies to `if`, `unless`, `while`, `until`, `for`, `case`, `select`, and `begin+rescue` constructs.
- **Fix false positive "Too many arguments" on parameter variables** — using a parameter variable in a binary expression (e.g. `count + 87` inside `def dance(count : Int32)`) no longer falsely reports "Too many arguments" because the inspection now checks if the method name resolves to a local variable or parameter before validating argument count against StubIndex methods
- **Fix `::` namespace not recognized inside string interpolation** — `#{Foo::Bar.method}` and `{{ RvmCli::Tools.config }}` no longer cause parse errors; the lexer now produces `DOUBLE_COLON` tokens inside `INTERPOLATION` and `MACRO_INTERPOLATION` states
- **Fix range with omitted start in bracket access** — `arr[..2]`, `arr[...2]`, `arr[1..]`, `arr[..]` etc. now parse correctly; `range_expression` and `bare_range_expression` now allow the left-hand side to be omitted
- **Fix Parameter Info (Ctrl+P) for DOT-calls after method calls** — `puts Tesa.hika<caret>` and `puts Tesa.hika <caret>` now show `hika`'s parameters instead of `puts`'s; the Quick-Check for DOT-call method names now runs before the generic args-holder lookup, and `findMethodNameInLeaves` iterates backwards to find the correct method name when multiple identifiers are present
- **Fix type checking for bare DOT-call arguments** — `puts Foo.bar 123` where `bar` expects `String` now correctly marks `123` as type mismatch; `bare_postfix_op` now allows `bare_argument_list` in addition to `call_args` for DOT-calls, matching `postfix_op` behavior
- **Fix Go to Definition (Ctrl+B) freezing on top-level bare method calls** — `sahne` after `def sahne(bonbon : String) … end` no longer hangs the IDE for 40+ seconds with a "Resolving reference..." popup; `CrystalReference.resolveLocal()` now stops walking up the PSI tree at the containing file boundary instead of climbing into `PsiDirectory` and lazily parsing every sibling file (including `.sh` build scripts via the Shell plugin). `findAssignmentWithName()` also gains a defensive `PsiFile`/`PsiDirectory` guard so a future regression cannot cascade across the project tree. The diagnostic `CRYSTAL RESOLVE #N` logging introduced for this investigation has been removed.
- **Hover documentation for DOT-call methods** — hovering over (or pressing Ctrl+Q on) `Apfel.tanzen`, `a.essen`, or `Senf.new` now shows the target method's signature and doc comment, just like for top-level calls such as `sahne`. `CrystalDocumentationProvider.getCustomDocumentationElement` now falls back to `CrystalGotoDeclarationHandler.getGotoDeclarationTargets` when no `PsiReference` is available on the context element.
- **Unified DOT-call resolution via `CrystalDotCallReference`** — DOT-call identifiers (`Apfel.tanzen`, `a.essen`) now have a real `PsiReference` backed by the new `dot_call_access` BNF rule. The reference resolves via `CrystalMethodByClassIndex` + `CrystalTypeInference` for instance methods. When the receiver type is unknown, resolution returns `null` (no name-only guessing, no false positives). This also gives DOT-call method names proper identifier highlighting in the editor.
- **Parameter hover popups** — hovering over a parameter name (e.g. `bonbon` in `def butter(bonbon : String)` or in the method body `return bonbon`) now shows a parameter-specific popup with type (hyperlinked) and name, instead of the enclosing method's popup. Untyped parameters show `Any` with a runtime evaluation note.
- **Definition hover popups** — hovering over a definition name (e.g. `butter` in `def butter`, `Foo` in `class Foo`) now shows the documentation popup, matching the behavior at call sites.
- **Namespace access Go to Definition and hover** — hovering over intermediate namespace segments (e.g. `Inner` in `Outer::Inner.method`) now shows the class popup and supports Go to Definition. The new `namespace_access` BNF rule creates a real PSI composite with `CrystalNamespaceReference` that reconstructs the full path and resolves via `CrystalClassIndex`. Supports `::Foo` (leading), `A::B` (nested), and `A::B::C` (multi-level) patterns.
- **Disambiguation for nested classes with same name** — `Foo::Sub.space` now correctly resolves to `Foo::Sub`'s `space` method, not `Bar::Sub`'s. References filter candidates by comparing the full qualified name chain (built via `CrystalPsiUtils.buildQualifiedName`) against the expected path. Completion of `Foo::<caret>` shows only types nested inside `Foo` via the new `CrystalClassByEnclosingIndex`. Completion of `Foo::Sub.<caret>` shows only methods from `Foo::Sub`, filtering out methods from other classes with the same simple name.
- **Auto-completion popup for `::`** — typing `::` after a CONSTANT (e.g. `Foo::<caret>`) now triggers the completion popup automatically, matching the behavior of `.` for method completion. No Ctrl+Space needed.
- **Fix postfix `?` after bracket access on method calls** — `RvmCli::Tools.config["default"]?` now parses correctly as a postfix `?` operator. The `expression` and `bare_expression` rules now try the ternary operator first, then fall back to postfix `?`, preventing `?` from being consumed as a postfix operator when a ternary is intended.
- **Fix rescue clause parsing for typed rescue** — `rescue JSON::ParseException`, `rescue SomeError | OtherError`, and `rescue e : SomeError | OtherError` now parse correctly. The `rescue_clause` BNF rule now uses a `rescue_spec` sub-rule that handles all Crystal rescue clause forms: bare, variable binding, typed, variable + type, and union types.
- **Fix implicit object bracket access (`&.[]`)** — `&.[1]`, `&.[]`, `&.[]?`, `&.[1, 2]`, `&.[0..1]`, and `&.[0] = 99` now parse correctly. The `implicit_object_call` BNF rule now supports `DOT LBRACKET argument_list RBRACKET [QUESTION] [assign_op expression]` for bracket-style implicit object calls. `bare_primary_expression` now includes `implicit_object_call` and `bare_argument` now supports `AMPERSAND bare_expression`, enabling `&.method` and `&.[]` as bare arguments (e.g. `match.try &.[1]`).
- **Fix string interpolation in percent literals** — `%(hello #{name})`, `%Q(hello #{name})`, `%r(pattern #{name})`, and `%x(echo #{name})` now correctly parse `#{expression}` as Crystal code with proper `STRING_INTERPOLATION_BEGIN`/`STRING_INTERPOLATION_END` tokens and full PSI tree structure. The `PERCENT_LITERAL` lexer state now has a `#{` rule that transitions to the `INTERPOLATION` state (guarded by a `percentInterpolation` flag). Non-interpolating forms (`%q`, `%w`, `%i`) remain unaffected. `percent_literal_content` in the parser now includes `STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END`.
- **Fix string interpolation in regex literals** — `/hello #{name}/` and `/pattern #{expr}/i` now correctly parse `#{expression}` as Crystal code. New `REGEX` lexer state handles `#{` → `INTERPOLATION` transitions, escape sequences, and closing `/` with flags. `regex_expression` in the parser now supports `REGEX_BEGIN (content | interpolation)* REGEX_END`.
- **Fix string interpolation in backtick command literals** — `` `echo #{name}` `` and `` `cmd #{arg1} #{arg2}` `` now correctly parse `#{expression}` as Crystal code. New `BACKTICK` lexer state handles `#{` → `INTERPOLATION` transitions, escape sequences, newlines, and closing backtick. `command_expression` in the parser now supports `COMMAND_BEGIN (content | interpolation)* COMMAND_END`. Non-interpolating `%x(...)` literals remain unaffected via existing `percentInterpolation` flag.
- **Type-path Go to Definition for qualified types** — `IO::FileDescriptor` in a `class Foo < IO::FileDescriptor` superclass clause (and any `type_path` reference such as `Bytes` or `MatchOptions::DotFiles`) now navigates to the correct qualified class. `CrystalTypePathMixin` now produces a reference spanning the whole type-path and using the full qualified name (e.g. `IO::FileDescriptor`) instead of only the last segment — so clicking either `IO` or `FileDescriptor` lands on `IO::FileDescriptor`, not the bare `IO` class.
- **Grammar: `return`/`break`/`next` as expressions** — `x || return nil`, `yield || break`, and similar short-circuit expressions whose operand is a control-flow keyword now parse. Added `return_expression`/`break_expression`/`next_expression` rules used in expression (not only statement) position.
- **Grammar: `super`/`previous_def` with a trailing block** — `super { |x| ... }` and `previous_def { ... }` (no parenthesized args) now parse correctly; `method_call_expression` gained a `(SUPER | PREVIOUS_DEF) block` alternative.

### Changed

- **Improved documentation hover format for DOT-call methods and class types** — hovering on `Tesa.hika` now displays `Tesa` (hyperlinked, blue) on the first line and `hika(params) : ReturnType` on the second line, with parameter and return types themselves hyperlinked to their class documentation (e.g. clicking `Foo` opens `Foo`'s class doc in the same popup). Hovering on a class itself (`class Tesa < Object`) links the superclass; a class's own name is not self-linked. Top-level methods show `Object` as the enclosing class (matching RubyMine's Ruby convention). Clicking any link in the popup replaces the popup content with the linked element's documentation via `getDocumentationElementForLink`. Non-resolvable type names render as plain text.
- **Remove FileTypeIndex fallback from Go to Definition** — removed the project-wide `.cr` file scan that caused 90+ second delays on right-click/hover; StubIndex is now the only lookup mechanism for definition resolution

## [0.1.16] — 2026-06-25

### Added

- **Colon spacing inspection** — reports missing space after `:` in type annotations (e.g. `x:Int32` → warning)
- **`@param` shorthand support** — `@param` in method bodies is recognized for completion, type inference, and inspections
- **Record macro support** — `record Config, host : String, port : Int32` works for completion, parameter info (`Ctrl+P`), and argument inspections
- **Type checking for record macro parameters** — validates argument types against record parameters in inspections
- **Parameter completion priority boost** — parameters appear higher in the completion popup with bold styling
- **Keywords as method/macro names** — `macro require(...)`, `def if(...)`, `def self.require(...)` etc. now parse correctly; all Crystal keywords are valid as method names
- **Annotation definitions inside module/class bodies** — `annotation GeneratedWrapper ... end` inside `module`/`class` now parses correctly
- **Global namespace prefix in expressions** — `::Bytes.new(...)`, `::Foo::Bar.new(x)` etc. now parse correctly
- **Overloaded methods in completion** — multiple overloads of the same method (e.g. `ENV.fetch` with 3 different signatures) now appear as separate entries in the code completion popup, each showing its parameter signature

### Bug Fixes

- **Fix rename prefix handling for instance/class variables** — renaming `@var` to `@new_name` or `@@var` to `@@new_name` (with explicit prefix) now correctly preserves the variable type instead of creating wrong prefixes (`@@` for instance vars or corrupted names for class vars)
- **Fix macro body depth tracking for postfix control flow** — `if`/`unless`/`while`/`until` used as postfix modifiers (e.g. `return x if condition`) no longer cause the macro body `end` detection to be off-by-one, which broke parsing of code after macros containing postfix control flow
- **Fix type check and parameter info for DOT-calls on constants** — `ENV.fetch("PER_PAGE", "25").to_i` no longer shows false positive type mismatch; both type check inspection and `Ctrl+P` parameter info now filter methods by the receiver's class/module, preventing stdlib calls from showing params of unrelated user-defined overloads
- **Fix false positive "Unknown named argument" on class constructor DOT-calls** — `::Bytes.new(ptr, length, read_only: true)` no longer falsely reports `read_only` as unknown; both argument count and type check inspections now filter by receiver class/module for DOT-calls
- **Fix `case ... end.tap do` parse error** — `case`/`when`/`end` followed by a method call with block (e.g. `.tap do`) now parses correctly; `case_statement` is now processed through `expression_statement` to support postfix method calls with blocks
- **Fix `?`/`!` suffix after macro interpolation** — `{{ expr }}?` and `{{ expr }}!` (Crystal method name suffixes) now parse correctly; the lexer consumes `?`/`!` immediately following `}}` as part of the interpolation end token
- **Fix multi-level pointer types in lib bindings** — `BaseInfo***`, `UInt8**` etc. (arbitrary pointer depth) in `fun` parameter types now parse correctly; type suffix now allows multiple `*`/`**` tokens
- **Fix postfix modifier after compound assignment** — `@data[key] += 1 if condition` now parses correctly; `expression_statement` now allows `postfix_modifier` after `expression_assign_suffix`
- **Fix greedy IDENTIFIER consumption in argument and bare_argument rules** — `Vector2D.new(x * scalar, y * scalar)` now correctly parses as two separate arguments; binary expressions like `x * scalar` inside calls are no longer misparsed as bare method calls with binary args
- **Fix splat prefix detection in argument extraction** — only the first child node is checked for splat prefix, preventing false positives on wrapped arguments
- **Fix record macro parameter priority** — record macro parameters are now checked before class `initialize` in inspections, preventing false "wrong argument type" errors
- **Fix colon spacing inspection scope** — only scans within method definitions, not the entire file body
- **Fix binary `+`/`-` parsed as bare method arguments** — `+`/`-` followed by an identifier no longer parsed as bare method call arguments
- **Fix Go to Definition on `.new`** — `Senf.new` now jumps to the correct target following Crystal's constructor resolution order (`def self.new` → `record` → `def initialize`), instead of showing every `new` method project-wide

### Changed

- **Method lookup elements use PSI object identity** — `LookupElementBuilder.create(method)` replaces `LookupElementBuilder.create(name)`, enabling IntelliJ to distinguish overloaded methods with the same name
- **Force Re-index button fixed** — now properly removes the library before re-adding it, ensuring stale stub index data is cleared before fresh indexing
- **Force Re-index shows progress** — background progress bar in status bar with "Removing old index..." / "Indexing..." states, plus balloon notification on completion

### Bug Fixes

- **Fix operator method name resolution** — `def self.[]?` etc. now parse correctly; operator method names (`[]`, `[]?`, `[]=`) are composed from tokens when no IDENTIFIER is found
- **Fix ENV.fetch completion** — all stdlib methods inside `module ENV` are now indexed; the BNF fix for `def self.[]?` prevents cascading parse failures that previously skipped all subsequent methods in `env.cr`

## [0.1.15] — 2026-06-14

### Changed

- **Parameter info (Ctrl+P) for `ClassName.new`** — now shows `initialize` method parameters when calling `new` on a class
- **Type check for `ClassName.new`** — argument type validation now works for `new` calls, resolving to `initialize` parameters

### Bug Fixes

- **Fix `new` resolution using wrong class** — `findTypeByName` now prefers the class from the current file when multiple classes with the same name exist in the project

## [0.1.13] — 2026-06-14

### Added

- **Code folding for multi-line arrays and hashes** — `[...]` and `{...}` blocks spanning multiple lines can now be collapsed, matching RubyMine behavior
- **Improved code folding for all block constructs** — keyword and signature remain visible when collapsed, placeholder shows ` ... end` (or `[ ... ]` / `{ ... }` for arrays/hashes), matching RubyMine behavior
- Supported: `if`/`unless`/`while`/`until` (with condition), `def` (with name + params), `class`/`module`/`struct`/`enum` (with name), `begin`, `do`, `case`/`for`, `macro`, `lib`, `annotation`, `verbatim do`

### Changed

- **Suppress completion after numeric literals** — typing a number (e.g. `a = 1`) no longer triggers variable/method suggestions, matching RubyMine behavior

### Bug Fixes

- **Fix nil-safe index (`?`) breaking parser** — `item["states"]?` inside `each do`/`if` blocks no longer causes false parse errors; the bracket access was incorrectly parsed as a method call with array literal

## [0.1.12] — 2026-06-12

### Changed

- **Minimum IDE version** — raised `sinceBuild` from 251 to 261 (IntelliJ 2026.1+) to match the target platform

## [0.1.11] — 2026-06-12

### Bug Fixes

- **Improved debugger compatibility on Windows** — added Windows-specific lldb-dap binary discovery, `.exe` suffix for compiled binaries, and fixed formatter path handling for Windows

## [0.1.9] — 2026-06-12

### Bug Fixes

- **Windows support for debugger** — patched `crystal_formatters.py` to run on Windows, enabling the debugger on Windows as well

## [0.1.6] — 2026-06-08

First official release of the Crystal Language Plugin. This is an early beta (Proof of Concept) providing comprehensive Crystal language support for JetBrains IDEs without requiring an external language server.

### Parser

- **GrammarKit BNF parser** — full Crystal syntax coverage including classes, modules, structs, enums, methods, macros, control flow, and expressions
- **Generics** — variadic generics (`*T`), default types (`T = Int32`), bounded generics, and `forall` constraints
- **Macros** — full macro body parsing with `{% %}` / `{{ }}` / `{% for %}`, hooks (`inherited`, `included`, `extended`, `finished`, `method_added`, `method_missing`), fresh variables (`%fresh_var`), and `verbatim` blocks
- **Union types** — type annotations (`Int32 | String`) and union type resolution in expressions
- **Proc/Lambda** — types (`-> Int32`, `Proc(Int32, String)`) and literals (`->{ }`, `->(x) { }`)
- **Pattern matching** — `case...in` with tuple destructuring (`in {x, y}`), pin operator (`^var`), and guard clauses (`in pattern if cond`)
- **Lib bindings** — `fun`, `union`, `enum`, external variables (`$errno`), varargs, and top-level `fun`
- **Annotations** — usage parsing (`@[Deprecated]`, `@[JSON::Serializable]`), multiple annotations on parameters
- **Operators** — wrapping operators (`&+`, `&-`, `&*`, `&**`), ternary (`? :`), suffix if/unless/while, `responds_to?`, `is_a?`, `nil?`
- **String handling** — interpolation as nested expressions, heredocs, percent literals (`%w[]`, `%i[]`, `%x()`), regex
- **Parameters** — type restrictions, default values, splat (`*args`), double splat (`**kwargs`), external parameter names (`def move(to destination : String)`), block-pass (`&block`)
- **Other** — `asm` blocks, named tuples, `select` statement (concurrency), `with...yield` blocks, `pointerof`, `offsetof`, `uninitialized`, `loop do`, `previous_def`, `out` parameters
- **Error-tolerant** — pin/recovery rules ensure the parser works with incomplete code while typing

### Syntax Highlighting

- **60+ keywords** — all Crystal keywords including reserved words, pseudo-variables (`self`, `typeof`, `_`), and special literals
- **Operators** — full operator support including compound assignment, range, bitwise, and comparison operators
- **Strings** — syntax highlighting with interpolation support
- **Heredocs** — highlighted as multi-line string constructs
- **Percent literals** — `%w[]`, `%i[]`, `%r()`, `%x()`, `%q()`, `%Q()` with correct delimiter matching
- **Symbols** — `:symbol` and `:"string symbol"` highlighting
- **Regex** — `/pattern/` with character classes and quantifiers
- **Annotations** — `@[Annotation]` syntax highlighting
- **Macros** — `{% %}` and `{{ }}` highlighted differently from regular code

### Semantic Highlighting

- **PSI annotator** — visually distinguishes variables, methods, types, parameters, and macro fresh vars
- **Instance variables** — `@name` styled differently from local variables
- **Class variables** — `@@name` with distinct styling
- **Constants** — recognized and highlighted as types

### Code Intelligence

#### Navigation

- **Go to Definition** (Ctrl+B / Ctrl+Click) — jump to class, module, struct, enum, method, and variable declarations across the entire project
- **Go to Symbol** (Ctrl+Alt+Shift+N) — find any symbol in the project via StubIndex
- **Go to Class** (Ctrl+N) — find classes, modules, structs, enums
- **Find Usages** (Alt+F7) — locate all references to methods, classes, instance variables (`@name`), and class variables (`@@name`)
- **Structure View** — PSI-based tree showing classes, modules, methods, macros, constants, and nested definitions
- **Parameter Info** (Ctrl+P) — method signatures at call sites; supports parenthesized calls, bare calls, dot-calls, class method calls, and overloads; works correctly with cursor after comma

#### Code Completion

- **Dot-completion** — context-aware suggestions on classes (static methods) and variables (instance methods via type inference)
- **Free-text completion** — classes, methods, locals, and stdlib types
- **Type completion** — after `:` in annotations, inside generics (`Array(<caret>)`), and in union types (`String | <caret>`)
- **Stdlib types** — built-in Crystal standard library types included in completions

#### Type Inference

- **Basic type deduction** — variable type inferred from assignment (`x = Klasse.new`) and parameter annotations (`x : Type`)
- **Instance variable type** — inferred from `@name` declarations in the class

### Code Quality

- **Type checking** — validates argument types against method parameter annotations; supports numeric autocasting, union types, nilable types, overloads, named args, splat skip
- **Argument count validation** — reports missing required arguments and excess arguments; supports named args, splat/double-splat, block params, default values, overloads, DOT-calls, bare calls
- **Unused variable detection** — warns on local variables that are assigned but never read; supports reassignment analysis, compound assignments, underscore-prefix convention, variables in method call expressions, and string interpolation
- **Lib fun type annotation** — reports missing type annotations in lib function definitions (ERROR level)

### Editor Features

- **Code Formatting** (Ctrl+Alt+L) — delegates to `crystal tool format` via stdin/stdout; no configuration needed
- **Rename Refactoring** (Shift+F6) — in-place rename with preview dialog and automatic compiler verification (`crystal build --no-codegen`)
- **Smart Enter** — automatically inserts `end` after def/class/module/if/do/unless/rescue and handles correct indentation
- **Code Folding** — collapse methods, classes, blocks, comments, and heredocs
- **Brace Matching** — highlights matching pairs for parentheses, brackets, braces, and do/end
- **TODO Indexing** — Crystal TODO/FIXME comments appear in the TODO tool window
- **Live Templates** — 21 code snippets for common Crystal patterns (def, class, module, struct, spec, describe, it, context, etc.)

### Run & Debug

#### Run Configurations

- **Crystal Run** — run Crystal programs with configurable arguments, environment variables, and working directory
- **Crystal Build** — compile with custom flags (release, static, target, etc.)
- **Crystal Spec** — run specs with file/line targeting and tag filters

#### Test Runner

- **SMTRunner integration** — connected to IntelliJ's test UI for familiar test execution experience
- **Gutter run icons** — run individual specs directly from `describe` and `it` blocks
- **Single-test execution** — via `file:line` targeting for precise test isolation
- **Real-time output** — live parsing of Crystal verbose output with pass/fail/error/pending states
- **Re-run failed tests** — one-click re-execution of failed specs
- **Folder-level running** — run all specs in a directory recursively
- **Navigate to source** — double-click on test node to jump to source location
- **Failure propagation** — parent suites marked as failed when children fail
- **Per-test timing** — execution duration from JUnit XML output
- **Duplicate test names** — handles multiple tests with the same name in different describe blocks

#### Debugger

- **LLDB DAP integration** — full debugging via Debug Adapter Protocol
- **Breakpoints** — set breakpoints with hit counts and conditions
- **Stepping** — step over / into / out of code
- **Variable inspection** — locals, instance variables, globals
- **Expression evaluation** — evaluate expressions during debugging
- **Crystal formatters** — bundled LLDB type formatters for readable variable display
- **Debug both** — supports debugging both `crystal run` and `crystal spec` targets

### Infrastructure

- **StubIndex** — project-wide index for classes and methods (instant navigation even in large projects)
- **Generated files committed** — standard convention for GrammarKit plugins to ensure reproducible builds
- **Error-tolerant parsing** — parser works with incomplete code while typing

### Requirements

- **IntelliJ Platform** 2026.1 or later
- **Crystal** installed and available in PATH (for formatting, compiler verification, and running programs)
- **lldb-dap** (optional) — required for debugging; install via your system package manager

### Compatibility

- **JetBrains IDEs** — IntelliJ IDEA, RubyMine, WebStorm, CLion, and other JetBrains IDEs
- **Incompatible with** — legacy Crystal plugin (`net.kenro.ji.jin.intellij.crystal-2`)

---

*This changelog follows [Keep a Changelog](https://keepachangelog.com/) format.*
