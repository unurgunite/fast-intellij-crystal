# ECR (Embedded Crystal) IDE support — PROPOSAL (UNIMPLEMENTED)

> Status: **not implemented**. No `EmbeddedCrystal`/`ECR` code exists in
> `src/main` (verified: zero hits). The tag reference below (§1–5 of the
> original `docs/specs/embedded-crystal.md`, incl. a stray German intro
> sentence) was a language tutorial, not a plugin spec, and has been removed —
> see the [Crystal book / ECR docs](https://crystal-lang.org/reference/latest/syntax_and_semantics/templates.html)
> for the language itself. What remains is the IDE design sketch (§6),
> preserved for future work.

## 6. IntelliJ Platform Integration

This section specifies how the IntelliJ Crystal plugin handles `.ecr` files and their variants within the IDE.

### 6.1 File Type Registration

The plugin registers the following file extensions as the **Embedded Crystal** file type:

| Extension | Example Filenames | Description |
| :--- | :--- | :--- |
| `.ecr` | `template.ecr`, `layout.ecr`, `footer.ecr` | Standalone ECR template file. The base language is Crystal; if the file name does not end in `.html.ecr` or `.ecr.html`, no implicit template data language is assigned. |
| `.html.ecr` | `index.html.ecr`, `show.html.ecr`, `users.html.ecr` | HTML template with embedded Crystal. The Crystal language engine handles `<% %>` tags while the template data language is set to **HTML**, enabling HTML syntax highlighting, completion, and structure view. |
| `.ecr.html` | `index.ecr.html`, `about.ecr.html` | Alternative naming convention — same semantics as `.html.ecr`. The template data language is set to **HTML**. |

All three extensions are registered under a single `EmbeddedCrystal` language and `EmbeddedCrystalFileType`.

**Extension detection behavior:**

| Pattern | How it matches |
| :--- | :--- |
| `*.ecr` | Caught by `extensions="ecr"` — `.ecr` is the final extension. |
| `*.html.ecr` | Caught by `extensions="ecr"` — IntelliJ uses the last dot-separated component as the extension, so `.html.ecr` resolves to extension `.ecr`. No special handling needed. |
| `*.ecr.html` | **Not** caught by `extensions="ecr"` — the final extension is `.html`. Must be associated programmatically via `FileTypeManager.getInstance().associatePattern()` on plugin initialization, or via a custom `FileTypeDetector`. |

#### Implementation Sketch — `plugin.xml` Registration for `.ecr` and `.html.ecr`

```xml
<fileType
    name="Embedded Crystal"
    implementationClass="io.github.unurgunite.crystal.ecr.EmbeddedCrystalFileType"
    fieldName="INSTANCE"
    language="EmbeddedCrystal"
    extensions="ecr"/>
```

#### Implementation Sketch — Programmatic Association for `.ecr.html`

```kotlin
// In a postStartupActivity or the plugin's constructor:
FileTypeManager.getInstance().associatePattern(
    EmbeddedCrystalFileType.INSTANCE,
    "*.ecr.html"
)
```

This associates files ending in `.ecr.html` with the Embedded Crystal file type despite their `.html` final extension.

#### Dedicated Language Definition

ECR templates get their own `EmbeddedCrystalLanguage` (distinct from the `Crystal` language). The parser for this language is purpose-built — it only understands the ECR tag syntax (`<%`, `<%=`, `<%-`, `-%>`, `<%#`, `<%%`). Inside raw text regions between tags, the lexer delegates to a **template data language lexer** (e.g., HTML) when configured, or treats the text as plain text otherwise.

This mirrors how RubyMine defines `ERBLanguage` (not the same as `RubyLanguage`). The Crystal-language features (type inference, references, inspections) do **not** apply to ECR files — only the Crystal code inside `<% %>` blocks is parsed and highlighted as Crystal.

### 6.2 File Icon

Files matching the `.ecr`, `.html.ecr`, and `.ecr.html` extensions receive the **`<%>` icon**, analogous to RubyMine's ERB file icon.

| File Type | Icon Source | Visual |
| :--- | :--- | :--- |
| `.ecr` / `.html.ecr` / `.ecr.html` | `EmbeddedCrystalIcons.FILE` — custom `<%>` glyph | `<%>` |

The icon is a triangular `<%>` glyph that mirrors the ERB icon used by RubyMine for `.erb` files. It conveys "this is a template file with embedded code tags" at a glance.

#### Implementation Sketch — `EmbeddedCrystalIcons.kt`

```kotlin
object EmbeddedCrystalIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/embedded_crystal.svg", EmbeddedCrystalIcons::class.java)
}
```

The icon is registered via the existing `CrystalIconProvider` (or a new `EmbeddedCrystalIconProvider`), which checks for `.ecr` and its variants:

```kotlin
class CrystalIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            val name = element.name
            return when {
                name.endsWith(".html.ecr") || name.endsWith(".ecr.html") || name.endsWith(".ecr") -> EmbeddedCrystalIcons.FILE
                name.endsWith("_spec.cr") -> CrystalIcons.SPEC_FILE
                else -> null
            }
        }
        return null
    }
}
```

Or alternatively registered directly in `plugin.xml` via an `<iconProvider>` extension.

### 6.3 Template Data Language — Implicit HTML Parser

For `.html.ecr` and `.ecr.html` files, the IDE must implicitly activate the **HTML parser** as the template data language. This means:

- HTML tags inside the ECR template receive full HTML syntax highlighting (elements, attributes, attribute values, entities, etc.)
- HTML code completion works inside tag regions (attribute names, tag names, CSS class completion with configured framework support)
- HTML structure view shows the HTML element tree
- HTML inspections (e.g., unclosed tags, duplicate IDs) are active on the raw text portions
- The `<% %>` and `<%= %>` ECR tags are highlighted using the Crystal/ECR highlighting rules within their respective tag boundaries
- Code folding for HTML elements works between ECR tags

This is achieved by registering **`TemplateDataLanguagePatterns`** that associate the file name patterns `*.html.ecr` and `*.ecr.html` with the HTML language:

#### Implementation Sketch — `TemplateDataLanguagePatterns` Registration

```xml
<!-- Registered via com.intellij.templateDataLanguagePatterns extension point -->
<templatesDataLanguage>
    <pattern
        language="HTML"
        pattern="*.html.ecr"/>
    <pattern
        language="HTML"
        pattern="*.ecr.html"/>
</templatesDataLanguage>
```

> **Note:** The `*.html.ecr` pattern uses a double glob — it matches files whose name (after extension stripping) ends in `.html`. For `.ecr.html`, the pattern matches files whose final extension is `.html` and whose prior extension is `.ecr`. Both patterns must be registered explicitly; IntelliJ does not automatically chain extensions.

#### Behavior Summary

| File Pattern | File Type | Template Data Language | RGB Example |
| :--- | :--- | :--- | :--- |
| `*.ecr` | Embedded Crystal | *(none — plain text)* | `footer.ecr` |
| `*.html.ecr` | Embedded Crystal | HTML | `index.html.ecr` |
| `*.ecr.html` | Embedded Crystal | HTML | `index.ecr.html` |

For `.plain.ecr` or other future template data languages (.xml, .json, .css), additional `<pattern>` entries can be added following the same scheme.

#### Visual Structure Example — `index.html.ecr`

The following example shows how HTML and Crystal interact in the IDE's highlighting layers:

```html
<!-- HTML comment — highlighted as HTML -->
<!DOCTYPE html>
<html>
<head>
  <title><%= @page_title %></title>
  <!--              ^^^^^^^^^^^^ Crystal expression — highlighted as Crystal -->
</head>
<body>
  <% if @items.empty? %>
  <!-- ^^ Crystal control keyword — highlighted as Crystal keyword -->
  <!--     ^^^^^^^^^^^^^^^^ Crystal expression — highlighted as Crystal -->
    <p class="empty">No items.</p>
    <!-- ^ HTML tag — highlighted as HTML -->
    <!--     ^^^^^^^^^^^^^ HTML attribute — highlighted as HTML -->
  <% end %>
  <!-- ^^ Crystal end keyword — highlighted as Crystal -->
</body>
</html>
```

### 6.4 Extension Point Registration Summary

The following extension points in `plugin.xml` are required:

| Extension Point | Registration | Purpose |
| :--- | :--- | :--- |
| `com.intellij.fileType` | `EmbeddedCrystalFileType` with `extensions="ecr"`; `.ecr.html` via `FileTypeManager.associatePattern()` | Associates `.ecr`, `.html.ecr`, `.ecr.html` with the Embedded Crystal file type |
| `com.intellij.lang.parserDefinition` | `EmbeddedCrystalParserDefinition` for `EmbeddedCrystalLanguage` | Provides the ECR lexer and parser to the IDE |
| `com.intellij.lang.syntaxHighlighterFactory` | `EmbeddedCrystalSyntaxHighlighterFactory` for `EmbeddedCrystalLanguage` | Provides ECR syntax highlighting (ECR tags + template data language delegation) |
| `com.intellij.templateDataLanguagePatterns` | Patterns `*.html.ecr` → HTML, `*.ecr.html` → HTML | Enables HTML support inside ECR templates |
| `com.intellij.iconProvider` | `CrystalIconProvider` (extended) or new `EmbeddedCrystalIconProvider` | Returns the `<%>` icon for `.ecr` file variants |

This architecture mirrors how RubyMine handles `.erb` files: a dedicated template language (`ERBLanguage`), a file type (`ERBFileType`), template data language patterns mapping `.html.erb` → HTML, and a recognizable `<%>` icon.
