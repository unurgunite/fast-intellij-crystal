package io.github.unurgunite.crystal.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalFoldingBuilder : FoldingBuilderEx() {
    companion object {
        internal val BLOCK_START_TOKENS =
            setOf(
                CrystalTypes.DEF,
                CrystalTypes.CLASS,
                CrystalTypes.MODULE,
                CrystalTypes.STRUCT,
                CrystalTypes.ENUM,
                CrystalTypes.DO,
                CrystalTypes.BEGIN,
                CrystalTypes.IF,
                CrystalTypes.UNLESS,
                CrystalTypes.WHILE,
                CrystalTypes.UNTIL,
                CrystalTypes.FOR,
                CrystalTypes.CASE,
                CrystalTypes.MACRO,
                CrystalTypes.LIB,
                CrystalTypes.ANNOTATION,
                CrystalTypes.VERBATIM,
            )

        internal val CONDITIONAL_KEYWORDS =
            setOf(
                CrystalTypes.ANNOTATION,
                CrystalTypes.BEGIN,
                CrystalTypes.CASE,
                CrystalTypes.CLASS,
                CrystalTypes.DEF,
                CrystalTypes.DO,
                CrystalTypes.ENUM,
                CrystalTypes.FOR,
                CrystalTypes.IF,
                CrystalTypes.LIB,
                CrystalTypes.MACRO,
                CrystalTypes.MODULE,
                CrystalTypes.STRUCT,
                CrystalTypes.UNLESS,
                CrystalTypes.UNTIL,
                CrystalTypes.VERBATIM,
                CrystalTypes.WHILE,
            )
    }

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ): Array<FoldingDescriptor> {
        val elements = PsiTreeUtil.collectElements(root) { true }
        return (
            CrystalFoldingCollectors.collectKeywordBlocks(elements, document) +
                CrystalFoldingCollectors.collectBracketPairs(elements, document) +
                CrystalFoldingCollectors.collectCommentRuns(elements, document)
        ).toDescriptors()
    }

    override fun getPlaceholderText(node: ASTNode): String {
        val elementType = node.elementType
        if (elementType == CrystalTypes.LINE_COMMENT) return "# ..."
        if (elementType == CrystalTypes.LBRACKET) return "[ ... ]"
        if (elementType == CrystalTypes.LBRACE) return "{ ... }"
        if (elementType in BLOCK_START_TOKENS) return " ... end"
        return "..."
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
