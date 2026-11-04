package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
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
                depth += macroBlockDelta(control)
            }
            sibling = sibling.prevSibling
        }
        return depth > 0
    }

    /**
     * How the macro control changes the block depth: openers add one, `{% end %}` removes one,
     * and branch/standalone controls (`{% else %}`, `{% elsif %}`, `{% when %}`, `{% in %}`,
     * `{% debug %}`) leave it unchanged. Treating only `end` as neutral-prefix would otherwise
     * count `else` as an opener and leave the depth permanently above zero.
     */
    private fun macroBlockDelta(control: CrystalMacroControl): Int =
        when (control.firstKeyword()) {
            CrystalTypes.END -> -1
            in OPENING_MACRO_KEYWORDS -> 1
            else -> 0
        }

    /**
     * The control's leading keyword, skipping the `{%` token and whitespace. The lexer emits
     * keywords as dedicated tokens inside [CrystalMacroControl], so the kind is knowable
     * without re-reading the text.
     */
    private fun CrystalMacroControl.firstKeyword(): IElementType? =
        firstChild
            ?.let { PsiTreeUtil.skipWhitespacesForward(it) }
            ?.node
            ?.elementType

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

    /**
     * `{% ... %}` keywords that wrap a body and are therefore terminated by `{% end %}`.
     * Everything else (`{% else %}`, `{% elsif %}`, `{% when %}`, `{% in %}`, `{% debug %}`)
     * is a branch marker or a standalone directive and must not change the depth — counting
     * them as openers leaves the depth above zero and silently hides later assignments.
     */
    private val OPENING_MACRO_KEYWORDS: Set<IElementType> =
        setOf(CrystalTypes.FOR, CrystalTypes.IF, CrystalTypes.UNLESS, CrystalTypes.BEGIN, CrystalTypes.MACRO, CrystalTypes.VERBATIM)
}
