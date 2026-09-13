package io.github.unurgunite.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.MAX_BACKTRACK_TOKENS
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Backwards leaf collection for bare-call detection: gathers leaf tokens from
 * the cursor up to a statement boundary or bracket mismatch, in source order.
 */
internal object CrystalLeafCollector {
    /**
     * Collects leaf tokens going backwards from the cursor up to a statement
     * boundary, bracket mismatch, or [MAX_BACKTRACK_TOKENS]. Returns them in
     * source order (left to right), or null when there is nothing to analyze.
     */
    fun collectBackwardsLeaves(
        file: PsiFile,
        offset: Int,
    ): List<PsiElement>? {
        val start = startLeaf(file, offset) ?: return null
        val state = BracketDepth()
        val leaves =
            generateSequence(start) { PsiTreeUtil.prevLeaf(it) }
                .take(MAX_BACKTRACK_TOKENS)
                .takeWhile { collectLeaf(it, state) }
                .toList()
        if (leaves.isEmpty()) return null
        // Reverse to get source order (left to right)
        return leaves.reversed()
    }

    /** Leaf before the cursor (one step back on empty space). */
    private fun startLeaf(
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        if (offset <= 0) return null
        return file.findElementAt(offset - 1)
            ?: if (offset > 1) {
                file.findElementAt(offset - 2)
            } else {
                null
            }
    }

    /** Bracket nesting level while scanning backwards (closing brackets open a level). */
    private class BracketDepth {
        var depth: Int = 0
    }

    /**
     * Decides whether [leaf] belongs to the current bare-call candidate and updates
     * [state]. Returns false at statement boundaries and bracket mismatches.
     */
    private fun collectLeaf(
        leaf: PsiElement,
        state: BracketDepth,
    ): Boolean {
        val type = leaf.node?.elementType
        if (leaf is PsiWhiteSpace || type == CrystalTokenTypes.WHITE_SPACE) {
            return !(leaf.text.contains('\n') && state.depth == 0)
        }
        return when (type) {
            CrystalTypes.RPAREN, CrystalTypes.RBRACKET, CrystalTypes.RBRACE -> {
                state.depth++
                true
            }

            CrystalTypes.LPAREN, CrystalTypes.LBRACKET, CrystalTypes.LBRACE -> {
                collectOpenBracket(state)
            }

            else -> {
                state.depth != 0 || !isBoundaryToken(type)
            }
        }
    }

    /** An opening bracket: closes a nesting level, or ends the scan at depth 0. */
    private fun collectOpenBracket(state: BracketDepth): Boolean {
        if (state.depth == 0) {
            return false
        }
        state.depth--
        return true
    }

    private fun isBoundaryToken(type: com.intellij.psi.tree.IElementType?): Boolean =
        type == CrystalTypes.NEWLINE ||
            CrystalBareCallScanner.STOP_OPERATORS.contains(type) ||
            CrystalBareCallScanner.STRUCTURAL_KEYWORDS.contains(type)
}
