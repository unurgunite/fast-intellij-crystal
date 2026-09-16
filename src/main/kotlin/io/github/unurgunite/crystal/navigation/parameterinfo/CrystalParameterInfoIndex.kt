package io.github.unurgunite.crystal.navigation.parameterinfo

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.completion.CrystalRecordCompletion
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalRecordDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Cursor indexing for parameter info: which parameter the cursor sits on
 * (top-level comma counting with bracket-depth tracking) plus `record`
 * parameter-list rendering.
 */
internal object CrystalParameterInfoIndex {
    /**
     * Computes which parameter index the cursor is currently at.
     */
    fun computeCurrentParameterIndex(
        argsHolder: PsiElement,
        offset: Int,
    ): Int {
        // Case: synthetic anchor
        if (argsHolder is CrystalParameterInfoAnchor) {
            return indexInAnchor(argsHolder, offset)
        }

        // Case: bare-call backtracking anchor (IDENTIFIER/CONSTANT leaf = method name) — legacy
        val holderType = argsHolder.node?.elementType
        if (holderType == CrystalTypes.IDENTIFIER || holderType == CrystalTypes.CONSTANT) {
            // Count commas in file text between method name end and cursor
            val fileText = argsHolder.containingFile?.text ?: return 0
            val argsStart = argsHolder.textRange.endOffset
            return countTopLevelCommas(fileText, argsStart, offset)
        }

        return indexInHolderText(argsHolder, offset)
    }

    /**
     * Comma-based index for synthetic anchors: from after `(` (or after the
     * method name for bare calls) up to the cursor.
     */
    private fun indexInAnchor(
        argsHolder: CrystalParameterInfoAnchor,
        offset: Int,
    ): Int {
        val fileText = argsHolder.containingFile.text ?: return 0
        val argsStart =
            if (argsHolder.lparenOffset >= 0) {
                argsHolder.lparenOffset + 1 // skip '('
            } else {
                argsHolder.nameToken.textRange.endOffset // bare call: after method name
            }
        return countTopLevelCommas(fileText, argsStart, offset)
    }

    /**
     * Comma-based index inside structured holders; falls back to a text scan for
     * an unmatched LPAREN when the PSI is broken.
     */
    private fun indexInHolderText(
        argsHolder: PsiElement,
        offset: Int,
    ): Int {
        val holderText = argsHolder.text
        val startOffset = argsHolder.textRange.startOffset
        val relativeOffset = (offset - startOffset).coerceIn(0, holderText.length)

        if (argsHolder is CrystalCallArgs) {
            return countTopLevelCommas(holderText, 1, relativeOffset) // skip '('
        }
        if (argsHolder is CrystalBareArgumentList) {
            return countTopLevelCommas(holderText, 0, relativeOffset)
        }
        // Broken PSI fallback: find the unmatched LPAREN relative to cursor in file text
        val fileText = argsHolder.containingFile?.text ?: holderText
        val lparenFileOffset = CrystalBareCallScanner.findUnmatchedLparen(fileText, offset)
        if (lparenFileOffset >= 0) {
            return countTopLevelCommas(fileText, lparenFileOffset + 1, offset)
        }
        val parenPos = holderText.indexOf('(')
        val startPos = if (parenPos >= 0 && parenPos < relativeOffset) parenPos + 1 else 0
        return countTopLevelCommas(holderText, startPos, relativeOffset)
    }

    fun countTopLevelCommas(
        text: String,
        from: Int,
        to: Int,
    ): Int {
        var index = 0
        var depth = 0
        for (i in from until to.coerceAtMost(text.length)) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) index++
            }
        }
        return index
    }

    /**
     * Extracts a parameter list from a `record` macro call for parameter info display.
     */
    fun extractRecordParameterList(recordDef: CrystalRecordDefinition): RecordParameterInfo {
        val fields = CrystalRecordCompletion.extractRecordFields(recordDef)
        if (fields.isEmpty()) return RecordParameterInfo(emptyList())

        val params =
            fields.map { f ->
                val param =
                    buildString {
                        append(f.name)
                        if (f.typeText != null) append(" : ").append(f.typeText)
                        if (f.defaultText != null) append(" = ").append(f.defaultText)
                    }
                RecordParam(param)
            }
        return RecordParameterInfo(params)
    }
}
