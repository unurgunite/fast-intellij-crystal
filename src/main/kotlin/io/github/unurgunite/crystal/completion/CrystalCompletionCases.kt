package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Case handlers for Crystal completion, split out of [CrystalCompletionContributor]
 * (which keeps only the IntelliJ dispatch surface plus shared helpers).
 *
 * Dispatch order in [addCompletions]:
 * 1. Suppress inside string literals and after numeric literals.
 * 2. Override methods after `def ` in a class body (non-terminal).
 * 3. Type-annotation, class-body and `@[`-annotation contexts (terminal each).
 * 4. DOT-calls: `CONSTANT.` (static) and `identifier.` (instance) — terminal.
 * 5. `CONSTANT::` nested types — terminal.
 * 6. Free text: scope items, then stdlib types + classes for empty/uppercase prefixes.
 *
 * DOT-call shapes live in [CrystalDotCompletion], free-text/scope shapes in
 * [CrystalScopeCompletion].
 */
internal object CrystalCompletionCases {
    fun addCompletions(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        val project = position.project

        if (isSuppressed(position)) return

        // Non-terminal class-level cases (also allow normal free-text completion)
        addClassLevelCompletions(position, result)

        // Case 5 + 7: type-annotation and `@[`-annotation contexts (terminal each)
        if (completeTypeOrAnnotationContext(position, project, result)) return

        // Cases 4 + 1b: DOT-calls and `CONSTANT::` nested types (terminal)
        if (completeDotOrNamespacedType(parameters, position, project, result)) return

        CrystalScopeCompletion.completeFreeText(parameters, position, project, result)
    }

    /** Suppression: inside string literals (not interpolation) or after numeric literals. */
    private fun isSuppressed(position: PsiElement): Boolean =
        // Suppress completion inside string literals (but not inside interpolation)
        isInsideStringLiteral(position) ||
            // Suppress completion after numeric literals (user is typing a number, not a name)
            CrystalCompletionContributor.isAfterNumericLiteral(position)

    /**
     * Non-terminal class-level cases: `def `-override methods and class-body
     * macros/keywords. Both fall through to free-text completion.
     */
    private fun addClassLevelCompletions(
        position: PsiElement,
        result: CompletionResultSet,
    ) {
        // Case 4: After `def ` inside a class/struct body — offer override methods
        if (CrystalCompletionContributor.isAfterDefKeywordInClassBody(position)) {
            for (lookup in CrystalOverrideMethodProvider.getOverrideLookups()) {
                result.addElement(lookup)
            }
            // Don't return — also allow normal free-text completion
        }

        // Case 6: Class/struct/module body level — macros and keywords (not inside a method)
        if (CrystalCompletionContributor.isInClassBodyNotMethod(position)) {
            for (lookup in CrystalClassBodyCompletionProvider.getClassBodyLookups()) {
                result.addElement(lookup)
            }
            // Don't return — also allow normal free-text completion
        }
    }

    /** Terminal type contexts: `:` annotations and `@[` attribute annotations. */
    private fun completeTypeOrAnnotationContext(
        position: PsiElement,
        project: Project,
        result: CompletionResultSet,
    ): Boolean {
        // Case 5: Type annotation context — after `:` in parameter or return type position
        if (CrystalCompletionContributor.isInTypeAnnotationContext(position)) {
            for (lookup in CrystalTypeCompletionProvider.getTypeLookups(position, project)) {
                result.addElement(lookup)
            }
            return true
        }

        // Case 7: Annotation context — after `@[`
        if (CrystalCompletionContributor.isInAnnotationContext(position)) {
            for (lookup in CrystalAnnotationCompletionProvider.getAnnotationLookups()) {
                result.addElement(lookup)
            }
            return true
        }
        return false
    }

    /** Terminal positional cases: `receiver.` DOT-calls and `CONSTANT::` nested types. */
    private fun completeDotOrNamespacedType(
        parameters: CompletionParameters,
        position: PsiElement,
        project: Project,
        result: CompletionResultSet,
    ): Boolean {
        // Check if we're after a dot
        val prevLeaf = CrystalCompletionContributor.getPreviousNonWhitespaceLeaf(position) ?: return false
        if (prevLeaf.text == ".") return completeDotTail(parameters, project, result, prevLeaf)
        // Case 1b: Double-colon after CONSTANT (Foo::<caret>) — show nested types only
        if (prevLeaf.text == "::") return completeNestedTypeTail(project, result, prevLeaf)
        return false
    }

    /** `receiver.<caret>`: static or instance DOT-call completion. */
    private fun completeDotTail(
        parameters: CompletionParameters,
        project: Project,
        result: CompletionResultSet,
        prevLeaf: PsiElement,
    ): Boolean {
        val beforeDot = CrystalCompletionContributor.getPreviousNonWhitespaceLeaf(prevLeaf) ?: return false
        CrystalDotCompletion.completeAfterDot(parameters, project, result, beforeDot)
        return true
    }

    /** `CONSTANT::<caret>`: nested types of an uppercase receiver only. */
    private fun completeNestedTypeTail(
        project: Project,
        result: CompletionResultSet,
        prevLeaf: PsiElement,
    ): Boolean {
        val beforeDoubleColon = CrystalCompletionContributor.getPreviousNonWhitespaceLeaf(prevLeaf) ?: return false
        val beforeText = beforeDoubleColon.text
        if (beforeText.isEmpty() || !beforeText[0].isUpperCase()) return false
        for (lookup in CrystalTypeCompletionProvider.getEnclosingTypeLookups(beforeText, project)) {
            result.addElement(lookup)
        }
        return true
    }
}
