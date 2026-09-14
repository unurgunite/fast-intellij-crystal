package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalNamespaceAccess
import io.github.unurgunite.crystal.psi.CrystalPsiUtils

/**
 * DOT-call completion: `CONSTANT.` (static methods + `new` fallbacks) and
 * `identifier.` (instance methods of the inferred receiver type).
 */
internal object CrystalDotCompletion {
    /**
     * DOT-call completion: `CONSTANT.` (static methods + `new` fallbacks) or
     * `identifier.` (inferred-type instance methods).
     */
    fun completeAfterDot(
        parameters: CompletionParameters,
        project: Project,
        result: CompletionResultSet,
        beforeDot: PsiElement,
    ) {
        val beforeDotText = beforeDot.text

        // Case 1: CONSTANT. (Class.method)
        if (beforeDotText.isNotEmpty() && beforeDotText[0].isUpperCase()) {
            completeStaticMethods(parameters, project, result, beforeDot, beforeDotText)
            return
        }

        // Case 2: identifier. (variable.method or @instance_var.method)
        completeInstanceMethods(project, result, beforeDot, beforeDotText)
    }

    /** Instance methods of the inferred type of a variable/`@ivar` receiver. */
    private fun completeInstanceMethods(
        project: Project,
        result: CompletionResultSet,
        beforeDot: PsiElement,
        beforeDotText: String,
    ) {
        val cleanedText = beforeDotText.removePrefix("@")
        if (cleanedText.isEmpty() || !cleanedText[0].isLowerCase()) return
        val inferredTypes = CrystalTypeInference.inferTypeList(cleanedText, beforeDot, project)
        for (inferredType in inferredTypes) {
            for (lookup in CrystalCompletionHelper.getMethodsAsLookups(inferredType, project)) {
                result.addElement(lookup)
            }
        }
    }

    private fun completeStaticMethods(
        parameters: CompletionParameters,
        project: Project,
        result: CompletionResultSet,
        receiver: PsiElement,
        beforeDotText: String,
    ) {
        // Check if this CONSTANT is part of a namespace_access (e.g. Foo::Sub.space)
        val nsAccess = PsiTreeUtil.getParentOfType(receiver, CrystalNamespaceAccess::class.java, false)
        val staticMethods = staticMethodsFor(beforeDotText, project, nsAccess)
        for (method in staticMethods) {
            result.addElement(CrystalLookupBuilders.buildMethodLookup(method))
        }
        if (staticMethods.any { it.name == "new" }) return
        addNewFallbacks(parameters, project, result, beforeDotText, nsAccess)
    }

    /**
     * `new` fallbacks when no constructor is indexed: the `record` macro's
     * parameters first, then a generic `new` for instantiable classes.
     */
    private fun addNewFallbacks(
        parameters: CompletionParameters,
        project: Project,
        result: CompletionResultSet,
        beforeDotText: String,
        nsAccess: CrystalNamespaceAccess?,
    ) {
        // Fallback 1: record macro — offer "new" with record parameters
        val recordDef = CrystalRecordCompletion.findRecordDefinition(beforeDotText, parameters.originalFile)
        if (recordDef != null) {
            result.addElement(CrystalRecordCompletion.buildRecordNewLookup(recordDef, beforeDotText))
        } else if (nsAccess == null && CrystalCompletionHelper.canInstantiate(beforeDotText, project)) {
            // Fallback 2: only for simple constants, not namespace paths
            result.addElement(CrystalLookupBuilders.buildNewLookup(beforeDotText, project, parameters.originalFile))
        }
    }

    /**
     * Static methods for a CONSTANT receiver: namespace-qualified when the receiver
     * is part of `Foo::Sub.space`, plain class lookup otherwise.
     */
    private fun staticMethodsFor(
        beforeDotText: String,
        project: Project,
        nsAccess: CrystalNamespaceAccess?,
    ): List<CrystalMethodDefinition> {
        if (nsAccess == null) {
            // Simple constant receiver (e.g. Apfel.tanzen)
            return CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
        }
        // Namespace receiver: build full path, filter by qualified enclosing class
        val qualifiedName = CrystalPsiUtils.buildNamespacePath(nsAccess)
        val allMethods = CrystalCompletionHelper.getStaticMethods(beforeDotText, project)
        return allMethods.filter { method ->
            val enclosing = CrystalPsiUtils.getEnclosingType(method)
            enclosing != null && CrystalPsiUtils.buildQualifiedName(enclosing) == qualifiedName
        }
    }
}
