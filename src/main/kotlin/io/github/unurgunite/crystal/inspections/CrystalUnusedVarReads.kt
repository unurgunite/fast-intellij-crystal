package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.AssignmentInfo
import io.github.unurgunite.crystal.psi.CrystalBeginStatement
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalCaseStatement
import io.github.unurgunite.crystal.psi.CrystalForStatement
import io.github.unurgunite.crystal.psi.CrystalIfStatement
import io.github.unurgunite.crystal.psi.CrystalMacroControl
import io.github.unurgunite.crystal.psi.CrystalProcLiteral
import io.github.unurgunite.crystal.psi.CrystalSelectStatement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalUnlessStatement
import io.github.unurgunite.crystal.psi.CrystalUntilStatement
import io.github.unurgunite.crystal.psi.CrystalWhileStatement

/**
 * Read-range analysis for the unused-variable inspection: for each assignment,
 * which later assignments bound its value's lifetime and which references read it.
 * Conditional branches (if/unless/loops/case) extend the range, since a branch
 * assignment may not execute. Split out of `CrystalUnusedVariableInspection`.
 */
internal object CrystalUnusedVarReads {
    /** Later plain assignment of [name] after [assignOffset], nearest first. */
    fun nextAssignment(
        assignments: List<AssignmentInfo>,
        name: String,
        assignOffset: Int,
    ): AssignmentInfo? =
        assignments
            .filter { it.name == name && it.offset > assignOffset && !it.isCompound }
            .minByOrNull { it.offset }

    /**
     * Upper bound of the read window: the next assignment's end offset — or, when
     * that assignment sits in a conditional branch (may not execute), the next
     * UNCONDITIONAL assignment's end (reads after the block still see our value).
     */
    fun readWindowEnd(
        assignments: List<AssignmentInfo>,
        assignment: AssignmentInfo,
        nextAssign: AssignmentInfo?,
    ): Int {
        val nextAssignEndOffset =
            nextAssign
                ?.identifierElement
                ?.parent
                ?.textRange
                ?.endOffset ?: Int.MAX_VALUE
        if (nextAssign == null || !isInConditionalBranch(nextAssign.identifierElement.parent)) {
            return nextAssignEndOffset
        }
        return assignments
            .filter { it.name == assignment.name && it.offset > assignment.offset && !it.isCompound }
            .firstOrNull { !isInConditionalBranch(it.identifierElement.parent) }
            ?.identifierElement
            ?.parent
            ?.textRange
            ?.endOffset ?: Int.MAX_VALUE
    }

    /**
     * Checks if [element] is inside a conditional construct that may prevent execution
     * of the code it contains: if/unless/while/until/for/case/select, or begin+rescue.
     * Plain `begin ... end` (without rescue) is NOT considered conditional — its body
     * always executes.
     */
    private fun isInConditionalBranch(element: PsiElement): Boolean {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (isConditionalConstruct(current)) return true
            current = current.parent
        }
        return isInsideMacroBlock(element)
    }

    /**
     * A macro block (`{% for ... %}`, `{% if ... %}`) wraps its body, but the
     * grammar keeps that body as ordinary siblings rather than nesting it, so the
     * wrapping cannot be seen from the tree. Walk the preceding sibling macro
     * controls instead and keep a depth: opening controls add one, `{% end %}`
     * removes one. A positive depth means the element sits inside a macro block.
     *
     * Counting sibling [CrystalMacroControl] nodes rather than scanning text is
     * what keeps `{% ... %}` inside strings and comments (which form no such
     * node) from skewing the depth.
     */
    private fun isInsideMacroBlock(element: PsiElement): Boolean {
        var depth = 0
        var sibling = element.parent?.prevSibling
        while (sibling != null) {
            val control = PsiTreeUtil.findChildOfType(sibling, CrystalMacroControl::class.java)
            if (control != null && control.textRange.startOffset < element.textRange.startOffset) {
                depth += if (isMacroBlockEnd(control)) -1 else 1
            }
            sibling = sibling.prevSibling
        }
        return depth > 0
    }

    /** True when the macro control is a `{% end %}` closer. */
    private fun isMacroBlockEnd(control: CrystalMacroControl): Boolean =
        control.firstChild
            ?.nextSibling
            ?.let { PsiTreeUtil.skipWhitespacesForward(it) }
            ?.node
            ?.elementType == CrystalTypes.END

    /**
     * Conditional constructs plus `begin`+rescue (plain `begin` always executes).
     * A block or proc body (`do ... end`, `{ ... }`, `-> { ... }`) counts too: it may run zero or many
     * times, so an assignment inside it never bounds an outer value's lifetime.
     */
    private fun isConditionalConstruct(current: PsiElement): Boolean {
        when (current) {
            is CrystalBlock,
            is CrystalProcLiteral,
            -> {
                return true
            }

            is CrystalIfStatement,
            is CrystalUnlessStatement,
            is CrystalWhileStatement,
            is CrystalUntilStatement,
            is CrystalForStatement,
            is CrystalCaseStatement,
            is CrystalSelectStatement,
            -> {
                return true
            }

            is CrystalBeginStatement -> {
                if (current.rescueClauseList.isNotEmpty()) return true
            }
        }
        return false
    }
}
