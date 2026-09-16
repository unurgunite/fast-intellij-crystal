package io.github.unurgunite.crystal.navigation.ivar

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Assignment shapes for instance/class variables: `@name = ...`, either as a
 * raw INSTANCE_VAR/CLASS_VAR leaf token or as a composite access (`@x = 1`
 * parses as ASSIGNMENT > *_VAR_ACCESS + ASSIGN, so the leaf's own next sibling
 * is null and only the composite check fires).
 */
internal object CrystalVarAssignments {
    /** Raw `INSTANCE_VAR`/`CLASS_VAR` leaf followed by `ASSIGN`. */
    fun collectLeafAssignment(
        child: PsiElement,
        astChild: ASTNode,
        varName: String,
        assignments: MutableList<PsiElement>,
    ) {
        val tokenType = astChild.elementType
        if (tokenType != CrystalTypes.INSTANCE_VAR && tokenType != CrystalTypes.CLASS_VAR) return
        if (astChild.text != varName) return
        if (isAssignNext(skipWhitespaceAst(astChild.treeNext))) {
            assignments.add(child)
        }
    }

    /** Composite `*_VAR_ACCESS` followed by `ASSIGN`. */
    fun collectCompositeAssignment(
        child: PsiElement,
        varName: String,
        assignments: MutableList<PsiElement>,
    ) {
        if (child !is CrystalInstanceVarAccess && child !is CrystalClassVarAccess) return
        if (child.text != varName) return
        if (isAssignNext(skipWhitespaceAst(child.node.treeNext))) {
            assignments.add(child)
        }
    }

    /** True when the next meaningful node is an assignment operator. */
    private fun isAssignNext(nextMeaningful: ASTNode?): Boolean =
        nextMeaningful != null && nextMeaningful.elementType == CrystalTypes.ASSIGN

    private fun skipWhitespaceAst(node: ASTNode?): ASTNode? {
        var current = node
        while (current != null && current.elementType.toString() == "WHITE_SPACE") {
            current = current.treeNext
        }
        return current
    }
}
