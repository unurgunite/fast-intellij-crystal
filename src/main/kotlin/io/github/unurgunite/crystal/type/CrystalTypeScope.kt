package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.util.extractParameterName
import io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference.splitTypeNames

/**
 * Scope rules for local-variable typing: which declaration may type a name at a
 * given use site. Kept out of [CrystalTypeInference] to stay within detekt's
 * function budget.
 */
internal object CrystalTypeScope {
    /**
     * Nearest enclosing def/macro/block. A block IS a scope opener here: its
     * parameters must shadow same-named locals from other methods
     * (`each do |command|` must not inherit `command = ...`).
     */
    fun enclosing(element: PsiElement?): PsiElement? =
        generateSequence(element?.parent) { it.parent }
            .takeWhile { it !is PsiFile }
            .firstOrNull { it is CrystalMethodDefinition || it is CrystalMacroDefinition || it is CrystalBlock }

    /**
     * Type(s) of a parameter named [name] visible at [context], or null when no
     * such parameter exists. An untyped parameter yields an empty list, which
     * intentionally blocks assignment-based inference (the parameter shadows it).
     */
    fun parameterTypes(
        name: String,
        context: PsiElement,
    ): List<String>? {
        val params = parameterListOf(enclosing(context) ?: return null) ?: return null
        val param = params.firstOrNull { extractParameterName(it) == name } ?: return null
        return param.typeReference?.let { splitTypeNames(it.text) } ?: emptyList()
    }

    private fun parameterListOf(scope: PsiElement): List<CrystalParameter>? =
        when (scope) {
            is CrystalMethodDefinition -> scope.parameterList
            is CrystalMacroDefinition -> scope.parameterList
            is CrystalBlock -> scope.parameterList
            else -> null
        }?.parameterList
}
