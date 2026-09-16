package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiFile
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
     * A single field of a `record` definition.
     *
     * Canonical type lives in [CrystalRecordLookup]; this alias stays for
     * binary/source compatibility of existing callers.
     */
    typealias RecordFieldInfo = CrystalRecordLookup.RecordFieldInfo

    /**
     * Checks whether a class name is defined via a `record` definition in the given file.
     * Returns the [CrystalRecordDefinition] if found, null otherwise.
     *
     * Canonical implementation lives in [CrystalRecordLookup]; this delegate
     * stays for binary/source compatibility of existing callers.
     */
    fun findRecordDefinition(
        className: String,
        file: PsiFile,
    ): CrystalRecordDefinition? = CrystalRecordLookup.findRecordDefinition(className, file)

    /**
     * Extracts the field list from a `record` definition. Each field yields its name,
     * optional type text, and optional default-value text.
     *
     * Canonical implementation lives in [CrystalRecordLookup]; this delegate
     * stays for binary/source compatibility of existing callers.
     */
    fun extractRecordFields(rec: CrystalRecordDefinition): List<RecordFieldInfo> = CrystalRecordLookup.extractRecordFields(rec)

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
