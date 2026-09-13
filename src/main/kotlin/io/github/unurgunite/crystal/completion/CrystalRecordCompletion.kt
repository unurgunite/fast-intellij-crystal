package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalRecordDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * `record` macro support for completion: finding record definitions, extracting
 * their fields and building `new` lookups with record signatures.
 * Split out of `CrystalCompletionHelper` (which exceeded the function budget).
 */
object CrystalRecordCompletion {
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

    /**
     * Extracts the parameter signature from a `record` definition.
     * Returns a string like "(host : String, port : Int32 = 80, ssl : Bool = false)".
     */
    fun getRecordSignature(rec: CrystalRecordDefinition): String {
        val fields = extractRecordFields(rec)
        if (fields.isEmpty()) return "()"
        val paramStrings =
            fields.map { f ->
                buildString {
                    append(f.name)
                    if (f.typeText != null) append(" : ").append(f.typeText)
                    if (f.defaultText != null) append(" = ").append(f.defaultText)
                }
            }
        return "(${paramStrings.joinToString(", ")})"
    }

    /**
     * Builds a LookupElement for `new` on a record type.
     */
    fun buildRecordNewLookup(
        rec: CrystalRecordDefinition,
        className: String,
    ): LookupElementBuilder {
        val signature = getRecordSignature(rec)
        val tailText = if (signature == "()") "" else signature
        return LookupElementBuilder
            .create("new")
            .withIcon(AllIcons.Nodes.Method)
            .withTailText(tailText, true)
            .withTypeText(className, true)
    }
}
