# Test Conventions

Rules learned while building the suite (958 tests in `test`, 92 parser goldens
run separately via `-PgoldenOnly=true`). Follow them for every new test.

## Suite layout

- `./gradlew test` — full suite minus parser goldens (excluded in
  `build.gradle.kts`) and minus the diagnostic `StdlibGraphToolTest`
  (0 asserts; runnable via manual `stdlibParseErrors` / `stdlibBuildGraph` /
  `stdlibStructure` / `stdlibCheckFile` tasks).
- `./gradlew test -PgoldenOnly=true` — 92 `CrystalParserTest` goldens,
  non-blocking in CI (GrammarKit GPUB memo nondeterminism across tests in one
  JVM; see `docs/reports/wave-9-grammar.md`).

## Fixture files live under `temp://`

`myFixture.addFileToProject` / `configureByText` create files in the in-memory
`temp://` filesystem. `LocalFileSystem` cannot see them. Consequences:

- Tests for path-based APIs (`CrystalTestLocator`, `CrystalStdlibLibraryProvider`)
  must write **real files** (`java.io.File` in a temp dir or `project.basePath`,
  plus `LocalFileSystem.refreshAndFindFileByPath`) instead of fixture files.
- `CrystalStdlibLibraryProvider.isCrystalProject` reads `shard.yml` via
  `LocalFileSystem`, so the marker must be written to the real base dir
  (recreate it with `mkdirs` — the fixture dir may already be cleaned).
- Always clean up real files in `tearDown`.

## StubIndex names must be stdlib-unique

When the stdlib gets indexed in a full-suite run (real Crystal is installed on
dev machines and CI), `allScope(project)` includes library files. A fixture
constant named `DEFAULT_CREATE_PERMISSIONS` collides with the real stdlib
`file.cr` and breaks size assertions. Use unique names (`STUB_WIDGET_CONST_XYZ`,
`StubWidget`, `widget_alpha`) in all StubIndex tests.

## Static caches must be cleared per test

`CrystalStdlibLibraryProvider.libCache` is static and keyed by `Project`, and
the platform itself may query the provider during fixture setup. Call
`clearCache()` at the **start** of each test (not only in `tearDown`), after
arranging settings — otherwise a cached library from setup shadows the test.

## JUnit3 `fail()` returns `Unit`

`?: fail("...")` does not narrow to non-null and breaks `?.` chains. Use
`assertNotNull("...", x)` + `!!` instead.

## `configureByText` takes a bare filename

Paths with `/` (`spec/math_spec.cr`) throw `Invalid file name`. The `_spec.cr`
suffix alone is enough for spec detection — no subdirectory needed.

## `FoldingDescriptor.getElement()` returns `ASTNode`

Not `PsiElement`. No `.node` call needed.

## Structure view renders method signatures

Methods appear as `name(params)` (e.g. `bar()`), not bare names.

## Final classes with protected surface need reflection

`CrystalCodeBlockSupportHandler` is final with protected TokenSet methods and no
behavioral public entry for structure. Structural assertions go through
`getDeclaredMethod(...).isAccessible = true`; behavioral assertions use the
public `getCodeBlockMarkerRanges(element)` with the caret **inside** the keyword
token (an adjacent identifier yields no ranges).

## Query searchers in isolation via direct `processQuery`

`ReferencesSearch.search(target)` also runs the platform's default word search,
which pollutes counts (it re-adds the excluded target). To test a custom
`QueryExecutorBase`, construct `ReferencesSearch.SearchParameters` directly and
call `processQuery` with a collecting processor.

## `RunLineMarkerContributor.Info.tooltipProvider` is `java.util.Function`

Call `.apply(element)`, not `.invoke()`.

## `OccurrenceConsumer` / `IdDataConsumer` are final

No test doubles — use the real `IdDataConsumer` and assert on result masks
(`IN_COMMENTS` bit). `IdIndexEntry.toString()` prints only hashes, so content
matching via constructed entries does not work. The filter lexer needs the test
app (BasePlatformTestCase), not a pure unit test, because TODO counting touches
the extension point.

## Never `waitForSmartMode()` on the EDT test thread

It deadlocks (hung a suite run past the 30-minute timeout). For
dumb-mode-deferred logic (`runWhenSmart`), test the early-return paths through
the public entry and the deferred step directly (reflection if private).

## Never mutate the module model in tests

`ModuleRootModificationUtil.addContentRoot` / `updateModel` in a test leaks
async reindex work into later test classes in the same JVM: 59 highlighting
failures with "PSI changes are not allowed during highlighting" (bisected to
`CrystalSpecSourceRootConfiguratorTest`, which was then rewritten to extract a
pure predicate instead). Module-model-mutating positive tests are banned;
extract pure predicates (`isUnderContentRoot`) and test those.

## Real-file tests must quiesce in tearDown

Tests that create/delete real files (for `LocalFileSystem`-based code) leave
async VFS/index events that break later highlighting tests in the same JVM —
same 59-failure signature as above. After deleting the files, drain
synchronously before `super.tearDown()`:

```kotlin
com.intellij.openapi.vfs.LocalFileSystem.getInstance().refresh(false)
com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
```

Verified by full-suite runs with timestamp checks proving the victims ran
after the file-touching tests.

## Refresh created VFS paths individually

After creating real files for `LocalFileSystem`-based code, call
`refreshAndFindFileByPath` on each created path. Refreshing only the parent
dir does not reliably surface new children to `findFileByPath`.
