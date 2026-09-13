# Test Conventions

Rules learned while building the suite (895 tests). Follow them for every new test.

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
