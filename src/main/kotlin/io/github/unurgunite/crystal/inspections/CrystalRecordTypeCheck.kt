package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.completion.CrystalRecordCompletion
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.ArgumentInfo
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition

/**
 * Type validation against `record` macro parameters (`record Config, ...`).
 * Record constructors check their own params and return early, before the
 * regular overload lookup.
 */
internal object CrystalRecordTypeCheck {
    data class RecordParamInfo(
        val name: String,
        val typeText: String?,
        val hasDefault: Boolean,
    )

    /**
     * Record-constructor fast path: for `Config.new(...)` with a `record Config, ...`
     * in the current file, checks record params directly and returns true (handled).
     */
    fun checkRecordConstructor(
        callExpr: PsiElement,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder,
    ): Boolean {
        val methodName = CrystalCallArguments.extractMethodName(callExpr)
        if (methodName != "new") return false
        val className = CrystalCallArguments.findClassNameBeforeNew(callExpr) ?: return false
        val recordParams = extractRecordParamInfo(className, callExpr) ?: return false
        checkRecordTypeArgs(recordParams, arguments, holder)
        return true
    }

    /**
     * Constructor overloads for a DOT-call with no index hits: record params are
     * reported directly (empty list), otherwise the `initialize` method if found.
     */
    fun resolveConstructorOverloads(
        className: String,
        argsElement: PsiElement,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder,
    ): List<CrystalMethodDefinition> {
        val recordParams = extractRecordParamInfo(className, argsElement)
        if (recordParams != null) {
            checkRecordTypeArgs(recordParams, arguments, holder)
            return emptyList()
        }
        val initMethod =
            CrystalCompletionHelper.getInitializeMethod(className, argsElement.project, argsElement.containingFile)
        return if (initMethod != null) listOf(initMethod) else emptyList()
    }

    /**
     * Extracts parameter info from a `record` definition for type checking.
     * Returns list of (name, typeText, hasDefault) or null if not a record.
     */
    private fun extractRecordParamInfo(
        className: String,
        contextElement: PsiElement,
    ): List<RecordParamInfo>? {
        val file = contextElement.containingFile ?: return null
        val recordDef = CrystalRecordCompletion.findRecordDefinition(className, file) ?: return null
        return CrystalRecordCompletion.extractRecordFields(recordDef).map {
            RecordParamInfo(it.name, it.typeText, it.defaultText != null)
        }
    }

    /**
     * Type-checks arguments against record parameters.
     */
    private fun checkRecordTypeArgs(
        recordParams: List<RecordParamInfo>,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder,
    ) {
        for ((argIndex, argInfo) in arguments.withIndex()) {
            checkRecordTypeArg(recordParams, argInfo, argIndex, holder)
        }
    }

    /** One argument against its record param (by name or position). Skips untyped params. */
    private fun checkRecordTypeArg(
        recordParams: List<RecordParamInfo>,
        argInfo: ArgumentInfo,
        argIndex: Int,
        holder: ProblemsHolder,
    ) {
        val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression) ?: return

        // Find matching record param by name (named) or position (positional)
        val recordParam =
            if (argInfo.name != null) {
                recordParams.find { it.name == argInfo.name }
            } else {
                recordParams.getOrNull(argIndex)
            }

        if (recordParam == null) return
        val paramType = recordParam.typeText ?: return

        if (!CrystalTypeCompatibility.isCompatible(
                resolvedType.typeName,
                paramType,
                resolvedType.isUnsuffixedNumericLiteral,
            )
        ) {
            val highlightElement = CrystalCallArguments.highlightTarget(argInfo.expression)
            holder.registerProblem(
                highlightElement,
                "Type mismatch: expected '$paramType', got '${resolvedType.typeName}'",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }
}
