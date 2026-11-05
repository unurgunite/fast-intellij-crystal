package io.github.unurgunite.crystal.inspections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalProcLiteral
import io.github.unurgunite.crystal.psi.CrystalStringExpression
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.util.extractParameterName

/**
 * Assignment/reference collection for the unused-variable inspection: walks a
 * scope recording local assignments (with compound ops as reads) and variable
 * references. Split out of `CrystalUnusedVariableInspection`.
 */
internal object CrystalUnusedVarCollect {
    /**
     * Operators that bind a local variable directly. Any other token after the
     * name means the assignment goes through a member or an index.
     */
    private val ASSIGN_OPERATORS: Set<IElementType> =
        setOf(
            CrystalTypes.ASSIGN,
            CrystalTypes.PLUS_ASSIGN,
            CrystalTypes.MINUS_ASSIGN,
            CrystalTypes.STAR_ASSIGN,
            CrystalTypes.SLASH_ASSIGN,
            CrystalTypes.OR_OR_ASSIGN,
            CrystalTypes.AND_AND_ASSIGN,
            CrystalTypes.CARET_ASSIGN,
            CrystalTypes.PERCENT_ASSIGN,
            CrystalTypes.AMPERSAND_ASSIGN,
            CrystalTypes.PIPE_ASSIGN,
            CrystalTypes.DOUBLE_STAR_ASSIGN,
            CrystalTypes.DOUBLE_SLASH_ASSIGN,
            CrystalTypes.LSHIFT_ASSIGN,
            CrystalTypes.RSHIFT_ASSIGN,
            CrystalTypes.WRAP_PLUS_ASSIGN,
            CrystalTypes.WRAP_MINUS_ASSIGN,
            CrystalTypes.WRAP_STAR_ASSIGN,
            CrystalTypes.WRAP_DOUBLE_STAR_ASSIGN,
        )

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
            when {
                // A file-level pass skips method bodies entirely; a method-level
                // pass descends into them for READS only, because a nested `def`
                // starts a new scope: its locals cannot rebind this one's, but a
                // read there still uses the outer value.
                child is CrystalMethodDefinition && skipNestedMethods -> Unit

                child is CrystalMethodDefinition -> collectNestedScope(child, references)

                else -> visitChild(child, assignments, references, skipNestedMethods)
            }
            child = next
        }
    }

    /**
     * Reads of a nested `def` only count for names it does not bind itself:
     * a local assignment or a parameter there creates an inner variable, so a
     * use of that name says nothing about the outer one.
     */
    private fun collectNestedScope(
        method: CrystalMethodDefinition,
        references: MutableList<ReferenceInfo>,
    ) {
        val inner = mutableListOf<AssignmentInfo>()
        val innerRefs = mutableListOf<ReferenceInfo>()
        collectElements(method, inner, innerRefs, false)
        val bound = inner.map { it.name }.toSet() + parameterNames(method).orEmpty()
        innerRefs.filterTo(references) { it.name !in bound }
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

    /**
     * Ranges where a nested method or block declares a parameter of its own,
     * so a same-named outer variable is shadowed there: `[1].each do |value|`
     * or `def inner(value)`. References inside such a range belong to the
     * inner binding and must not keep an outer value alive.
     */
    fun collectShadowRanges(scope: PsiElement): List<ShadowRange> {
        val ranges = mutableListOf<ShadowRange>()
        val holders = mutableListOf<PsiElement>()
        holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalMethodDefinition::class.java))
        holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalBlock::class.java))
        holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalProcLiteral::class.java))
        return holders
            .filter { it !== scope }
            .mapNotNull { holder -> parameterNames(holder)?.let { ShadowRange(it, holder.textRange) } }
    }

    internal fun parameterNames(holder: PsiElement): Set<String>? {
        val list =
            when (holder) {
                is CrystalMethodDefinition -> holder.parameterList?.parameterList
                is CrystalBlock -> holder.parameterList?.parameterList
                is CrystalProcLiteral -> holder.parameterList?.parameterList
                else -> null
            } ?: return null
        val names = list.mapNotNull { extractParameterName(it) }.toSet()
        return names.ifEmpty { null }
    }

    /** A nested scope and the parameter names it declares. */
    data class ShadowRange(
        val names: Set<String>,
        val range: TextRange,
    )

    /**
     * True when the assignment targets a member or an index rather than the
     * local variable itself, i.e. `header.ids = x`, `a.b.c = x`, `list[i] = x`.
     * Grammar-wise a plain local binding puts the assign operator directly
     * after the name (`assignable_call assign_op`), while a member/index write
     * has a `.`/`[` in between (`assignable_head DOT ... assign_op`).
     * The receiver is read there, so the earlier value is still alive.
     */
    private fun isMemberWrite(info: AssignmentInfo): Boolean {
        var next = info.identifierElement.nextSibling
        while (next != null && next.node.elementType == TokenType.WHITE_SPACE) {
            next = next.nextSibling
        }
        val type = next?.node?.elementType ?: return false
        return type !in ASSIGN_OPERATORS
    }

    /**
     * Local assignment recorded; compound ops also recorded as reads.
     * `header.ids = [...]` is an attribute write, not a rebinding: `header` is
     * read as the receiver there, so it counts as a read and never as a new
     * assignment (otherwise the earlier value looks dead).
     */
    private fun recordAssignment(
        child: PsiElement,
        assignments: MutableList<AssignmentInfo>,
        references: MutableList<ReferenceInfo>,
    ) {
        if (child !is CrystalAssignment) return
        val info = extractLocalAssignment(child) ?: return
        if (isMemberWrite(info)) {
            references.add(ReferenceInfo(info.name, info.offset))
            return
        }
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
