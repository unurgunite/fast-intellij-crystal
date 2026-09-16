package io.github.unurgunite.crystal.editor

import com.intellij.codeInsight.highlighting.AbstractCodeBlockSupportHandler
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalCodeBlockSupportHandler : AbstractCodeBlockSupportHandler() {
    override fun getTopLevelElementTypes(): TokenSet =
        TokenSet.create(
            CrystalTypes.IF_STATEMENT,
            CrystalTypes.UNLESS_STATEMENT,
            CrystalTypes.WHILE_STATEMENT,
            CrystalTypes.UNTIL_STATEMENT,
            CrystalTypes.FOR_STATEMENT,
            CrystalTypes.CASE_STATEMENT,
            CrystalTypes.BEGIN_STATEMENT,
            CrystalTypes.METHOD_DEFINITION,
            CrystalTypes.CLASS_DEFINITION,
            CrystalTypes.MODULE_DEFINITION,
            CrystalTypes.STRUCT_DEFINITION,
            CrystalTypes.ENUM_DEFINITION,
            CrystalTypes.ANNOTATION_DEFINITION,
            CrystalTypes.LIB_DEFINITION,
            CrystalTypes.MACRO_DEFINITION,
            CrystalTypes.BLOCK,
        )

    override fun getKeywordElementTypes(): TokenSet =
        TokenSet.create(
            CrystalTypes.DEF,
            CrystalTypes.CLASS,
            CrystalTypes.MODULE,
            CrystalTypes.STRUCT,
            CrystalTypes.ENUM,
            CrystalTypes.ANNOTATION,
            CrystalTypes.LIB,
            CrystalTypes.MACRO,
            CrystalTypes.VERBATIM,
            CrystalTypes.IF,
            CrystalTypes.UNLESS,
            CrystalTypes.WHILE,
            CrystalTypes.UNTIL,
            CrystalTypes.FOR,
            CrystalTypes.CASE,
            CrystalTypes.DO,
            CrystalTypes.BEGIN,
            CrystalTypes.END,
            CrystalTypes.ELSE,
            CrystalTypes.ELSIF,
            CrystalTypes.WHEN,
            CrystalTypes.RESCUE,
            CrystalTypes.ENSURE,
        )

    override fun getBlockElementTypes(): TokenSet = getTopLevelElementTypes()

    companion object {
        private val IF_CHILDREN: TokenSet =
            TokenSet.create(
                CrystalTypes.IF,
                CrystalTypes.ELSIF_CLAUSE,
                CrystalTypes.ELSE_CLAUSE,
                CrystalTypes.END,
            )

        private val UNLESS_CHILDREN: TokenSet =
            TokenSet.create(
                CrystalTypes.UNLESS,
                CrystalTypes.ELSE_CLAUSE,
                CrystalTypes.END,
            )

        private val CASE_CHILDREN: TokenSet =
            TokenSet.create(
                CrystalTypes.CASE,
                CrystalTypes.WHEN_CLAUSE,
                CrystalTypes.IN_CLAUSE,
                CrystalTypes.ELSE_CLAUSE,
                CrystalTypes.END,
            )

        private val BEGIN_CHILDREN: TokenSet =
            TokenSet.create(
                CrystalTypes.BEGIN,
                CrystalTypes.RESCUE_CLAUSE,
                CrystalTypes.ELSE_CLAUSE,
                CrystalTypes.ENSURE_CLAUSE,
                CrystalTypes.END,
            )

        private val BLOCK_CHILDREN: TokenSet =
            TokenSet.create(
                CrystalTypes.DO,
                CrystalTypes.RESCUE_CLAUSE,
                CrystalTypes.ELSE_CLAUSE,
                CrystalTypes.ENSURE_CLAUSE,
                CrystalTypes.END,
            )

        private fun singleKeywordChildren(keyword: IElementType): TokenSet = TokenSet.create(keyword, CrystalTypes.END)

        private fun loneKeyword(keyword: IElementType): TokenSet = TokenSet.create(keyword)

        private val CHILDREN_BY_PARENT: Map<IElementType, TokenSet> by lazy {
            mapOf(
                CrystalTypes.IF_STATEMENT to IF_CHILDREN,
                CrystalTypes.UNLESS_STATEMENT to UNLESS_CHILDREN,
                CrystalTypes.CASE_STATEMENT to CASE_CHILDREN,
                CrystalTypes.BEGIN_STATEMENT to BEGIN_CHILDREN,
                CrystalTypes.BLOCK to BLOCK_CHILDREN,
                CrystalTypes.METHOD_DEFINITION to singleKeywordChildren(CrystalTypes.DEF),
                CrystalTypes.CLASS_DEFINITION to singleKeywordChildren(CrystalTypes.CLASS),
                CrystalTypes.MODULE_DEFINITION to singleKeywordChildren(CrystalTypes.MODULE),
                CrystalTypes.STRUCT_DEFINITION to singleKeywordChildren(CrystalTypes.STRUCT),
                CrystalTypes.ENUM_DEFINITION to singleKeywordChildren(CrystalTypes.ENUM),
                CrystalTypes.ANNOTATION_DEFINITION to singleKeywordChildren(CrystalTypes.ANNOTATION),
                CrystalTypes.LIB_DEFINITION to singleKeywordChildren(CrystalTypes.LIB),
                CrystalTypes.MACRO_DEFINITION to singleKeywordChildren(CrystalTypes.MACRO),
                CrystalTypes.WHILE_STATEMENT to singleKeywordChildren(CrystalTypes.WHILE),
                CrystalTypes.UNTIL_STATEMENT to singleKeywordChildren(CrystalTypes.UNTIL),
                CrystalTypes.FOR_STATEMENT to singleKeywordChildren(CrystalTypes.FOR),
                CrystalTypes.ELSIF_CLAUSE to loneKeyword(CrystalTypes.ELSIF),
                CrystalTypes.ELSE_CLAUSE to loneKeyword(CrystalTypes.ELSE),
                CrystalTypes.RESCUE_CLAUSE to loneKeyword(CrystalTypes.RESCUE),
                CrystalTypes.ENSURE_CLAUSE to loneKeyword(CrystalTypes.ENSURE),
                CrystalTypes.WHEN_CLAUSE to loneKeyword(CrystalTypes.WHEN),
                CrystalTypes.IN_CLAUSE to loneKeyword(CrystalTypes.IN),
            )
        }
    }

    override fun getDirectChildrenElementTypes(parentType: IElementType?): TokenSet = CHILDREN_BY_PARENT[parentType] ?: TokenSet.EMPTY
}
