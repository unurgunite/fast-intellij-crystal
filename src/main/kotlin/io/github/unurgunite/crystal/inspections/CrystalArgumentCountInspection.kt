package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.ArgumentInfo
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.inspections.CrystalOverloadEvaluator.OverloadMatch
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Inspection that validates argument count against method parameter definitions.
 * Reports warnings when:
 * - Required (non-default) parameters are missing
 * - Too many arguments are provided (excess args highlighted individually)
 *
 * Handles:
 * - Parenthesized calls: foo(arg1, arg2)
 * - Bare calls: foo arg1, arg2
 * - DOT-calls: Foo.bar(arg1)
 * - Named arguments: foo(name: value)
 * - Splat (*args) and double-splat (**kwargs) parameters
 * - Block (&block) parameters (not counted)
 * - Default parameter values (make parameter optional)
 * - Multiple overloads (only reports if NO overload matches)
 *
 * Call-shape extraction lives in [CrystalCallArguments], overload matching in
 * [CrystalOverloadEvaluator], `record` constructors in [CrystalRecordArgumentCheck].
 */
class CrystalArgumentCountInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalMethodCallExpression -> {
                        checkCall(element, holder)
                    }

                    is CrystalBareMethodCallExpression -> {
                        checkCall(element, holder)
                    }

                    is CrystalBareCommandExpression -> {
                        checkCall(element, holder)
                    }

                    is CrystalCallArgs, is CrystalBareArgumentList -> {
                        val dotCallInfo = CrystalDotCallScan.detectDotCall(element)
                        if (dotCallInfo != null) {
                            checkDotCall(element, dotCallInfo, holder)
                        }
                    }
                }
            }
        }

    private fun checkCall(
        callExpr: PsiElement,
        holder: ProblemsHolder,
    ) {
        // Skip if the method name resolves to a local variable or parameter.
        // This prevents false positives when a bare call like "count + 87" is parsed
        // as method_call_expression(count, +87) due to binary_op_lookahead not catching
        // operators followed by literals. The parameter shadows any same-named method.
        val resolvedRef = callExpr.reference?.resolve()
        if (resolvedRef != null && resolvedRef !is CrystalMethodDefinition) return

        val methodName = CrystalCallArguments.extractMethodName(callExpr) ?: return
        val arguments = CrystalCallArguments.extractArguments(callExpr)
        val methodNameElement = findMethodNameElement(callExpr) ?: return

        if (methodName == "new") {
            val className = CrystalCallArguments.findClassNameBeforeNew(callExpr)
            if (checkNewConstructorCall(className, arguments, methodNameElement, callExpr, holder)) return
        }

        val methods = CrystalOverloadEvaluator.findCandidateMethods(methodName, callExpr.project)
        if (methods.isEmpty()) return

        checkArgumentCount(methods, arguments, methodNameElement, holder)
    }

    private fun checkDotCall(
        argsElement: PsiElement,
        info: DotCallInfo,
        holder: ProblemsHolder,
    ) {
        // Skip DOT-calls on local variables/parameters — we can't reliably determine
        // the receiver's type, so we only check calls on Constants (class methods).
        val firstChar = info.receiverName.firstOrNull() ?: return
        if (!firstChar.isUpperCase()) return

        val arguments = CrystalCallArguments.extractArgumentsFromArgsElement(argsElement)

        var methods = CrystalOverloadEvaluator.findCandidateMethods(info.methodName, argsElement.project)

        // Filter to methods defined inside a class/module matching the receiver name.
        // This prevents false positives like ::Bytes.new(...) matching unrelated new overloads.
        // Only filter when methods actually have an enclosing type; top-level defs stay.
        methods =
            methods.filter { method ->
                val enclosing = CrystalCompletionHelper.getEnclosingClassName(method)
                enclosing == null || enclosing == info.receiverName
            }

        if (info.methodName == "new") {
            if (checkNewConstructorCall(info.receiverName, arguments, info.methodNameElement, argsElement, holder)) return
        }

        if (methods.isEmpty()) return

        checkArgumentCount(methods, arguments, info.methodNameElement, holder)
    }

    /**
     * Record/`initialize` constructor check. Returns true when handled
     * (record params or `initialize` found), false to fall through to regular methods.
     */
    private fun checkNewConstructorCall(
        className: String?,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        contextElement: PsiElement,
        holder: ProblemsHolder,
    ): Boolean {
        if (className == null) return false
        // Check record definition first — if `record Config, ...` exists in the
        // current file, its parameters take priority over any `class Config`
        // defined elsewhere (which might have a different `initialize`).
        val recordParams = CrystalRecordArgumentCheck.findRecordParameters(className, contextElement)
        if (recordParams != null) {
            CrystalRecordArgumentCheck.checkRecordArguments(recordParams, arguments, methodNameElement, holder)
            return true
        }
        // No record found — try regular class initialize
        val initMethod =
            CrystalCompletionHelper.getInitializeMethod(className, contextElement.project, contextElement.containingFile)
        if (initMethod != null) {
            checkArgumentCount(listOf(initMethod), arguments, methodNameElement, holder)
            return true
        }
        return false
    }

    private fun checkArgumentCount(
        methods: List<CrystalMethodDefinition>,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        // If any argument has an unresolvable splat/double-splat, skip the check entirely
        if (CrystalOverloadEvaluator.hasUnresolvedSplat(arguments)) return

        // Expand resolved splats into effective argument counts
        val effectivePositionalCount = CrystalOverloadEvaluator.effectivePositionalCount(arguments)

        // Collect named arg names (including resolved double-splat keys)
        val namedArgNames = CrystalOverloadEvaluator.collectNamedArgNames(arguments)

        val effectiveArgCount = effectivePositionalCount + namedArgNames.size

        // Check each overload
        val bestMatch =
            CrystalOverloadEvaluator.findBestOverloadMatch(methods, effectiveArgCount, effectivePositionalCount, namedArgNames)
        if (bestMatch == null) return // At least one overload accepts this call

        // No overload matched — report problem
        reportOverloadMismatch(bestMatch, arguments, effectiveArgCount, methodNameElement, holder)
    }

    /**
     * Reports the best-match overload failure: missing params, excess args or
     * unknown named args.
     */
    private fun reportOverloadMismatch(
        match: OverloadMatch,
        arguments: List<ArgumentInfo>,
        effectiveArgCount: Int,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        when {
            match.missingParams.isNotEmpty() -> {
                reportMissingParams(match, methodNameElement, holder)
            }

            match.excessStartIndex >= 0 -> {
                reportExcessArgs(match, arguments, effectiveArgCount, methodNameElement, holder)
            }

            match.unknownNamedArgs.isNotEmpty() -> {
                reportUnknownNamedArgs(match, arguments, methodNameElement, holder)
            }
        }
    }

    private fun reportMissingParams(
        match: OverloadMatch,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        val missing = match.missingParams.joinToString(", ") { "'$it'" }
        holder.registerProblem(
            methodNameElement,
            "Missing required argument(s): $missing",
            ProblemHighlightType.GENERIC_ERROR,
        )
    }

    private fun reportExcessArgs(
        match: OverloadMatch,
        arguments: List<ArgumentInfo>,
        effectiveArgCount: Int,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        if (arguments.any { it.isSplat || it.isDoubleSplat }) {
            holder.registerProblem(
                methodNameElement,
                "Too many arguments: expected at most ${match.maxArgs}, got $effectiveArgCount",
                ProblemHighlightType.GENERIC_ERROR,
            )
            return
        }
        for (i in match.excessStartIndex until arguments.size) {
            val argExpr = arguments[i].element
            val target = CrystalCallArguments.highlightTarget(argExpr)
            holder.registerProblem(
                target,
                "Too many arguments: expected at most ${match.maxArgs}, got $effectiveArgCount",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }

    private fun reportUnknownNamedArgs(
        match: OverloadMatch,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        val unknownNames = match.unknownNamedArgs.joinToString(", ") { "'$it'" }
        var highlighted = false
        for (arg in arguments) {
            if (arg.name != null && arg.name in match.unknownNamedArgs) {
                holder.registerProblem(
                    arg.element,
                    "Unknown named argument '${arg.name}'",
                    ProblemHighlightType.GENERIC_ERROR,
                )
                highlighted = true
            }
        }
        if (!highlighted) {
            // Unknown keys came from resolved double-splat — report on method name
            holder.registerProblem(
                methodNameElement,
                "Unknown named argument(s): $unknownNames",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }

    private fun findMethodNameElement(callExpr: PsiElement): PsiElement? {
        var child = callExpr.firstChild
        var lastNameElement: PsiElement? = null
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameElement = child
            }
            child = child.nextSibling
        }
        return lastNameElement
    }
}
