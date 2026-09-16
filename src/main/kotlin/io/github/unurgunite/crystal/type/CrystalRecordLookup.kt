package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiFile
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
     */
    fun findRecordDefinition(
        className: String,
        file: PsiFile,
    ): CrystalRecordDefinition? {
        val records = PsiTreeUtil.findChildrenOfType(file, CrystalRecordDefinition::class.java)
        for (rec in records) {
            val nameNode = rec.node.findChildByType(CrystalTypes.CONSTANT) ?: continue
            if (nameNode.text == className) return rec
        }
        return null
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
