package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.ArgumentInfo
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalStatement
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Inspection that validates argument types against parameter type annotations
 * in Crystal method calls. Reports type mismatches as errors.
 *
 * Handles:
 * - Parenthesized calls: foo(arg1, arg2)
 * - Bare calls: foo arg1, arg2
 * - Named arguments: foo(name: value)
 * - Multiple overloads (only reports error if ALL overloads are incompatible)
 * - Numeric autocasting (Crystal's lossless widening rules)
 * - Union types and nilable types
 * - Splat parameters (skipped)
 * - Default parameter values (no error for missing args)
 *
 * Call-shape extraction lives in [CrystalCallArguments], candidate lookup in
 * [CrystalOverloadEvaluator], `record` constructors in [CrystalRecordTypeCheck].
 */
class CrystalTypeCheckInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalMethodCallExpression -> {
                        checkMethodCall(element, holder)
                    }

                    is CrystalBareMethodCallExpression -> {
                        checkMethodCall(element, holder)
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

    private fun checkMethodCall(
        callExpr: PsiElement,
        holder: ProblemsHolder,
    ) {
        val methodName = CrystalCallArguments.extractMethodName(callExpr) ?: return

        // Skip if method name is actually a local variable/parameter (binary operator ambiguity)
        // e.g. "text * times" parsed as bare call "text(*times)" but actually is binary "text * times"
        if (CrystalLocalScope.isLocalVariableOrParameter(callExpr, methodName)) return

        val arguments = CrystalCallArguments.extractArguments(callExpr)
        if (arguments.isEmpty()) return

        // Record constructors check their own params and return early.
        if (CrystalRecordTypeCheck.checkRecordConstructor(callExpr, arguments, holder)) return

        val methods = CrystalOverloadEvaluator.findCandidateMethods(methodName, callExpr.project)
        if (methods.isEmpty()) return

        // Check each argument against all overloads
        for ((argIndex, argInfo) in arguments.withIndex()) {
            val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression) ?: continue
            checkArgumentAgainstOverloads(argInfo, argIndex, methods, resolvedType, holder)
        }
    }

    /**
     * Type-checks a DOT-call (e.g. Apfel.kurz "lol") by extracting args from the args element.
     */
    private fun checkDotCall(
        argsElement: PsiElement,
        info: DotCallInfo,
        holder: ProblemsHolder,
    ) {
        val arguments = CrystalCallArguments.extractArgumentsFromArgsElement(argsElement)
        if (arguments.isEmpty()) return

        val methods = resolveDotCallMethods(argsElement, info, arguments, holder)
        if (methods.isEmpty()) return

        // Reuse the same type-checking logic
        for ((argIndex, argInfo) in arguments.withIndex()) {
            val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression) ?: continue
            checkArgumentAgainstOverloads(argInfo, argIndex, methods, resolvedType, holder)
        }
    }

    /**
     * Overloads named [info.methodName] for a DOT-call: filtered to the receiver's class,
     * with record/`initialize` priority for constructor calls. Returns empty after
     * reporting directly (record fast path) or when nothing matches.
     */
    private fun resolveDotCallMethods(
        argsElement: PsiElement,
        info: DotCallInfo,
        arguments: List<ArgumentInfo>,
        holder: ProblemsHolder,
    ): List<CrystalMethodDefinition> {
        var methods = CrystalOverloadEvaluator.findCandidateMethods(info.methodName, argsElement.project)

        // Filter to methods defined inside a class/module matching the receiver name.
        // This prevents false positives like ENV.fetch(...) matching Hash#fetch.
        // Only apply for CONSTANT receivers (class/module names start with uppercase in Crystal).
        if (info.receiverName.isNotEmpty() && info.receiverName[0].isUpperCase()) {
            methods =
                methods.filter { method ->
                    CrystalCompletionHelper.getEnclosingClassName(method) == info.receiverName
                }
        }

        if (methods.isNotEmpty() || info.methodName != "new") return methods

        // Constructor with no index hits: record params or initialize.
        val className = CrystalCallArguments.findClassNameBeforeNewFromArgs(argsElement) ?: return emptyList()
        return CrystalRecordTypeCheck.resolveConstructorOverloads(className, argsElement, arguments, holder)
    }

    /**
     * Checks one argument against every overload, reporting when none accepts it.
     */
    private fun checkArgumentAgainstOverloads(
        argInfo: ArgumentInfo,
        argIndex: Int,
        methods: List<CrystalMethodDefinition>,
        resolvedType: CrystalExpressionTypeResolver.ResolvedType,
        holder: ProblemsHolder,
    ) {
        // Collect expected types from all overloads for this argument position
        val expectedTypes = mutableListOf<String>()
        val anyOverloadAccepts = methods.any { collectsExpectedType(it, argIndex, argInfo, resolvedType, expectedTypes) }

        if (!anyOverloadAccepts && expectedTypes.isNotEmpty()) {
            reportTypeMismatch(argInfo, expectedTypes, resolvedType, holder)
        }
    }

    /**
     * One overload's verdict for an argument: true when accepted, else records
     * its expected type and returns false.
     */
    private fun collectsExpectedType(
        method: CrystalMethodDefinition,
        argIndex: Int,
        argInfo: ArgumentInfo,
        resolvedType: CrystalExpressionTypeResolver.ResolvedType,
        expectedTypes: MutableList<String>,
    ): Boolean {
        val params = method.parameterList?.parameterList ?: return false
        val param = findMatchingParameter(params, argIndex, argInfo.name) ?: return false
        if (acceptsArgument(param, resolvedType)) return true
        param.typeReference?.text?.let { expectedTypes.add(it) }
        return false
    }

    /**
     * True when a parameter accepts an argument: splats accept anything, untyped
     * parameters use duck typing, typed ones go through compatibility rules.
     */
    private fun acceptsArgument(
        param: CrystalParameter,
        resolvedType: CrystalExpressionTypeResolver.ResolvedType,
    ): Boolean {
        if (isSplatParameter(param)) return true
        // No type annotation → duck typing, always compatible
        val paramType = param.typeReference?.text ?: return true
        return CrystalTypeCompatibility.isCompatible(
            resolvedType.typeName,
            paramType,
            resolvedType.isUnsuffixedNumericLiteral,
        )
    }

    private fun reportTypeMismatch(
        argInfo: ArgumentInfo,
        expectedTypes: List<String>,
        resolvedType: CrystalExpressionTypeResolver.ResolvedType,
        holder: ProblemsHolder,
    ) {
        val expectedDesc =
            if (expectedTypes.size == 1) {
                "'${expectedTypes.first()}'"
            } else {
                expectedTypes.distinct().joinToString(" or ") { "'$it'" }
            }
        // Use the innermost meaningful element for highlighting
        val highlightElement = CrystalCallArguments.highlightTarget(argInfo.expression)
        holder.registerProblem(
            highlightElement,
            "Type mismatch: expected $expectedDesc, got '${resolvedType.typeName}'",
            ProblemHighlightType.GENERIC_ERROR,
        )
    }

    private fun findMatchingParameter(
        params: List<CrystalParameter>,
        argIndex: Int,
        argName: String?,
    ): CrystalParameter? {
        // Named argument: find parameter by name
        if (argName != null) {
            for (param in params) {
                val paramName = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                if (paramName == argName) return param
            }
            return null
        }

        // Positional: find by index, skipping splat parameters for counting
        var positionalIndex = 0
        for (param in params) {
            if (isSplatParameter(param)) {
                // Splat absorbs all remaining positional args
                return param
            }
            if (positionalIndex == argIndex) return param
            positionalIndex++
        }
        return null
    }

    private fun isSplatParameter(param: CrystalParameter): Boolean {
        val children = param.node.getChildren(null)
        val firstType = children.firstOrNull()?.elementType
        return firstType == CrystalTypes.STAR || firstType == CrystalTypes.DOUBLE_STAR ||
            firstType == CrystalTypes.AMPERSAND
    }
}
