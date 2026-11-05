package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.AssignmentInfo
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.ReferenceInfo
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalProcLiteral

/**
 * A block or proc may run zero times, so the value a variable holds on entry
 * survives it: `value = 1; each { value = 2 }` does not kill the outer value.
 * The surviving value is recorded as a read at the block's START, before any
 * write inside the block, so it keeps the outer value alive without keeping the
 * block's own write alive.
 */
internal fun addBlockExitReads(
    scope: PsiElement,
    assignments: List<AssignmentInfo>,
    references: MutableList<ReferenceInfo>,
) {
    val holders = mutableListOf<PsiElement>()
    holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalBlock::class.java))
    holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalProcLiteral::class.java))
    holders
        .filter { it !== scope }
        .mapNotNull { CrystalUnusedVarCollect.parameterNames(it)?.let { names -> it to names } }
        .forEach { (holder, names) ->
            assignments
                // Only a value written BEFORE the block survives it; a write
                // inside the block is the block's own output.
                .filter { it.name !in names && it.offset < holder.textRange.startOffset }
                .groupBy { it.name }
                .values
                .mapNotNull { it.maxByOrNull { assign -> assign.offset } }
                .forEach {
                    references.add(ReferenceInfo(it.name, holder.textRange.startOffset - 1))
                }
        }
}
