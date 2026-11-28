package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.AssignmentInfo
import io.github.unurgunite.crystal.inspections.CrystalUnusedVarCollect.ReferenceInfo
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalProcLiteral
import io.github.unurgunite.crystal.psi.CrystalUntilStatement
import io.github.unurgunite.crystal.psi.CrystalWhileStatement

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

/**
 * A block body runs once per iteration, so a write at its end is read at the
 * top of the next iteration: `start_token = nil` inside `run do ... end` is
 * live because the next `run` call reads the variable again.
 *
 * Records a read for every write inside a block when the same name is read
 * anywhere in that block. Location matters only for ordering, so the read is
 * placed at the block's start — before the write, which is where the next
 * iteration reads it.
 *
 * `while` / `until` loop bodies repeat the same way, but the read that closes
 * the cycle sits in the loop *condition*, not in the body: `cursor = 0` then
 * `while start = script.index(needle, cursor)` re-reads the `cursor` written by
 * the body on the next iteration. Only the block list was walked, so that write
 * looked dead and was reported as unused.
 */
internal fun addRepeatReads(
    scope: PsiElement,
    assignments: List<AssignmentInfo>,
    references: List<ReferenceInfo>,
    sink: MutableList<ReferenceInfo>,
) {
    val holders = mutableListOf<PsiElement>()
    holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalBlock::class.java))
    holders.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalProcLiteral::class.java))
    holders
        .filter { it !== scope }
        .forEach { holder ->
            val start = holder.textRange.startOffset
            val end = holder.textRange.endOffset
            // Only a read at or before the write is the next iteration's read.
            // A read after it is the same iteration's and already counted, so
            // `x = 1; x = 2; use x` stays a dead store.
            val reads = references.filter { it.offset in start until end }
            assignments
                .filter { it.offset in start until end }
                .filter { a -> reads.any { it.name == a.name && it.offset <= a.offset } }
                .forEach { sink.add(ReferenceInfo(it.name, it.offset + 1)) }
        }

    val loops = mutableListOf<PsiElement>()
    loops.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalWhileStatement::class.java))
    loops.addAll(PsiTreeUtil.findChildrenOfType(scope, CrystalUntilStatement::class.java))
    loops
        .filter { it !== scope }
        .forEach { loop ->
            val condition =
                (loop as? CrystalWhileStatement)?.condition
                    ?: (loop as? CrystalUntilStatement)?.condition
                    ?: return@forEach
            val body =
                (loop as? CrystalWhileStatement)?.statementList
                    ?: (loop as? CrystalUntilStatement)?.statementList
                    ?: return@forEach
            val condStart = condition.textRange.startOffset
            val condEnd = condition.textRange.endOffset
            // The condition is evaluated again after the body runs, so any name
            // it reads outlives a write of that name inside the body.
            val condNames = references.filter { it.offset in condStart until condEnd }.map { it.name }.toSet()
            assignments
                .filter { it.name in condNames && it.offset in body.textRange.startOffset until body.textRange.endOffset }
                .forEach { sink.add(ReferenceInfo(it.name, it.offset + 1)) }
        }
}
