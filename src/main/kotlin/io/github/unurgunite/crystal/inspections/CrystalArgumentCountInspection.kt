package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.unurgunite.crystal.inspections.CrystalOverloadEvaluator.OverloadMatch
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareCommandSpaceFirst
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.type.CrystalCallArguments
import io.github.unurgunite.crystal.type.CrystalCallArguments.ArgumentInfo
import io.github.unurgunite.crystal.type.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.type.CrystalDotCallScan
import io.github.unurgunite.crystal.type.CrystalMethodLookup

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

                    is CrystalBareCommandSpaceFirst -> {
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
        val methodName = CrystalCallArguments.extractMethodName(callExpr) ?: return
        if (isSkippedBareCall(callExpr, methodName)) return
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

    /**
     * True when a bare call needs no checks: the name resolves to a local
     * variable/parameter (shadows any method — binary-op ambiguity like
     * `count + 87` parsed as a call), it is a spec matcher (accepts
     * anything), or a definition macro (`define`, `delegate` — declare
     * methods, not calls). File-level shape keeps checkCall under ReturnCount.
     */
    private fun isSkippedBareCall(
        callExpr: PsiElement,
        methodName: String,
    ): Boolean {
        // Skip if the method name resolves to a local variable or parameter.
        // This prevents false positives when a bare call like "count + 87" is parsed
        // as method_call_expression(count, +87) due to binary_op_lookahead not catching
        // operators followed by literals. The parameter shadows any same-named method.
        val resolvedRef = callExpr.reference?.resolve()
        if (resolvedRef != null && resolvedRef !is CrystalMethodDefinition) return true
        val scope = io.github.unurgunite.crystal.type.CrystalLocalScope
        // Spec matchers accept anything — never arity/type-check them.
        if (scope.isSpecMatcherCall(methodName)) return true
        return scope.isDefinitionMacroCall(methodName)
    }

    private fun checkDotCall(
        argsElement: PsiElement,
        info: DotCallInfo,
        holder: ProblemsHolder,
    ) {
        // Spec matchers accept anything — never arity/type-check them.
        if (io.github.unurgunite.crystal.type.CrystalLocalScope
                .isSpecMatcherCall(info.methodName)
        ) {
            return
        }
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
                val enclosing = CrystalMethodLookup.getEnclosingClassName(method)
                enclosing == null || enclosing == info.receiverName
            }

        if (info.methodName == "new") {
            if (checkNewConstructorCall(info.receiverName, arguments, info.methodNameElement, argsElement, holder)) return
        }

        if (methods.isEmpty()) return

        checkArgumentCount(methods, arguments, info.methodNameElement, holder)
    }

    /**
     * Record/`initialize`/`param` constructor check. Returns true when handled
     * (record params, `initialize` or macro `param` declarations found),
     * false to fall through to regular methods.
     *
     * Priority: record > literal initialize > macro `param` (file-level
     * helpers keep this class under detekt's TooManyFunctions budget).
     */
    private fun checkNewConstructorCall(
        className: String?,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        contextElement: PsiElement,
        holder: ProblemsHolder,
    ): Boolean {
        if (className == null) return false
        if (CrystalNewConstructorCheck.checkRecord(className, arguments, methodNameElement, contextElement, holder)) return true
        if (
            CrystalNewConstructorCheck.checkInitialize(
                className,
                arguments,
                methodNameElement,
                contextElement,
                holder,
                ::checkArgumentCount,
            )
        ) {
            return true
        }
        return CrystalNewConstructorCheck.checkParam(className, arguments, methodNameElement, contextElement, holder)
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
        // Block bodies are zero-count markers, never excess highlights: skip them
        // when mapping the excess window back onto argument elements.
        val valueArgs = arguments.filter { !it.isBlock }
        for (i in match.excessStartIndex until valueArgs.size) {
            val argExpr = valueArgs[i].element
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

/**
 * Constructor dispatch for `.new`: record > literal `initialize` > macro
 * `param`. File-level to keep the inspection class under detekt's
 * TooManyFunctions budget.
 */
private object CrystalNewConstructorCheck {
    /** Record-definition fast path (highest priority). */
    fun checkRecord(
        className: String,
        arguments: List<CrystalCallArguments.ArgumentInfo>,
        methodNameElement: PsiElement,
        contextElement: PsiElement,
        holder: ProblemsHolder,
    ): Boolean {
        // Check record definition first — if `record Config, ...` exists in the
        // current file, its parameters take priority over any `class Config`
        // defined elsewhere (which might have a different `initialize`).
        val recordParams = CrystalRecordArgumentCheck.findRecordParameters(className, contextElement) ?: return false
        CrystalRecordArgumentCheck.checkRecordArguments(recordParams, arguments, methodNameElement, holder)
        return true
    }

    /**
     * Literal `def initialize` path — unless it is an empty fallback
     * (`def initialize; end`) inherited from a macro base (Mosquito's
     * `QueuedJob` ships one for the zero-params case) while the class
     * itself declares macro `param`s. The macro-generated constructor
     * (`initialize(@attempt_id, ...)`) is invisible in source, so arity
     * against the empty fallback reports every named arg as unknown —
     * yield to the `param` path instead (overload semantics: the empty
     * fallback stays valid for zero-arg calls, `param` covers the rest).
     */
    fun checkInitialize(
        className: String,
        arguments: List<ArgumentInfo>,
        methodNameElement: PsiElement,
        contextElement: PsiElement,
        holder: ProblemsHolder,
        checkArgumentCount: (
            List<CrystalMethodDefinition>,
            List<CrystalCallArguments.ArgumentInfo>,
            PsiElement,
            ProblemsHolder,
        ) -> Unit,
    ): Boolean {
        val initMethod =
            CrystalMethodLookup.getInitializeMethod(className, contextElement.project, contextElement.containingFile)
                ?: return false
        if (isEmptyFallback(initMethod) && hasParamDeclarations(className, contextElement)) return false
        checkArgumentCount(listOf(initMethod), arguments, methodNameElement, holder)
        return true
    }

    /** True when [method] takes no parameters at all (empty fallback shape). */
    private fun isEmptyFallback(method: CrystalMethodDefinition): Boolean = method.parameterList?.parameterList.isNullOrEmpty()

    /** True when [className] declares macro `param`s (project-wide lookup). */
    private fun hasParamDeclarations(
        className: String,
        contextElement: PsiElement,
    ): Boolean {
        val file = contextElement.containingFile ?: return false
        return io.github.unurgunite.crystal.type.CrystalParamLookup
            .paramsForProject(className, contextElement.project, file)
            .isNotEmpty()
    }

    /**
     * Macro `param` declarations (Mosquito-style `param name : Type`) generate
     * the constructor at compile time. Lowest priority: literal initialize
     * and record win when present.
     */
    fun checkParam(
        className: String,
        arguments: List<CrystalCallArguments.ArgumentInfo>,
        methodNameElement: PsiElement,
        contextElement: PsiElement,
        holder: ProblemsHolder,
    ): Boolean {
        val file = contextElement.containingFile ?: return false
        val paramEntries =
            io.github.unurgunite.crystal.type.CrystalParamLookup
                .paramsForProject(className, contextElement.project, file)
        if (paramEntries.isEmpty()) return false
        checkParamArity(paramEntries, arguments, methodNameElement, holder)
        return true
    }

    /**
     * Arity check against macro `param` declarations: same overload shape as
     * record params (no splat), unknown named args reported per argument.
     */
    private fun checkParamArity(
        paramEntries: List<io.github.unurgunite.crystal.type.CrystalParamLookup.ParamEntry>,
        arguments: List<CrystalCallArguments.ArgumentInfo>,
        methodNameElement: PsiElement,
        holder: ProblemsHolder,
    ) {
        val params =
            paramEntries.map {
                CrystalOverloadEvaluator.ParamInfo(it.name, it.hasDefault)
            }
        CrystalRecordArgumentCheck.checkRecordArguments(params, arguments, methodNameElement, holder)
    }
}
