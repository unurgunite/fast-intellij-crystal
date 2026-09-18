package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalRecordDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * `record` macro index queries: finding record definitions and extracting
 * their fields. Leaf of the dependency graph — depends only on PSI.
 * Split out of `CrystalRecordCompletion` (whose lookup builders stay in
 * completion/).
 */
object CrystalRecordLookup {
    /**
     * A single field of a `record` definition.
     */
    data class RecordFieldInfo(
        val name: String,
        val typeText: String?,
        val defaultText: String?,
    )

    /**
     * Checks whether a class name is defined via a `record` definition in the given file.
     * Returns the [CrystalRecordDefinition] if found, null otherwise.
     *
     * The name map is built once per file version (dropped on any PSI change):
     * this runs on the resolve hot path (every `.new`), and a fresh whole-file
     * walk per call froze the IDE on big files — same bug family as the
     * assignment scan (see [CrystalAssignmentIndex]).
     */
    fun findRecordDefinition(
        className: String,
        file: PsiFile,
    ): CrystalRecordDefinition? {
        val byName =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result(indexRecords(file), PsiModificationTracker.MODIFICATION_COUNT)
            }
        return byName[className]
    }

    /** All `record` definitions of [file] by name (first definition wins). */
    private fun indexRecords(file: PsiFile): Map<String, CrystalRecordDefinition> {
        val byName = LinkedHashMap<String, CrystalRecordDefinition>()
        for (rec in PsiTreeUtil.findChildrenOfType(file, CrystalRecordDefinition::class.java)) {
            val name = rec.node.findChildByType(CrystalTypes.CONSTANT)?.text ?: continue
            byName.putIfAbsent(name, rec)
        }
        return byName
    }

    /**
     * Extracts the field list from a `record` definition. Each field yields its name,
     * optional type text, and optional default-value text.
     */
    fun extractRecordFields(rec: CrystalRecordDefinition): List<RecordFieldInfo> {
        return rec.recordFieldList.mapNotNull { field ->
            val nameNode = field.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return@mapNotNull null
            RecordFieldInfo(nameNode.text, field.typeReference?.text, field.expression?.text)
        }
    }
}
