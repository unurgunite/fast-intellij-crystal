package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Assignment LHS/RHS accessors for type inference: the call/index-aware
 * `assignment` shape exposes targets and values as lists (a chain like
 * `node.anchor` contributes several expressions).
 */
internal object CrystalAssignmentTarget {
    /**
     * LHS text of an assignment: plain `variable` (IDENTIFIER leaf) or the first
     * identifier of a call/index chain (`node.anchor` → `node`). The chain's own
     * type comes from the receiver, not the assignment, so only the base name
     * matters here.
     */
    fun targetText(assignment: CrystalAssignment): String? {
        val identNode =
            assignment.node.findChildByType(CrystalTypes.IDENTIFIER)
                ?: assignment.firstChild?.node?.findChildByType(CrystalTypes.IDENTIFIER)
        if (identNode != null) return identNode.text
        val ivar = assignment.instanceVarAccessList.firstOrNull() ?: assignment.classVarAccessList.firstOrNull()
        return ivar?.text
    }

    /**
     * RHS of an assignment: the last `expression` child (targets contribute
     * expressions too — `a[i]` — so FIRST is wrong for index targets).
     */
    fun rhs(assignment: CrystalAssignment): PsiElement? = assignment.expressionList.lastOrNull()
}
