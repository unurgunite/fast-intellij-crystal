package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Low-level annotation emission for the Crystal annotator: silent INFORMATION
 * annotations on an element or an offset range.
 */
internal object CrystalAnnotationEmit {
    fun apply(
        holder: AnnotationHolder,
        element: PsiElement,
        key: TextAttributesKey,
    ) {
        holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(key)
            .create()
    }

    fun applyRange(
        holder: AnnotationHolder,
        baseOffset: Int,
        range: IntRange,
        key: TextAttributesKey,
    ) {
        holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(baseOffset + range.first, baseOffset + range.last + 1))
            .textAttributes(key)
            .create()
    }
}
