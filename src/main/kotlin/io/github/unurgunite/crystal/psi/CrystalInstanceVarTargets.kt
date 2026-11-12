package io.github.unurgunite.crystal.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalPropertyDeclaration
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * Class-body target collection for instance/class variables: property
 * declarations, `getter`/`setter`/`property` macro calls and assignments.
 * Split out of `CrystalInstanceVarFinder` (which exceeded the function budget).
 */
internal object CrystalInstanceVarTargets {
    /**
     * Priority buckets of definition targets in [classBody]:
     * property declarations, macro declarations, assignments.
     */
    fun collectTargets(
        classBody: PsiElement,
        varName: String,
        bareName: String,
    ): Triple<MutableList<PsiElement>, MutableList<PsiElement>, MutableList<PsiElement>> {
        val collected = Collected(varName, bareName)
        collectTargets(classBody, collected)
        return Triple(collected.propertyDecls, collected.macroDecls, collected.assignments)
    }

    /** Names being searched plus the three output buckets they fill. */
    private class Collected(
        val varName: String,
        val bareName: String,
    ) {
        val propertyDecls = mutableListOf<PsiElement>()
        val macroDecls = mutableListOf<PsiElement>()
        val assignments = mutableListOf<PsiElement>()
    }

    private fun collectTargets(
        element: PsiElement,
        collected: Collected,
    ) {
        var astChild = element.node.firstChildNode
        while (astChild != null) {
            val child = astChild.psi

            collectPropertyDeclaration(child, collected)
            collectMacroDeclaration(child, collected)
            collectAssignmentCandidate(child, astChild, collected)

            // Always recurse (but don't cross into nested class/struct/module definitions —
            // enum bodies share the enclosing scope, so they are not a boundary)
            if (!CrystalInstanceVarScope.isNestedScopeBoundary(child) && astChild.firstChildNode != null) {
                collectTargets(child, collected)
            }

            astChild = astChild.treeNext
        }
    }

    /**
     * Property declaration: @name : Type or name : Type.
     * The variable may be a raw leaf token or a composite *_VAR_ACCESS
     * (`@size : Int32` parses as PROPERTY_DECLARATION > INSTANCE_VAR_ACCESS).
     */
    private fun collectPropertyDeclaration(
        child: PsiElement,
        collected: Collected,
    ) {
        if (child !is CrystalPropertyDeclaration) return
        val declName =
            child.node.findChildByType(CrystalTypes.INSTANCE_VAR)?.text
                ?: child.node.findChildByType(CrystalTypes.CLASS_VAR)?.text
                ?: child.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                ?: findVarAccessText(child, CrystalInstanceVarAccess::class.java)
                ?: findVarAccessText(child, CrystalClassVarAccess::class.java)
        if (declName == collected.bareName || declName == collected.varName) {
            collected.propertyDecls.add(child)
        }
    }

    /** Text of the first strict [accessClass] child, if present. */
    private fun findVarAccessText(
        child: PsiElement,
        accessClass: Class<out PsiElement>,
    ): String? =
        com.intellij.psi.util.PsiTreeUtil
            .findChildOfType(child, accessClass, false)
            ?.text

    /**
     * Method calls (with or without parens): getter name, setter name, property name.
     * Bare commands (getter name : String) also parse as CrystalBareCommandExpression.
     * property_macro (getter name : Type) is its own PSI element since the grammar fix.
     */
    private fun collectMacroDeclaration(
        child: PsiElement,
        collected: Collected,
    ) {
        if (!CrystalPsiUtils.isCallExpression(child)) return
        val callName = child.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
        if (callName !in PROPERTY_MACROS) return
        val argText = getFirstArgumentTextGeneric(child)
        if (argText == collected.bareName) {
            collected.macroDecls.add(child)
        }
    }

    /**
     * Assignment candidate: `@name = ...`, either as a raw INSTANCE_VAR/CLASS_VAR
     * leaf token or as a composite access. Shapes live in [CrystalVarAssignments].
     */
    private fun collectAssignmentCandidate(
        child: PsiElement,
        astChild: ASTNode,
        collected: Collected,
    ) {
        CrystalVarAssignments.collectLeafAssignment(child, astChild, collected.varName, collected.assignments)
        CrystalVarAssignments.collectCompositeAssignment(child, collected.varName, collected.assignments)
    }

    private fun getFirstArgumentTextGeneric(element: PsiElement): String? {
        // Look for the first IDENTIFIER in argument position (after the method name)
        // Works for both call_args (parens) and bare_argument_list (no parens)
        var foundMethodName = false
        var astChild = element.node.firstChildNode
        while (astChild != null) {
            if (!foundMethodName) {
                if (astChild.elementType == CrystalTypes.IDENTIFIER || astChild.elementType == CrystalTypes.CONSTANT) {
                    foundMethodName = true
                }
            } else {
                // Find first IDENTIFIER in children (the argument name)
                val ident = findFirstIdentInTree(astChild)
                if (ident != null) return ident
            }
            astChild = astChild.treeNext
        }
        return null
    }

    private fun findFirstIdentInTree(node: ASTNode): String? {
        if (node.elementType == CrystalTypes.IDENTIFIER) return node.text
        var child = node.firstChildNode
        while (child != null) {
            val result = findFirstIdentInTree(child)
            if (result != null) return result
            child = child.treeNext
        }
        return null
    }

    private val PROPERTY_MACROS =
        setOf(
            "getter",
            "getter!",
            "getter?",
            "setter",
            "setter!",
            "property",
            "property!",
            "property?",
        )
}
