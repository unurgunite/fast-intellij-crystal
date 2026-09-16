package io.github.unurgunite.crystal.navigation.ivar

import com.intellij.psi.PsiElement

/**
 * Finds definitions and usages of instance variables (@name) and class variables (@@name)
 * within the enclosing class/struct.
 *
 * Priority for Go to Definition:
 * 1. Property declarations (@name : Type)
 * 2. getter/setter/property macro calls (getter name, property name : Type)
 * 3. All assignments (@name = ...) as fallback
 *
 * Target collection lives in [CrystalInstanceVarTargets], scope lookup in
 * [CrystalInstanceVarScope].
 */
object CrystalInstanceVarFinder {
    /**
     * Find the definition target(s) for an instance/class variable.
     * Returns property declarations first, then getter/setter/property calls, then assignments.
     */
    fun findDefinitionTargets(
        varName: String,
        context: PsiElement,
    ): List<PsiElement> {
        val classBody = enclosingClassBody(context) ?: return emptyList()

        // Strip @ or @@ prefix for matching against getter/setter/property names
        val bareName = varName.removePrefix("@@").removePrefix("@")

        val (propertyDecls, macroDecls, assignments) =
            CrystalInstanceVarTargets.collectTargets(classBody, varName, bareName)

        // Priority: property declarations > macro declarations > assignments
        if (propertyDecls.isNotEmpty()) return propertyDecls
        if (macroDecls.isNotEmpty()) return macroDecls
        return assignments
    }

    /**
     * Find all usages (reads and writes) of an instance/class variable within the enclosing class.
     */
    fun findAllUsages(
        varName: String,
        context: PsiElement,
    ): List<PsiElement> {
        val classBody = enclosingClassBody(context) ?: return emptyList()

        val usages = mutableListOf<PsiElement>()
        CrystalInstanceVarScope.collectAllOccurrences(classBody, varName, usages)
        return usages
    }

    /** Class body of the type enclosing [context], or null outside a type. */
    private fun enclosingClassBody(context: PsiElement): PsiElement? {
        val enclosingClass = CrystalInstanceVarScope.findEnclosingClass(context) ?: return null
        return CrystalInstanceVarScope.getClassBody(enclosingClass)
    }
}
