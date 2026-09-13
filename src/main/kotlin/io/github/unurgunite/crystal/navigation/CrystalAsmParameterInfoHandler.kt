package io.github.unurgunite.crystal.navigation

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAsmExpression
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Provides parameter info (Ctrl+P) for Crystal asm() expressions.
 * Shows the 5 sections: template : outputs : inputs : clobbers : options
 * and highlights the current section based on cursor position (colon count).
 */
class CrystalAsmParameterInfoHandler : ParameterInfoHandler<CrystalAsmExpression, CrystalAsmParameterInfoHandler.AsmInfo> {
    data class AsmInfo(
        val sections: List<String> = listOf("template", "outputs", "inputs", "clobbers", "options"),
    )

    companion object {
        // Length of the "asm(" keyword, used to anchor the parameter hint.
        private const val ASM_KEYWORD_LENGTH = 4

        // Sections are joined as "template : outputs : ..." — the " : " separator length.
        private const val SECTION_SEPARATOR_LENGTH = 3

        // Highest section index (template=0 .. options=4); extra colons stay on the last section.
        private const val MAX_SECTION_INDEX = 4
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): CrystalAsmExpression? {
        val asmExpr = findAsmExpression(context.file, context.offset) ?: return null
        context.itemsToShow = arrayOf(AsmInfo())
        return asmExpr
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): CrystalAsmExpression? =
        findAsmExpression(context.file, context.offset)

    override fun showParameterInfo(
        element: CrystalAsmExpression,
        context: CreateParameterInfoContext,
    ) {
        context.showHint(element, element.textRange.startOffset + ASM_KEYWORD_LENGTH, this) // after "asm("
    }

    override fun updateParameterInfo(
        parameterOwner: CrystalAsmExpression,
        context: UpdateParameterInfoContext,
    ) {
        val index = computeCurrentSection(parameterOwner, context.offset)
        context.setCurrentParameter(index)
    }

    override fun updateUI(
        info: AsmInfo?,
        context: ParameterInfoUIContext,
    ) {
        if (info == null) {
            context.isUIComponentEnabled = false
            return
        }

        val sections = info.sections
        val text = sections.joinToString(" : ")
        val currentIndex = context.currentParameterIndex

        var startHighlight = -1
        var endHighlight = -1

        if (currentIndex in sections.indices) {
            startHighlight = sections.take(currentIndex).sumOf { it.length + SECTION_SEPARATOR_LENGTH }
            endHighlight = startHighlight + sections[currentIndex].length
        }

        context.setupUIComponentPresentation(
            text,
            startHighlight,
            endHighlight,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    private fun findAsmExpression(
        file: com.intellij.psi.PsiFile,
        offset: Int,
    ): CrystalAsmExpression? {
        val element = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return null
        return PsiTreeUtil.getParentOfType(element, CrystalAsmExpression::class.java)
    }

    /**
     * Determines which section the cursor is in by counting COLON and DOUBLE_COLON tokens
     * before the cursor offset within the asm expression.
     */
    private fun computeCurrentSection(
        asmExpr: CrystalAsmExpression,
        offset: Int,
    ): Int {
        var colonCount = 0
        var child = asmExpr.firstChild
        while (child != null) {
            if (child.textRange.startOffset >= offset) break
            when (child.node.elementType) {
                CrystalTypes.COLON -> colonCount++
                CrystalTypes.DOUBLE_COLON -> colonCount += 2
            }
            child = child.nextSibling
        }
        return colonCount.coerceAtMost(MAX_SECTION_INDEX)
    }
}
