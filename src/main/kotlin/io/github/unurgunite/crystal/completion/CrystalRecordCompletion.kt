package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import io.github.unurgunite.crystal.psi.CrystalRecordDefinition
import io.github.unurgunite.crystal.type.CrystalRecordLookup
import io.github.unurgunite.crystal.type.CrystalRecordLookup.RecordFieldInfo

/**
 * `record` macro support for completion: finding record definitions, extracting
 * their fields and building `new` lookups with record signatures.
 * Split out of `CrystalCompletionHelper` (which exceeded the function budget).
 */
object CrystalRecordCompletion {
    /**
     * Extracts the parameter signature from a `record` definition.
     * Returns a string like "(host : String, port : Int32 = 80, ssl : Bool = false)".
     */
    fun getRecordSignature(rec: CrystalRecordDefinition): String {
        val fields = CrystalRecordLookup.extractRecordFields(rec)
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
