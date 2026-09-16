package io.github.unurgunite.crystal.documentation

import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.stubs.CrystalClassIndex

/**
 * HTML emission for documentation popups: syntax highlighting, class links and
 * escaping. Split out of `CrystalDocumentationProvider` (which exceeded the
 * function budget).
 */
internal object CrystalDocHtml {
    // Possible total name lengths of sized numeric types ("Int8".."Int128", etc.).
    private val INT_TYPE_NAME_LENGTHS = 4..6
    private val UINT_TYPE_NAME_LENGTHS = 5..7
    private val FLOAT_TYPE_NAME_LENGTHS = 6..7

    /**
     * Returns an `<a>` tag linking to the class documentation, or null if the class
     * is not found in [CrystalClassIndex] (silent omit — callers fall back to plain text).
     */
    fun linkToClass(
        name: String,
        project: Project,
    ): String? {
        val elements =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                name,
                project,
                GlobalSearchScope.allScope(project),
                CrystalNamedElement::class.java,
            )
        if (elements.isEmpty()) return null
        return "<a href=\"psi_element://class:$name\">$name</a>"
    }

    /**
     * Wraps type names in the syntax-highlighted HTML with clickable links.
     * For each uppercase identifier found in the HTML that exists in [CrystalClassIndex],
     * it is wrapped with an `<a>` tag pointing to the class documentation.
     * Uses a word-boundary match (no letter/digit/underscore before or after) to prevent
     * partial matches like `Foo` inside `FooBar`.
     *
     * Special case: Integer types (Int8, Int32, UInt64, etc.) and Float types
     * (Float32, Float64) are linked to their parent type (`Int` or `Float`) since
     * they don't have individual documentation pages.
     */
    fun wrapTypeLinks(
        highlightedHtml: String,
        project: Project,
    ): String {
        // Find all potential type names (uppercase identifiers) in the HTML
        val typeNames =
            Regex("[A-Z][A-Za-z0-9_]*")
                .findAll(highlightedHtml)
                .map { it.value }
                .distinct()
                .filter { name ->
                    isResolvableType(name, project)
                }.toList()

        var result = highlightedHtml
        for (name in typeNames) {
            val linkTarget = resolveTypeLinkTarget(name, project)
            if (linkTarget != null) {
                // Match type name only at word boundaries — no letter/digit/underscore before or after
                // to prevent matching `Foo` inside `FooBar` or `Foo_Bar`
                result =
                    result.replace(
                        Regex("""(?<![a-zA-Z0-9_])${Regex.escape(name)}(?![a-zA-Z0-9_])"""),
                        "<a href=\"psi_element://class:$linkTarget\">$name</a>",
                    )
            }
        }
        return result
    }

    /**
     * Checks if a type name can be resolved (either directly in the class index,
     * or via the numeric type fallback mapping).
     */
    fun isResolvableType(
        name: String,
        project: Project,
    ): Boolean {
        if (classIndexHits(name, project).isNotEmpty()) {
            return true
        }
        return resolveNumericTypeLink(name) != null
    }

    /**
     * Returns the link target for a type name. For numeric types, returns the
     * parent type name (e.g. "Int32" → "Int", "Float64" → "Float").
     * Returns null if the type should not be linked.
     */
    private fun resolveTypeLinkTarget(
        name: String,
        project: Project,
    ): String? {
        // Check direct class index first
        if (classIndexHits(name, project).isNotEmpty()) {
            return name
        }

        // Check numeric type fallback
        return resolveNumericTypeLink(name)
    }

    /** Class-index entries for [name] (empty when the type is not indexed). */
    private fun classIndexHits(
        name: String,
        project: Project,
    ): Collection<CrystalNamedElement> =
        StubIndex.getElements(
            CrystalClassIndex.KEY,
            name,
            project,
            GlobalSearchScope.allScope(project),
            CrystalNamedElement::class.java,
        )

    /**
     * Maps numeric types to their parent type for documentation linking.
     * Int8/Int16/Int32/Int64/Int128 → "Int"
     * UInt8/UInt16/UInt32/UInt64/UInt128 → "Int"
     * Float32/Float64 → "Float"
     */
    private fun resolveNumericTypeLink(name: String): String? =
        when {
            // "Int8".."Int128" (Int64 is 5 chars, Int128 is 6)
            name.startsWith("Int") && name.length in INT_TYPE_NAME_LENGTHS -> "Int"

            // "UInt8".."UInt128"
            name.startsWith("UInt") && name.length in UINT_TYPE_NAME_LENGTHS -> "Int"

            // "Float32", "Float64"
            name.startsWith("Float") && name.length in FLOAT_TYPE_NAME_LENGTHS -> "Float"

            else -> null
        }

    /**
     * Replaces <code> blocks containing Crystal code with syntax-highlighted versions.
     */
    fun highlightCodeBlocks(
        html: String,
        context: PsiElement,
    ): String {
        // Replace <pre><code>...</code></pre> blocks with highlighted Crystal
        val codeBlockPattern = Regex("<pre><code[^>]*>(.*?)</code></pre>", RegexOption.DOT_MATCHES_ALL)
        return codeBlockPattern.replace(html) { match ->
            highlightCodeBlock(match, context) ?: match.value
        }
    }

    /** One code block highlighted, or null when highlighting fails (keep original). */
    private fun highlightCodeBlock(
        match: MatchResult,
        context: PsiElement,
    ): String? {
        val code = unescapeHtml(match.groupValues[1].trim())
        val highlighted = highlightCrystalCode(code, context) ?: return null
        return "<pre><code>$highlighted</code></pre>"
    }

    fun highlightCrystalCode(
        code: String,
        context: PsiElement,
    ): String? {
        val project = context.project
        return try {
            val builder = StringBuilder()
            HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                builder,
                project,
                CrystalLanguage,
                code,
                1.0f,
            )
            builder.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    fun unescapeHtml(text: String): String =
        text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
