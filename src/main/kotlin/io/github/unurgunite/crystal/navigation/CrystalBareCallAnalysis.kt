package io.github.unurgunite.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.isBlankSingleLineGap
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.isNameToken
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.prevMeaningfulLeaf
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Bare-call method-name analysis: args-parent lookup and the leaf-pattern
 * search for the method name (`DOT? IDENTIFIER WHITESPACE args`, last match wins).
 */
internal object CrystalBareCallAnalysis {
    fun findArgsParent(element: PsiElement?): PsiElement? {
        if (element == null) return null
        findEnclosingArgs(element)?.let { return it }
        if (element is CrystalCallArgs) return element
        if (element is CrystalBareArgumentList) return element
        return null
    }

    /** Nearest enclosing args holder, parenthesized or bare. */
    private fun findEnclosingArgs(element: PsiElement): PsiElement? {
        val callArgs = PsiTreeUtil.getParentOfType(element, CrystalCallArgs::class.java, false)
        if (callArgs != null) return callArgs
        return PsiTreeUtil.getParentOfType(element, CrystalBareArgumentList::class.java, false)
    }

    /**
     * Analyzes collected leaves (in source order) to find the method name.
     * Returns the IDENTIFIER/CONSTANT PsiElement that is the method name, or null.
     *
     * Pattern: DOT? IDENTIFIER WHITESPACE args. The LAST IDENTIFIER followed
     * by whitespace (before the cursor) is the method name — iterating backwards
     * finds `hika` (not `puts`) in `puts Tesa.hika <caret>`.
     */
    fun findMethodNameInLeaves(
        leaves: List<PsiElement>,
        file: PsiFile,
        cursorOffset: Int,
    ): PsiElement? {
        for (i in leaves.indices.reversed()) {
            if (isBareMethodNameAt(leaves, file, cursorOffset, i)) return leaves[i]
        }
        return null
    }

    /**
     * True when `leaves[i]` is a bare-call method name: an identifier followed by
     * same-line whitespace, not a named-arg label or comma-separated argument, and
     * with no LPAREN before the cursor (paren-calls are handled elsewhere).
     */
    private fun isBareMethodNameAt(
        leaves: List<PsiElement>,
        file: PsiFile,
        cursorOffset: Int,
        i: Int,
    ): Boolean {
        if (!isNameToken(leaves[i])) return false
        if (!isFollowedByWhitespace(leaves.getOrNull(i + 1))) return false
        // Disqualify named-arg labels and comma-separated arguments
        if (isArgumentContinuation(leaves.getOrNull(i - 1))) return false
        // A preceding DOT marks a dot-call method name (valid); a preceding bare
        // identifier is fine too — backwards iteration hits the LAST one first.
        val nameEnd = leaves[i].textRange.endOffset
        val textBetween = file.text.substring(nameEnd, cursorOffset)
        return !textBetween.contains('(')
    }

    /** Next token is whitespace (meaning bare call). */
    private fun isFollowedByWhitespace(next: PsiElement?): Boolean =
        next is PsiWhiteSpace || next?.node?.elementType == CrystalTokenTypes.WHITE_SPACE

    /** Previous token is a comma or colon (argument continuation, not a name). */
    private fun isArgumentContinuation(prev: PsiElement?): Boolean {
        val prevType = prev?.node?.elementType
        return prevType == CrystalTypes.COMMA || prevType == CrystalTypes.COLON
    }

    /**
     * Cursor directly after an identifier with only whitespace behind it:
     * a bare call with no args yet — unless preceded by a structural keyword
     * (`def`, `class`, ...), which makes it a definition, not a call.
     */
    fun quickCheckBareCallAtCursor(
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        if (offset <= 0) return null
        val (leafAtOffset, nameEnd) = nameTokenBeforeCursor(file, offset) ?: return null
        if (!isBlankSingleLineGap(file.text.substring(nameEnd, offset))) return null
        if (!isBareCallStart(leafAtOffset)) return null
        val stmtEnd = CrystalBareCallScanner.findStatementEndOffset(file, offset)
        return CrystalParameterInfoAnchor(file.manager, leafAtOffset, stmtEnd)
    }

    /** Name token ending at/before the cursor, with its end offset. */
    private fun nameTokenBeforeCursor(
        file: PsiFile,
        offset: Int,
    ): Pair<PsiElement, Int>? {
        val leafAtOffset = file.findElementAt(offset - 1)?.takeIf { isNameToken(it) } ?: return null
        val nameEnd = leafAtOffset.textRange.endOffset
        if (offset < nameEnd) return null
        return leafAtOffset to nameEnd
    }

    /**
     * True when [leaf] starts a bare/DOT call at quick-check time: preceded by DOT
     * (`obj.method <caret>`), at statement start, or after another identifier.
     * The last case covers `puts Tesa.hika<caret>` (argument position, still a
     * DOT-call we must own before the outer call's args win) — the quick-check
     * anchor then goes through the same prev-token validation as the original
     * code. Structural keywords are excluded (`def foo <caret>` is a definition,
     * not a call).
     */
    private fun isBareCallStart(leaf: PsiElement): Boolean {
        val prevType = prevMeaningfulLeaf(leaf)?.node?.elementType ?: return true
        if (prevType == CrystalTypes.DOT) return true
        return !CrystalBareCallScanner.STRUCTURAL_KEYWORDS.contains(prevType)
    }
}
