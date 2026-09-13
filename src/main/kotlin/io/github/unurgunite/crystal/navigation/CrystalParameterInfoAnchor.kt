package io.github.unurgunite.crystal.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import io.github.unurgunite.crystal.CrystalLanguage

/**
 * Synthetic anchor element for parameter info that provides an extended text range
 * covering from the method name token to the end of the statement (or cursor position).
 * This ensures IntelliJ's ParameterInfoController considers the cursor "inside" the anchor.
 */
class CrystalParameterInfoAnchor(
    psiManager: PsiManager,
    /** The IDENTIFIER/CONSTANT leaf token representing the method name. */
    val nameToken: PsiElement,
    /** End offset of the call statement (typically next newline or file end). */
    private val endOffset: Int,
    /** The offset of the unmatched LPAREN, or -1 for bare calls. */
    val lparenOffset: Int = -1,
) : LightElement(psiManager, CrystalLanguage) {
    override fun getTextRange(): TextRange = TextRange(nameToken.textRange.startOffset, endOffset)

    override fun getContainingFile(): PsiFile = nameToken.containingFile

    override fun toString(): String = "CrystalParameterInfoAnchor(${nameToken.text})"

    override fun isValid(): Boolean = nameToken.isValid

    override fun getText(): String {
        val file = nameToken.containingFile ?: return nameToken.text
        val fileText = file.text ?: return nameToken.text
        val start = nameToken.textRange.startOffset.coerceIn(0, fileText.length)
        val end = endOffset.coerceIn(start, fileText.length)
        return fileText.substring(start, end)
    }

    override fun getTextOffset(): Int = nameToken.textOffset

    override fun getStartOffsetInParent(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrystalParameterInfoAnchor) return false
        return nameToken.textOffset == other.nameToken.textOffset &&
            nameToken.containingFile == other.nameToken.containingFile &&
            lparenOffset == other.lparenOffset
    }

    override fun hashCode(): Int {
        var result = nameToken.textOffset
        result = 31 * result + lparenOffset
        return result
    }
}

/** Wrapper for record parameters to display in Ctrl+P. */
data class RecordParameterInfo(
    val params: List<RecordParam>,
)

data class RecordParam(
    val text: String,
) {
    override fun toString() = text
}
