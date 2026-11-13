package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Element type of an index expression `recv[index]`.
 *
 * The grammar has no index node: `row[0]` is an EXPRESSION whose children are
 * receiver + LBRACKET + index leaves + RBRACKET. Without this resolution the
 * expression fell through to the first-child descent, which returned the
 * RECEIVER's type — so `row[0]` on a tuple was reported as the whole tuple.
 */
internal object CrystalIndexedTypeResolver {
    /** Receivers that occupy children 0 and 1 of the expression, minimum. */
    private const val MIN_INDEX_CHILDREN = 4

    fun resolve(expr: PsiElement): CrystalExpressionTypeResolver.ResolvedType? {
        val children = expr.node?.getChildren(null) ?: return null
        if (!isIndexShape(children)) return null
        val receiverType = CrystalExpressionTypeResolver.resolveType(children[0].psi)?.typeName ?: return null
        return CrystalCollectionTypeResolver.indexedElementType(receiverType, indexTextOf(children))
    }

    /** `recv [ ... ]` — the index may span several leaves (a leading `-1`). */
    private fun isIndexShape(children: Array<com.intellij.lang.ASTNode>): Boolean =
        children.size >= MIN_INDEX_CHILDREN &&
            children[1].elementType == CrystalTypes.LBRACKET &&
            children[children.size - 1].elementType == CrystalTypes.RBRACKET

    private fun indexTextOf(children: Array<com.intellij.lang.ASTNode>): String =
        (2 until children.size - 1).joinToString("") { children[it].text }.trim()
}
