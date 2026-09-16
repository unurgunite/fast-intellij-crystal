# Find Usages from definition names

Clicking on a definition name (e.g., `module Kann`, `class Foo`, `def bar`)
highlights all usages in the current file and enables Rename (Shift+F6) and
project-wide Find Usages (Alt+F7). Previously only the usage side worked.

## Architecture

Two extension points handle different scopes:

| Extension Point | Scope | Triggered By |
|----------------|-------|-------------|
| `HighlightUsagesHandlerFactory` | Current file only | Ctrl+Shift+F7, auto-caret highlighting |
| `FindUsagesHandlerFactory` | Entire project | Alt+F7, Rename (Shift+F6) |

The `CONSTANT`/`IDENTIFIER` leaf inside a definition (e.g., `Kann` in
`module Kann`) has no `PsiReference`, and the platform's default
`TargetElementUtilBase.findTargetElement()` fails because the leaf itself is
not a `PsiNamedElement`. The custom factories detect the definition-name case
and delegate to handlers using `ReferencesSearch`:

1. `CrystalHighlightUsagesHandlerFactory` — detects a `CONSTANT`/`IDENTIFIER`
   leaf whose parent is a `CrystalNamedElement`, creates
   `CrystalHighlightUsagesHandler`.
2. `CrystalHighlightUsagesHandler` — searches the current file
   (`ReferencesSearch.search(target, LocalSearchScope(file))`).
3. `CrystalFindUsagesHandlerFactory` — creates `CrystalFindUsagesHandler` for
   project-wide search and rename.
4. `CrystalFindUsagesHandler` — searches with configurable scope.

## Behavior

| Cursor on | Highlighting | Rename | Find Usages |
|-----------|-------------|--------|-------------|
| `module Kann` (definition name) | All usages in file | Yes | Yes |
| `Kann` (usage) | Definition + all usages | Yes | Yes |
| `class` (keyword) | No highlighting | No | No |
| `def` (keyword) | No highlighting | No | No |
