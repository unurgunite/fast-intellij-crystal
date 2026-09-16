package io.github.unurgunite.crystal.documentation

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Doc-comment collection (Markdown above a definition) and Markdown rendering.
 * Split out of `CrystalDocumentationProvider` (which exceeded the function budget).
 */
internal object CrystalDocComments {
    /**
     * Collects doc comment lines above a definition.
     * Returns the merged Markdown text, or null if no doc comment exists.
     */
    fun collectDocComment(element: PsiElement): String? {
        val (start, gapNewlines) = skipLeadingGap(PsiTreeUtil.prevLeaf(element))
        // If there were 2+ newlines before the definition, there's a blank line = no doc comment
        if (gapNewlines > 1) return null
        val comments = mutableListOf<String>()
        var current = start
        while (current != null) {
            current = consumeDocLine(current, comments)
        }
        if (comments.isEmpty()) return null
        return comments.joinToString("\n")
    }

    /** Leaf before the comment block plus the blank-line count of the gap. */
    private fun skipLeadingGap(start: PsiElement?): Pair<PsiElement?, Int> {
        var current = start
        // Skip whitespace/newlines directly before element
        var newlineCount = 0
        while (current != null && isWhitespaceOrNewline(current)) {
            if (current.node?.elementType == CrystalTypes.NEWLINE) newlineCount++
            current = PsiTreeUtil.prevLeaf(current)
        }
        return Pair(current, newlineCount)
    }

    /**
     * One backwards doc-block line: appends comment text, skips single-newline
     * gaps; returns the next leaf — or null at a blank line / non-comment content.
     */
    private fun consumeDocLine(
        current: PsiElement,
        comments: MutableList<String>,
    ): PsiElement? {
        if (current is PsiComment || current.node?.elementType == CrystalTypes.LINE_COMMENT) {
            return consumeComment(current, comments)
        }
        if (isWhitespaceOrNewline(current)) {
            return skipSingleGap(current)
        }
        return null
    }

    /** Comment text (without the `#` marker) prepended; null for non-`#` comments. */
    private fun consumeComment(
        current: PsiElement,
        comments: MutableList<String>,
    ): PsiElement? {
        val text = current.text
        if (!text.startsWith("#")) return null
        comments.add(0, stripCommentMarker(text))
        return PsiTreeUtil.prevLeaf(current)
    }

    private fun stripCommentMarker(text: String): String =
        when {
            text.startsWith("# ") -> text.removePrefix("# ")
            text == "#" -> ""
            text.startsWith("#") -> text.removePrefix("#")
            else -> text
        }

    /**
     * Gap between comment lines: 2+ newlines mean a blank line (end of doc
     * block, stop); otherwise the next non-whitespace element (don't advance past it).
     */
    private fun skipSingleGap(current: PsiElement): PsiElement? {
        var cursor: PsiElement? = current
        var nlCount = 0
        while (cursor != null && isWhitespaceOrNewline(cursor)) {
            if (cursor.node?.elementType == CrystalTypes.NEWLINE) nlCount++
            cursor = PsiTreeUtil.prevLeaf(cursor)
        }
        if (nlCount > 1) return null
        return cursor
    }

    private fun isWhitespaceOrNewline(element: PsiElement): Boolean {
        if (element is PsiWhiteSpace) return true
        val type = element.node?.elementType
        return type == CrystalTypes.NEWLINE || type == com.intellij.psi.TokenType.WHITE_SPACE
    }

    fun renderMarkdown(
        markdown: String,
        context: PsiElement,
    ): String {
        val flavour = GFMFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        var html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

        // Strip the wrapping <body> tags that HtmlGenerator adds
        html = html.removePrefix("<body>").removeSuffix("</body>")

        // Enhance Crystal code blocks with syntax highlighting
        html = CrystalDocHtml.highlightCodeBlocks(html, context)

        return html
    }
}
