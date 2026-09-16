package io.github.unurgunite.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalClassBody
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Enclosing-scope lookup and usage collection for instance/class variables.
 * Target collection lives in [CrystalInstanceVarTargets].
 */
internal object CrystalInstanceVarScope {
    fun findEnclosingClass(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(
            element,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java,
        )

    fun getClassBody(classDef: PsiElement): CrystalClassBody? =
        when (classDef) {
            is CrystalClassDefinition -> classDef.classBody
            is CrystalStructDefinition -> classDef.classBody
            is CrystalModuleDefinition -> classDef.classBody
            else -> null
        }

    fun collectAllOccurrences(
        element: PsiElement,
        varName: String,
        usages: MutableList<PsiElement>,
    ) {
        // Check for CrystalInstanceVarAccess / CrystalClassVarAccess composite elements
        if ((element is CrystalInstanceVarAccess || element is CrystalClassVarAccess) &&
            element.text == varName
        ) {
            usages.add(element)
            return // No need to recurse into leaf
        }
        // Also check leaf tokens (for @name in property_declaration or parameter where it's still a raw token)
        if (isBareVarToken(element, varName)) {
            usages.add(element)
            return
        }
        // Recurse via AST children
        var child = element.node.firstChildNode
        while (child != null) {
            collectAllOccurrences(child.psi, varName, usages)
            child = child.treeNext
        }
    }

    /**
     * Nested class/struct/module bodies own their variables; enum bodies do not
     * introduce a variable scope, so recursion continues into them.
     */
    fun isNestedScopeBoundary(child: PsiElement): Boolean =
        child is CrystalClassDefinition ||
            child is CrystalStructDefinition ||
            child is CrystalModuleDefinition

    /**
     * Raw `@name`/`@@name` leaf token that is not wrapped in a `*_VAR_ACCESS`
     * composite (happens after parse errors) and matches the searched name.
     */
    private fun isBareVarToken(
        element: PsiElement,
        varName: String,
    ): Boolean {
        val nodeType = element.node.elementType
        if (nodeType != CrystalTypes.INSTANCE_VAR && nodeType != CrystalTypes.CLASS_VAR) return false
        if (element.text != varName) return false
        return element.parent !is CrystalInstanceVarAccess && element.parent !is CrystalClassVarAccess
    }
}
