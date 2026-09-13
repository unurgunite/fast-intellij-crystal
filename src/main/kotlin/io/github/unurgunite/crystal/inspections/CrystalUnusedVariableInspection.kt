package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.unurgunite.crystal.CrystalFile
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.AssignmentInfo
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.ReferenceInfo
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

        for (assignment in assignments) {
            checkAssignment(assignment, assignments, references, holder)
        }
    }

    /** One assignment: skipped (underscore/compound) or reported when never read. */
    private fun checkAssignment(
        assignment: AssignmentInfo,
        assignments: List<AssignmentInfo>,
        references: List<ReferenceInfo>,
        holder: ProblemsHolder,
    ) {
        if (assignment.name.startsWith("_")) return
        if (assignment.isCompound) return
        if (hasLaterRead(assignment, assignments, references)) return

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
    ): Boolean {
        val assignOffset = assignment.offset
        val nextAssign = CrystalUnusedVarReads.nextAssignment(assignments, assignment.name, assignOffset)
        if (nextAssign == null) {
            return references.any { ref ->
                ref.name == assignment.name && ref.offset > assignOffset
            }
        }
        // Use the end offset of the next assignment as upper bound, so that
        // references on its RHS (e.g. `abc = abc.upcase`) are counted as reads.
        val effectiveUpperBound = CrystalUnusedVarReads.readWindowEnd(assignments, assignment, nextAssign)
        return references.any { ref ->
            ref.name == assignment.name && ref.offset > assignOffset && ref.offset < effectiveUpperBound
        }
    }
}
