package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.CrystalFile
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.AssignmentInfo
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.ReferenceInfo
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition

/**
 * Inspection that reports local variables that are assigned but never read.
 * Also reports individual assignments whose value is overwritten before being read.
 *
 * Conventions:
 * - Variables starting with '_' are intentionally unused (no warning)
 * - Method parameters are NOT checked
 * - Instance vars (@x), class vars (@@x), globals ($x) are NOT checked
 * - Compound assignments (+=, ||=, etc.) count as both read and write (no warning)
 *
 * Collection lives in [CrystalUnusedVarCollect], read ranges in [CrystalUnusedVarReads].
 */
class CrystalUnusedVariableInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (!isAnalyzableCrystalFile(element.containingFile)) return
                when (element) {
                    is CrystalMethodDefinition -> analyzeScope(element, holder, skipNestedMethods = false)
                    is CrystalFile -> analyzeScope(element, holder, skipNestedMethods = true)
                }
            }
        }

    private fun analyzeScope(
        scope: PsiElement,
        holder: ProblemsHolder,
        skipNestedMethods: Boolean,
    ) {
        val assignments = mutableListOf<AssignmentInfo>()
        val references = mutableListOf<ReferenceInfo>()

        CrystalUnusedVarCollect.collectElements(scope, assignments, references, skipNestedMethods)
        // A block body may not run at all, so the value a variable holds on entry
        // survives it: writes made OUTSIDE the block stay live past it.
        addBlockExitReads(scope, assignments, references)

        val shadowed = CrystalUnusedVarCollect.collectShadowRanges(scope)
        for (assignment in assignments) {
            checkAssignment(assignment, assignments, references, shadowed, holder)
        }
    }

    /** One assignment: skipped (underscore/compound) or reported when never read. */
    private fun checkAssignment(
        assignment: AssignmentInfo,
        assignments: List<AssignmentInfo>,
        references: List<ReferenceInfo>,
        shadowed: List<CrystalUnusedVarCollect.ShadowRange>,
        holder: ProblemsHolder,
    ) {
        if (assignment.name.startsWith("_")) return
        if (assignment.isCompound) return
        if (hasLaterRead(assignment, assignments, references, shadowed)) return

        val otherAssignments = assignments.count { it.name == assignment.name }
        val message =
            if (otherAssignments > 1) {
                "Value assigned to '${assignment.name}' is never used"
            } else {
                "Variable '${assignment.name}' is never used"
            }
        holder.registerProblem(
            assignment.identifierElement,
            message,
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        )
    }

    /**
     * True when a reference reads the value: within the read window (up to the
     * next assignment's end, extended past conditional branches), or — with no
     * later assignment — anywhere after.
     */
    private fun hasLaterRead(
        assignment: AssignmentInfo,
        assignments: List<AssignmentInfo>,
        references: List<ReferenceInfo>,
        shadowed: List<CrystalUnusedVarCollect.ShadowRange>,
    ): Boolean {
        val assignOffset = assignment.offset
        val nextAssign = CrystalUnusedVarReads.nextAssignment(assignments, assignment.name, assignOffset)
        if (nextAssign == null ||
            CrystalUnusedVarReads.windowStaysOpen(assignments, assignment, nextAssign)
        ) {
            return readsInRange(assignment, references, shadowed, assignOffset, Int.MAX_VALUE)
        }
        // Use the end offset of the next assignment as upper bound, so that
        // references on its RHS (e.g. `abc = abc.upcase`) are counted as reads.
        val effectiveUpperBound = CrystalUnusedVarReads.readWindowEnd(assignments, assignment, nextAssign)
        return readsInRange(assignment, references, shadowed, assignOffset, effectiveUpperBound)
    }

    /**
     * A read counts only when it is not inside a nested scope that declares the
     * same name as its own parameter — there the inner binding shadows the outer
     * variable, so the outer value stays unused.
     */
    private fun readsInRange(
        assignment: AssignmentInfo,
        references: List<ReferenceInfo>,
        shadowed: List<CrystalUnusedVarCollect.ShadowRange>,
        from: Int,
        to: Int,
    ): Boolean =
        references.any { ref ->
            ref.name == assignment.name &&
                ref.offset > from &&
                ref.offset < to &&
                shadowed.none { assignment.name in it.names && it.range.contains(ref.offset) }
        }
}
