package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.completion.CrystalRecordCompletion
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.ArgumentInfo
import io.github.unurgunite.crystal.inspections.CrystalOverloadEvaluator.ParamInfo

/**
 * Arity validation against `record` macro parameters (`record Config, ...`).
 * Record params take priority over any `initialize` when a same-named record
 * exists in the current file.
 */
internal object CrystalRecordArgumentCheck {
    /**
     * Finds a `record ClassName, ...` macro call in the file and extracts its parameter list.
     * Returns the list of parameter infos (name, hasDefault) or null if no record found.
     */
    fun findRecordParameters(
        className: String,
        contextElement: PsiElement,
    ): List<ParamInfo>? {
        val file = contextElement.containingFile ?: return null
        val recordDef = CrystalRecordCompletion.findRecordDefinition(className, file) ?: return null
        return CrystalRecordCompletion.extractRecordFields(recordDef).map {
            ParamInfo(it.name, it.defaultText != null)
        }
    }

    /**
     * Validates arguments against record parameters (from `record` macro).
     * Reports missing required args or too many args.
     */
    fun checkRecordArguments(
        recordParams: List<ParamInfo>,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        // If any argument has an unresolvable splat/double-splat, skip the check entirely
        if (CrystalOverloadEvaluator.hasUnresolvedSplat(arguments)) return

        val effectivePositionalCount = CrystalOverloadEvaluator.effectivePositionalCount(arguments)
        val namedArgNames = CrystalOverloadEvaluator.collectNamedArgNames(arguments)
        val effectiveArgCount = effectivePositionalCount + namedArgNames.size

        if (checkUnknownNamedArgs(recordParams, arguments, namedArgNames, holder)) return
        if (checkMissingArgs(recordParams, namedArgNames, effectivePositionalCount, methodNameElement, holder)) return
        checkExcessArgs(recordParams, arguments, namedArgNames, effectivePositionalCount, effectiveArgCount, holder)
    }

    /** Unknown named args reported per argument. True when handled (caller stops). */
    private fun checkUnknownNamedArgs(
        recordParams: List<ParamInfo>,
        arguments: List<ArgumentInfo>,
        namedArgNames: Set<String>,
        holder: ProblemsHolder,
    ): Boolean {
        val paramNames = recordParams.map { it.name }.toSet()
        val unknown = namedArgNames - paramNames
        if (unknown.isEmpty()) return false
        for (arg in arguments) {
            if (arg.name != null && arg.name in unknown) {
                holder.registerProblem(
                    arg.element,
                    "Unknown named argument '${arg.name}'",
                    ProblemHighlightType.GENERIC_ERROR,
                )
            }
        }
        return true
    }

    /** Missing required args reported on the method name. True when handled. */
    private fun checkMissingArgs(
        recordParams: List<ParamInfo>,
        namedArgNames: Set<String>,
        effectivePositionalCount: Int,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ): Boolean {
        val requiredParams = recordParams.filter { !it.hasDefault }
        val satisfiedByName = namedArgNames.intersect(requiredParams.map { it.name }.toSet())
        val requiredNotSatisfiedByName = requiredParams.filter { it.name !in satisfiedByName }
        val positionallyRequired = requiredNotSatisfiedByName.size
        if (effectivePositionalCount >= positionallyRequired) return false
        val missing = requiredNotSatisfiedByName.drop(effectivePositionalCount).map { it.name }
        val missingStr = missing.joinToString(", ") { "'$it'" }
        holder.registerProblem(
            methodNameElement,
            "Missing required argument(s): $missingStr",
            ProblemHighlightType.GENERIC_ERROR,
        )
        return true
    }

    /** Excess args highlighted individually (record params have no splat). */
    private fun checkExcessArgs(
        recordParams: List<ParamInfo>,
        arguments: List<ArgumentInfo>,
        namedArgNames: Set<String>,
        effectivePositionalCount: Int,
        effectiveArgCount: Int,
        holder: ProblemsHolder,
    ) {
        val paramNames = recordParams.map { it.name }.toSet()
        val maxPositional = recordParams.size - namedArgNames.intersect(paramNames).size
        if (effectivePositionalCount <= maxPositional) return
        for (i in (arguments.size - (effectivePositionalCount - maxPositional)) until arguments.size) {
            val argExpr = arguments[i].element
            val target = CrystalCallArguments.highlightTarget(argExpr)
            holder.registerProblem(
                target,
                "Too many arguments: expected at most ${recordParams.size}, got $effectiveArgCount",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }
}
