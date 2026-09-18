package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAssignment

/**
 * Per-file index of assignments by LHS target text.
 *
 * [CrystalTypeInference.inferFromAssignmentList] used to walk the whole file
 * (`collectElementsOfType`) on EVERY inference — and inference recurses
 * (receiver chains re-enter `inferTypeList`), so one hover/Ctrl+click on a
 * big file like `path.cr` fanned out into nested full-file walks until the
 * IDE drowned ("Resolving reference" + memory blowup). This index walks once
 * per file version (dropped on any PSI change) and serves name lookups.
 */
internal object CrystalAssignmentIndex {
    /**
     * Assignments in [file] whose LHS is [name] or `@name` (same match rule as
     * inference: a plain local also sees `@`-prefixed targets), in document
     * order. Empty when the file assigns nothing to [name].
     */
    fun assignmentsFor(
        file: PsiFile,
        name: String,
    ): List<CrystalAssignment> {
        val byName =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result(indexFile(file), PsiModificationTracker.MODIFICATION_COUNT)
            }
        val plain = byName[name] ?: emptyList()
        val ivar = byName["@$name"] ?: emptyList()
        if (plain.isEmpty()) return ivar
        if (ivar.isEmpty()) return plain
        return (plain + ivar).sortedBy { it.textOffset }
    }

    /** All assignments of [file] grouped by exact LHS target text, in document order. */
    private fun indexFile(file: PsiFile): Map<String, List<CrystalAssignment>> {
        val byName = LinkedHashMap<String, MutableList<CrystalAssignment>>()
        for (assignment in PsiTreeUtil.collectElementsOfType(file, CrystalAssignment::class.java)) {
            val target = CrystalAssignmentTarget.targetText(assignment) ?: continue
            byName.getOrPut(target) { ArrayList() }.add(assignment)
        }
        return byName
    }
}
