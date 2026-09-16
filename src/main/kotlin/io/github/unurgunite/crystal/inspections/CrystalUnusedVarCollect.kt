package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalStringExpression
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference

/**
 * Assignment/reference collection for the unused-variable inspection: walks a
 * scope recording local assignments (with compound ops as reads) and variable
 * references. Split out of `CrystalUnusedVariableInspection`.
 */
internal object CrystalUnusedVarCollect {
    data class AssignmentInfo(
        val name: String,
        val offset: Int,
        val identifierElement: PsiElement,
        val isCompound: Boolean,
    )

    data class ReferenceInfo(
        val name: String,
        val offset: Int,
    )

    fun collectElements(
        element: PsiElement,
        assignments: MutableList<AssignmentInfo>,
        references: MutableList<ReferenceInfo>,
        skipNestedMethods: Boolean,
    ) {
        var child = element.firstChild
        while (child != null) {
            val next = child.nextSibling
            if (!(skipNestedMethods && child is CrystalMethodDefinition)) {
                visitChild(child, assignments, references, skipNestedMethods)
            }
            child = next
        }
    }

    /** One child: records assignments/references, then recurses structurally. */
    private fun visitChild(
        child: PsiElement,
        assignments: MutableList<AssignmentInfo>,
        references: MutableList<ReferenceInfo>,
        skipNestedMethods: Boolean,
    ) {
        recordAssignment(child, assignments, references)
        recordReference(child, references)
        for (grandchild in structuralChildren(child)) {
            collectElements(grandchild, assignments, references, skipNestedMethods)
        }
    }

    /**
     * Structurally relevant children: assignments contribute their whole subtree
     * (RHS reads), string interpolations their parts, calls their args;
     * everything else the child itself.
     */
    private fun structuralChildren(child: PsiElement): List<PsiElement> =
        when (child) {
            is CrystalStringExpression -> child.node.getChildren(null).map { it.psi }
            else -> listOf(child)
        }

    /** Local assignment recorded; compound ops also recorded as reads. */
    private fun recordAssignment(
        child: PsiElement,
        assignments: MutableList<AssignmentInfo>,
        references: MutableList<ReferenceInfo>,
    ) {
        if (child !is CrystalAssignment) return
        val info = extractLocalAssignment(child) ?: return
        assignments.add(info)
        if (info.isCompound) {
            references.add(ReferenceInfo(info.name, info.offset))
        }
    }

    /** Bare variable reference or method-call name recorded as a read. */
    private fun recordReference(
        child: PsiElement,
        references: MutableList<ReferenceInfo>,
    ) {
        if (child is CrystalVariableReference) {
            extractVariableReferenceName(child)?.let { references.add(ReferenceInfo(it, child.textOffset)) }
            return
        }
        if (child is CrystalMethodCallExpression) {
            val methodNameNode = child.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return
            references.add(ReferenceInfo(methodNameNode.text, methodNameNode.psi.textOffset))
        }
    }

    private fun extractLocalAssignment(assignment: CrystalAssignment): AssignmentInfo? {
        // Skip instance/class var assignments (plain or as call-chain base)
        if (assignment.instanceVarAccessList.isNotEmpty()) return null
        if (assignment.classVarAccessList.isNotEmpty()) return null

        // Find the IDENTIFIER token (local variable name)
        val identifierNode = assignment.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return null
        val name = identifierNode.text

        // Skip global vars (shouldn't happen since grammar handles them, but safety)
        if (name.startsWith("$")) return null

        // Check if compound assignment
        val assignOp = assignment.node.findChildByType(CrystalTypes.ASSIGN)
        val isCompound = assignOp == null // If no plain ASSIGN, it's a compound op

        val identifierPsi = identifierNode.psi

        return AssignmentInfo(
            name = name,
            offset = identifierPsi.textOffset,
            identifierElement = identifierPsi,
            isCompound = isCompound,
        )
    }

    private fun extractVariableReferenceName(ref: CrystalVariableReference): String? {
        val node = ref.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return null
        return node.text
    }
}
