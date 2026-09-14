package io.github.unurgunite.crystal.highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Suppresses generic parser error highlights when our own annotations/inspections
 * already provide a user-friendly error message for the same issue.
 *
 * This prevents double errors:
 * - Single-quote strings: BAD_CHARACTER inspection shows the good message,
 *   parser shows "<expression> or NEWLINE expected" → we suppress the parser error
 * - Incomplete heredocs: annotator shows "Missing heredoc end delimiter",
 *   parser shows generic syntax error → we suppress the parser error
 *
 * For all other parser errors, the original highlight is preserved.
 */
class CrystalHighlightErrorFilter : HighlightErrorFilter() {
    companion object {
        // How far up the tree to look for a BAD_CHARACTER sibling when deciding
        // whether a parser error is just fallout from an invalid single-quote string.
        private const val MAX_CAUSE_WALK_UP_LEVELS = 3
    }

    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        // This filter is registered globally (no language restriction), so the platform
        // invokes it for every error element in every file. Skip non-Crystal files
        // immediately — otherwise the Crystal-specific tree walk below runs on every
        // parse error in unrelated files (e.g. .groovy), which can stall highlighting.
        if (element.containingFile?.language != CrystalLanguage) return true

        // Suppress parser errors caused by invalid single-quote strings (BAD_CHARACTER)
        if (isCausedByBadCharacter(element)) return false

        // Suppress parser errors caused by missing heredoc end delimiters
        if (isCausedByMissingHeredocEnd(element)) return false

        // Keep all other parser errors visible
        return true
    }

    /**
     * Check if this PsiErrorElement is caused by a BAD_CHARACTER token nearby.
     * We traverse up the tree looking for a BAD_CHARACTER sibling at any level
     * (the BAD_CHARACTER may be a sibling of an ancestor, e.g. inside FILE).
     */
    private fun isCausedByBadCharacter(element: PsiErrorElement): Boolean {
        var current: com.intellij.psi.PsiElement? = element
        // Traverse up to MAX_CAUSE_WALK_UP_LEVELS levels looking for BAD_CHARACTER siblings.
        // The error element itself may BE the BAD_CHARACTER-adjacent leaf's parent chain:
        // for `e = 'hello world'` the PsiErrorElement sits directly under FILE with
        // no BAD_CHARACTER sibling anywhere (the quote lexes inside INTERPOLATION-free
        // STRING state) — so also treat an error whose own text is single-quoted
        // multi-char content as single-quote fallout.
        if (isSingleQuotedText(element.text)) return true
        repeat(MAX_CAUSE_WALK_UP_LEVELS) {
            current = current?.parent ?: return false
            var sibling = current.firstChild
            while (sibling != null) {
                if (sibling.node?.elementType == CrystalTokenTypes.BAD_CHARACTER) {
                    return true
                }
                sibling = sibling.nextSibling
            }
        }
        return false
    }

    /** Multi-char single-quoted text (`'hello world'`) — the invalid-char-literal shape. */
    private fun isSingleQuotedText(text: String): Boolean = text.length > 3 && text.startsWith("'") && text.endsWith("'")

    /**
     * Check if this PsiErrorElement is caused by a HEREDOC_START without matching HEREDOC_END.
     * We look for HEREDOC_START in siblings where there's no HEREDOC_END.
     */
    private fun isCausedByMissingHeredocEnd(element: PsiErrorElement): Boolean {
        val parent = element.parent ?: return false
        var hasHeredocStart = false
        var hasHeredocEnd = false

        var sibling = parent.firstChild
        while (sibling != null) {
            val type = sibling.node?.elementType
            if (type == CrystalTypes.HEREDOC_START) hasHeredocStart = true
            if (type == CrystalTypes.HEREDOC_END) hasHeredocEnd = true
            sibling = sibling.nextSibling
        }

        return hasHeredocStart && !hasHeredocEnd
    }
}
