package io.github.unurgunite.crystal.documentation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.psi.CrystalArgument
import io.github.unurgunite.crystal.psi.CrystalBareArgument
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex

/**
 * Target resolution for Quick Documentation: which PSI element the popup
 * describes for a hovered/caret element. Split out of
 * `CrystalDocumentationProvider` (which exceeded the function budget).
 */
internal object CrystalDocTargetResolve {
    // Budgets for walking up the PSI tree looking for an enclosing definition.
    // Definition hovers walk at most this far; the variable-identifier check
    // goes one level deeper (it starts from a leaf token, not a composite).
    private const val MAX_DEFINITION_WALK_UP_DEPTH = 4
    private const val MAX_VARIABLE_CHECK_WALK_UP_DEPTH = 5

    fun resolveTarget(element: PsiElement?): PsiElement? {
        if (element == null) return null
        // Already a definition or parameter — return directly
        if (CrystalPsiUtils.isDefinition(element)) return element
        // Variable identifier — return directly for type info rendering
        if (isVariableIdentifier(element)) return element
        // Try resolving via reference, else walk up to a definition
        // (for leaf tokens like IDENTIFIER in a method name).
        return resolveViaElementReference(element) ?: walkUpToParentDefinition(element)
    }

    /** Reference target, resolved recursively (an assignment resolves to its definition). */
    private fun resolveViaElementReference(element: PsiElement): PsiElement? {
        val resolved = element.reference?.resolve() ?: return null
        return resolveTarget(resolved)
    }

    /** Enclosing method/type definition within the walk-up budget. */
    private fun walkUpToParentDefinition(element: PsiElement): PsiElement? {
        var current: PsiElement? = element.parent
        var depth = 0
        while (current != null && depth < MAX_DEFINITION_WALK_UP_DEPTH) {
            if (isWalkUpDefinition(current)) return current
            current = current.parent
            depth++
        }
        return null
    }

    /** Definition at or above [element] itself (hovering the definition name). */
    fun walkUpFromSelf(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < MAX_DEFINITION_WALK_UP_DEPTH) {
            if (CrystalPsiUtils.isDefinition(current)) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Method or type definition (deliberately not a parameter: the walk-up serves
     * leaf tokens inside definition names, and matching a parameter here would
     * shadow the parameter-specific popup built by the caller).
     */
    private fun isWalkUpDefinition(element: PsiElement): Boolean =
        element is CrystalMethodDefinition ||
            CrystalPsiUtils.isTypeDefinition(element)

    fun isVariableIdentifier(element: PsiElement): Boolean {
        // An IDENTIFIER token or CrystalVariableReference that is NOT inside a definition/parameter
        val isIdent = element.node?.elementType == CrystalTypes.IDENTIFIER
        val isVarRef = element is CrystalVariableReference
        if (!isIdent && !isVarRef) {
            // Also check if it's a CrystalExpression wrapping a variable reference
            if (element is CrystalExpression) {
                val varRef = element.variableReferenceList.firstOrNull()
                if (varRef != null) return isVariableIdentifier(varRef)
            }
            return false
        }
        return !isInsideDefinition(element)
    }

    /** True when a definition/parameter encloses [element] within the check budget. */
    private fun isInsideDefinition(element: PsiElement): Boolean {
        var current: PsiElement? = element.parent
        var depth = 0
        while (current != null && depth < MAX_VARIABLE_CHECK_WALK_UP_DEPTH) {
            if (CrystalPsiUtils.isDefinition(current)) {
                return true
            }
            current = current.parent
            depth++
        }
        return false
    }

    /**
     * Unwraps argument wrappers (CrystalArgument, CrystalBareArgument) to find
     * the actual expression inside. E.g. for `foo(arr)`, extracts `arr` from
     * the CrystalArgument wrapper.
     */
    fun unwrapArgument(element: PsiElement): PsiElement =
        when (element) {
            is CrystalArgument -> {
                element.expression ?: element
            }

            is CrystalBareArgument -> {
                // CrystalBareArgument contains the expression as a child
                val expr = element.children.firstOrNull { it is CrystalExpression }
                expr ?: element
            }

            else -> {
                element
            }
        }

    /**
     * Reference target when it is documentation-worthy (a definition, parameter
     * or variable identifier); null lets the caller try DOT-call/declaration fallbacks.
     */
    fun resolveViaReference(unwrapped: PsiElement): PsiElement? {
        // Try PsiReference on the context element (and its parent, for leaf tokens
        // whose reference lives on the wrapping composite, e.g. IDENTIFIER inside
        // CrystalVariableReference).
        val ref = unwrapped.reference ?: unwrapped.parent?.reference
        val resolved = ref?.resolve() ?: return null
        // If resolved element is a definition/parameter, return it directly
        if (CrystalPsiUtils.isDefinition(resolved)) {
            return resolved
        }
        // If resolved element is a variable identifier, return it for type info
        if (isVariableIdentifier(resolved)) return resolved
        // Otherwise, resolved to something like an assignment — continue to next steps
        return null
    }

    /**
     * DOT-call identifiers (Apfel.tanzen, a.essen, Senf.new) have no PsiReference.
     * Delegates to the GotoDeclarationHandler, which resolves the DOT pattern.
     */
    fun resolveViaDotCall(
        unwrapped: PsiElement,
        targetOffset: Int,
        editor: Editor,
    ): PsiElement? {
        val handler: GotoDeclarationHandler =
            io.github.unurgunite.crystal.navigation
                .CrystalGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(unwrapped, targetOffset, editor)
        return targets?.firstOrNull()
    }

    /** Last resort: direct StubIndex lookup for classes/methods by hovered text. */
    fun lookupByName(unwrapped: PsiElement): PsiElement? {
        val project = unwrapped.project
        val scope = GlobalSearchScope.allScope(project)
        val name = unwrapped.text

        val classes =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                name,
                project,
                scope,
                CrystalNamedElement::class.java,
            )
        if (classes.isNotEmpty()) return classes.firstOrNull()

        val methods =
            StubIndex.getElements(
                CrystalMethodIndex.KEY,
                name,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        if (methods.isNotEmpty()) return methods.firstOrNull()

        return null
    }
}
