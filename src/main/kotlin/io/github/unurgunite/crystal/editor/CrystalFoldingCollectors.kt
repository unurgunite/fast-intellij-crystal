package io.github.unurgunite.crystal.editor

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Stateless fold-region collectors for [CrystalFoldingBuilder].
 * Each collector scans the flat element list once for one foldable shape:
 * keyword blocks (`def`..`end`), bracket pairs (`[`..`]`), comment runs.
 */
internal object CrystalFoldingCollectors {
    /**
     * One matched foldable region: the node owning the fold plus its text range.
     */
    data class FoldEntry(
        val element: PsiElement,
        val range: TextRange,
    )

    /**
     * Adds the entry when it spans multiple lines and is non-empty.
     * Single-line and zero-width ranges are never foldable.
     */
    fun addIfMultiline(
        entries: MutableList<FoldEntry>,
        element: PsiElement,
        range: TextRange,
        document: Document,
    ) {
        if (range.length <= 0) return
        val startLine = document.getLineNumber(range.startOffset)
        val endLine = document.getLineNumber(range.endOffset)
        if (endLine > startLine) {
            entries.add(FoldEntry(element, range))
        }
    }

    /**
     * Stack-based matching of block-start keywords to `end`.
     * Conditional keywords (`if`/`unless`/...) fold from after the condition
     * (first NEWLINE/THEN/SEMICOLON), others from the keyword end.
     */
    fun collectKeywordBlocks(
        elements: Array<out PsiElement>,
        document: Document,
    ): List<FoldEntry> {
        val entries = mutableListOf<FoldEntry>()

        data class OpenEntry(
            val element: PsiElement,
            val foldStartOffset: Int,
        )
        val startStack = mutableListOf<OpenEntry>()

        var i = 0
        while (i < elements.size) {
            val element = elements[i]
            val tokenType = element.node?.elementType
            if (tokenType == null) {
                i++
                continue
            }
            if (tokenType in CrystalFoldingBuilder.BLOCK_START_TOKENS) {
                startStack.add(OpenEntry(element, keywordFoldStart(elements, element, tokenType, i)))
            } else if (tokenType == CrystalTypes.END && startStack.isNotEmpty()) {
                val entry = startStack.removeAt(startStack.lastIndex)
                addIfMultiline(
                    entries,
                    entry.element,
                    TextRange(entry.foldStartOffset, element.textRange.endOffset),
                    document,
                )
            }
            i++
        }
        return entries
    }

    private fun keywordFoldStart(
        elements: Array<out PsiElement>,
        element: PsiElement,
        tokenType: com.intellij.psi.tree.IElementType,
        index: Int,
    ): Int {
        if (tokenType !in CrystalFoldingBuilder.CONDITIONAL_KEYWORDS) {
            return element.textRange.endOffset
        }
        // For if/unless/while/until: fold starts after the condition (first NEWLINE/THEN/SEMICOLON)
        var j = index + 1
        while (j < elements.size) {
            val nextType = elements[j].node?.elementType
            if (nextType == CrystalTypes.NEWLINE || nextType == CrystalTypes.THEN || nextType == CrystalTypes.SEMICOLON) {
                break
            }
            j++
        }
        return if (j < elements.size) elements[j].textRange.startOffset else element.textRange.endOffset
    }

    /**
     * Multi-line arrays `[...]` and hashes `{...}`, matched as pairs only
     * (`[` with `]`, `{` with `}`).
     */
    fun collectBracketPairs(
        elements: Array<out PsiElement>,
        document: Document,
    ): List<FoldEntry> {
        val entries = mutableListOf<FoldEntry>()
        val bracketStack = mutableListOf<PsiElement>()
        for (element in elements) {
            val tokenType = element.node?.elementType ?: continue
            when (tokenType) {
                CrystalTypes.LBRACKET, CrystalTypes.LBRACE -> {
                    bracketStack.add(element)
                }

                CrystalTypes.RBRACKET, CrystalTypes.RBRACE -> {
                    popMatchingBracket(bracketStack, tokenType)?.let { start ->
                        addIfMultiline(
                            entries,
                            start,
                            TextRange(start.textRange.startOffset, element.textRange.endOffset),
                            document,
                        )
                    }
                }
            }
        }
        return entries
    }

    /** Pops the matching opener for [closer], or null on mismatch/empty stack. */
    private fun popMatchingBracket(
        bracketStack: MutableList<PsiElement>,
        closer: com.intellij.psi.tree.IElementType,
    ): PsiElement? {
        if (bracketStack.isEmpty()) return null
        val start = bracketStack.removeAt(bracketStack.lastIndex)
        val openType = start.node.elementType
        // Only fold matching pairs: [] or {}
        val isMatchingPair =
            (closer == CrystalTypes.RBRACKET && openType == CrystalTypes.LBRACKET) ||
                (closer == CrystalTypes.RBRACE && openType == CrystalTypes.LBRACE)
        return if (isMatchingPair) start else null
    }

    /**
     * Runs of consecutive comment lines (a lone comment never folds).
     */
    fun collectCommentRuns(
        elements: Array<out PsiElement>,
        document: Document,
    ): List<FoldEntry> {
        val entries = mutableListOf<FoldEntry>()
        var commentStart: PsiElement? = null
        var commentEnd: PsiElement? = null
        for (element in elements) {
            val tokenType = element.node?.elementType ?: continue
            if (tokenType == CrystalTypes.LINE_COMMENT) {
                if (commentStart == null) {
                    commentStart = element
                }
                commentEnd = element
            } else if (tokenType != CrystalTypes.NEWLINE && tokenType != CrystalTokenTypes.WHITE_SPACE) {
                val start = commentStart
                val end = commentEnd
                if (start != null && end != null && start != end) {
                    addIfMultiline(
                        entries,
                        start,
                        TextRange(start.textRange.startOffset, end.textRange.endOffset),
                        document,
                    )
                }
                commentStart = null
                commentEnd = null
            }
        }
        return entries
    }
}

/** Converts [CrystalFoldingCollectors.FoldEntry] to platform descriptors. */
internal fun List<CrystalFoldingCollectors.FoldEntry>.toDescriptors(): Array<FoldingDescriptor> =
    map { FoldingDescriptor(it.element.node, it.range) }.toTypedArray()
