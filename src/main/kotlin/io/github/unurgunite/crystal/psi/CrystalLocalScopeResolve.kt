package io.github.unurgunite.crystal.psi

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

/**
 * Local-scope resolution for references: walks up the PSI tree (never crossing
 * the file boundary) looking for assignments and parameters. Split out of
 * `CrystalReference` (which exceeded the function budget).
 */
internal object CrystalLocalScopeResolve {
    fun resolveLocal(
        element: PsiElement,
        name: String,
    ): PsiElement? {
        val containingFile = element.containingFile ?: return null
        var scope: PsiElement? = element.parent
        // Walk up the PSI tree, but NEVER cross the file boundary — climbing into
        // PsiDirectory would traverse the whole project tree and lazily parse
        // every sibling file (including .sh build scripts), freezing the IDE for
        // tens of seconds on Ctrl+B / identifier highlighting.
        while (scope != null && scope !== containingFile) {
            // Walk siblings before the reference looking for assignments like "name = ..."
            var sibling = scope.prevSibling
            while (sibling != null) {
                val assignment = findAssignmentWithName(sibling, name)
                if (assignment != null) return assignment
                sibling = sibling.prevSibling
            }
            // Check parameters if we're inside a method, macro or block:
            // method/macro params, then block params (e.g. |ola| in each do |ola|).
            // A method/macro scope stops the walk either way — don't look beyond
            // method boundaries for locals; a block scope only returns on a hit.
            when (scope) {
                is CrystalMethodDefinition, is CrystalMacroDefinition -> return findParameterWithName(scope, name)
                is CrystalBlock -> findParameterWithName(scope, name)?.let { return it }
            }
            scope = scope.parent
        }
        return null
    }

    /**
     * IDENTIFIER leaf of the parameter named [name] in a method/macro/block
     * parameter list, or null.
     */
    private fun findParameterWithName(
        scope: PsiElement,
        name: String,
    ): PsiElement? {
        val paramList =
            when (scope) {
                is CrystalMethodDefinition -> scope.parameterList
                is CrystalMacroDefinition -> scope.parameterList
                is CrystalBlock -> scope.parameterList
                else -> null
            } ?: return null
        for (param in paramList.parameterList) {
            val paramIdent = param.node.findChildByType(CrystalTypes.IDENTIFIER)
            if (paramIdent?.text == name) return paramIdent.psi
        }
        return null
    }

    /**
     * Recursively searches a PSI subtree for a CrystalAssignment node
     * whose variable name matches [targetName].
     *
     * Stops at method/macro/class/struct boundaries to avoid resolving
     * across scope boundaries — a variable in method A should not resolve
     * to an assignment in sibling method B.
     *
     * Also refuses to cross file/directory boundaries — a defensive guard
     * so any future regression in [resolveLocal] cannot cascade into the
     * project tree and lazily parse every sibling file.
     */
    private fun findAssignmentWithName(
        element: PsiElement,
        targetName: String,
    ): PsiElement? {
        // Don't cross scope boundaries
        if (CrystalPsiUtils.isScopeBoundary(element)) {
            return null
        }
        // Hard boundary: never recurse into files or directories. This is a defensive
        // guard — resolveLocal() also stops at the file boundary, but this ensures
        // that even if the walk escaped, we cannot trigger lazy parsing of every
        // file in the project (which caused 40+ second EDT freezes).
        if (element is PsiFile || element is PsiDirectory) return null
        if (element is CrystalAssignment && element is PsiNameIdentifierOwner &&
            (element as PsiNameIdentifierOwner).name == targetName
        ) {
            return element
        }
        for (child in element.children) {
            val result = findAssignmentWithName(child, targetName)
            if (result != null) return result
        }
        return null
    }
}
