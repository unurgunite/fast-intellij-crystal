package io.github.unurgunite.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Backwards leaf scanning for bare-call detection: token sets, sibling/leaf
 * navigation, statement offsets and unmatched-LPAREN search. The bare-call
 * method-name analysis lives in [CrystalBareCallAnalysis].
 */
internal object CrystalBareCallScanner {
    /** Operators that stop the backwards scan (bare-call boundary). */
    internal val STOP_OPERATORS: TokenSet =
        TokenSet.create(
            CrystalTypes.ASSIGN,
            CrystalTypes.PLUS_ASSIGN,
            CrystalTypes.MINUS_ASSIGN,
            CrystalTypes.STAR_ASSIGN,
            CrystalTypes.SLASH_ASSIGN,
            CrystalTypes.PERCENT_ASSIGN,
            CrystalTypes.AMPERSAND_ASSIGN,
            CrystalTypes.PIPE_ASSIGN,
            CrystalTypes.CARET_ASSIGN,
            CrystalTypes.DOUBLE_STAR_ASSIGN,
            CrystalTypes.LSHIFT_ASSIGN,
            CrystalTypes.RSHIFT_ASSIGN,
            CrystalTypes.OR_OR_ASSIGN,
            CrystalTypes.AND_AND_ASSIGN,
            CrystalTypes.EQ,
            CrystalTypes.NEQ,
            CrystalTypes.LT,
            CrystalTypes.GT,
            CrystalTypes.LTE,
            CrystalTypes.GTE,
            CrystalTypes.SPACESHIP,
            CrystalTypes.CASE_EQ,
            CrystalTypes.AND_AND,
            CrystalTypes.OR_OR,
            CrystalTypes.LSHIFT,
            CrystalTypes.RSHIFT,
            CrystalTypes.ARROW,
            CrystalTypes.DOUBLE_ARROW,
            CrystalTypes.DOTDOT,
            CrystalTypes.DOTDOTDOT,
            CrystalTypes.SEMICOLON,
        )

    /** Structural keywords that mark statement boundaries. */
    internal val STRUCTURAL_KEYWORDS: TokenSet =
        TokenSet.create(
            CrystalTypes.DEF,
            CrystalTypes.CLASS,
            CrystalTypes.MODULE,
            CrystalTypes.END,
            CrystalTypes.DO,
            CrystalTypes.ABSTRACT,
            CrystalTypes.STRUCT,
            CrystalTypes.ENUM,
            CrystalTypes.LIB,
            CrystalTypes.FUN,
            CrystalTypes.MACRO,
            CrystalTypes.ANNOTATION,
        )

    /** Max tokens to scan backwards (performance limit). */
    internal const val MAX_BACKTRACK_TOKENS = 200

    /**
     * True when [gap] is whitespace without a newline (possibly empty): the cursor
     * sits right after an identifier with no arguments typed yet.
     */
    internal fun isBlankSingleLineGap(gap: String): Boolean = gap.isBlank() && !gap.contains('\n')

    /** Previous sibling skipping whitespace, or null at the start. */
    fun prevMeaningfulSibling(element: PsiElement?): PsiElement? {
        var current = element?.prevSibling
        while (current is PsiWhiteSpace) current = current.prevSibling
        return current
    }

    /** Previous leaf skipping whitespace, or null at the start. */
    fun prevMeaningfulLeaf(leaf: PsiElement?): PsiElement? {
        var current = leaf?.let { PsiTreeUtil.prevLeaf(it) }
        while (current is PsiWhiteSpace ||
            current?.node?.elementType == CrystalTokenTypes.WHITE_SPACE
        ) {
            current = PsiTreeUtil.prevLeaf(current)
        }
        return current
    }

    fun isNameToken(element: PsiElement?): Boolean {
        val type = element?.node?.elementType
        return type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT
    }

    fun findArgsParent(element: PsiElement?): PsiElement? = CrystalBareCallAnalysis.findArgsParent(element)

    /**
     * Scans backwards from offset to find the position of an unmatched '(' character.
     */
    fun findUnmatchedLparen(
        text: String,
        offset: Int,
    ): Int {
        var depth = 0
        var i = (offset - 1).coerceAtMost(text.length - 1)
        while (i >= 0) {
            when (text[i]) {
                ')' -> {
                    depth++
                }

                '(' -> {
                    if (depth == 0) return i
                    depth--
                }

                '\n' -> {
                    if (depth == 0) return -1
                } // Don't cross line boundaries for paren search
            }
            i--
        }
        return -1
    }

    /**
     * Finds the IDENTIFIER/CONSTANT token immediately before an LPAREN at the given offset.
     * Skips whitespace and handles dot-calls (e.g., obj.method( or Foo.bar().
     */
    fun findIdentifierBeforeLparen(
        file: PsiFile,
        lparenOffset: Int,
    ): PsiElement? {
        if (lparenOffset <= 0) return null
        val leaf = prevMeaningfulLeaf(file.findElementAt(lparenOffset - 1)) ?: return null
        return if (isNameToken(leaf)) leaf else null
    }

    /**
     * Finds the end offset of the current statement (next newline or file end).
     * Guarantees the returned offset is strictly greater than cursorOffset so that
     * TextRange.contains(cursorOffset) is true.
     */
    fun findStatementEndOffset(
        file: PsiFile,
        cursorOffset: Int,
    ): Int {
        val text = file.text
        val newlinePos = text.indexOf('\n', cursorOffset)
        val end = if (newlinePos >= 0) newlinePos else text.length
        // Ensure range end is strictly greater than cursor so contains() works
        return maxOf(end, cursorOffset + 1).coerceAtMost(text.length)
    }

    /**
     * Scans backwards from the cursor position to detect a bare method call.
     *
     * Returns the IDENTIFIER/CONSTANT token of the method name wrapped in a
     * CrystalParameterInfoAnchor (with extended range), or null if no bare call is detected.
     *
     * Algorithm:
     * 1. Quick check: identifier directly before the cursor with only whitespace after it.
     * 2. Otherwise walk backwards collecting leaf tokens until a statement boundary.
     * 3. Analyze collected tokens from left to right (source order).
     */
    fun scanBackwardsForBareCall(
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        // Quick check: if element at offset-1 is an IDENTIFIER/CONSTANT and the text
        // between its end and offset is only whitespace (no newline), it might be a bare call.
        // This handles the case where IntelliJ calls with offset slightly before cursor.
        CrystalBareCallAnalysis.quickCheckBareCallAtCursor(file, offset)?.let { return it }

        val leaves = CrystalLeafCollector.collectBackwardsLeaves(file, offset) ?: return null

        // Find method name: first IDENTIFIER/CONSTANT that is followed by whitespace
        // and not preceded by anything that makes it NOT a method name (like another identifier
        // without DOT separator).
        // Also handle dot-calls: ... DOT IDENTIFIER WHITESPACE ...
        val nameToken = CrystalBareCallAnalysis.findMethodNameInLeaves(leaves, file, offset) ?: return null
        val stmtEnd = findStatementEndOffset(file, offset)
        return CrystalParameterInfoAnchor(file.manager, nameToken, stmtEnd)
    }
}
