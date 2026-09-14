package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalStatement
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Local-scope name checks: is a call name actually a local variable or method
 * parameter? Disambiguates `text * times` (binary op) from `text(*times)`
 * (bare call with splat).
 */
internal object CrystalLocalScope {
    /**
     * Checks if the given name is a local variable or method parameter in the enclosing scope.
     */
    fun isLocalVariableOrParameter(
        callExpr: PsiElement,
        name: String,
    ): Boolean {
        // Walk up to find enclosing method definition
        var parent = callExpr.parent
        while (parent != null) {
            if (parent is CrystalMethodDefinition) {
                if (isMethodParameter(parent, name)) return true
                break
            }
            parent = parent.parent
        }

        // Check local variable assignments before this expression
        return hasLocalAssignment(callExpr, name)
    }

    /** True when [name] is a parameter of [method] (instance vars matched without `@`). */
    private fun isMethodParameter(
        method: CrystalMethodDefinition,
        name: String,
    ): Boolean {
        val params = method.parameterList?.parameterList ?: emptyList()
        for (param in params) {
            val paramName =
                param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                    ?: param.node
                        .findChildByType(CrystalTypes.INSTANCE_VAR)
                        ?.text
                        ?.removePrefix("@")
            if (paramName == name) return true
        }
        return false
    }

    /** True when a `name = ...` assignment precedes [callExpr] in its block. */
    private fun hasLocalAssignment(
        callExpr: PsiElement,
        name: String,
    ): Boolean {
        var sibling = callExpr.parent?.prevSibling
        while (sibling != null) {
            if (sibling is CrystalStatement) {
                val assignment = sibling.firstChild as? CrystalAssignment
                if (assignment != null) {
                    val varName = assignment.firstChild?.text
                    if (varName == name) return true
                }
            }
            sibling = sibling.prevSibling
        }
        return false
    }
}
