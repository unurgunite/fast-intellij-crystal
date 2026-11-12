package io.github.unurgunite.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
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
import io.github.unurgunite.crystal.type.CrystalCallArguments
import io.github.unurgunite.crystal.type.CrystalCallArguments.ArgumentInfo
import io.github.unurgunite.crystal.type.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.type.CrystalDotCallScan
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver
import io.github.unurgunite.crystal.type.CrystalLocalScope
import io.github.unurgunite.crystal.type.CrystalMethodLookup
import io.github.unurgunite.crystal.type.CrystalTypeCompatibility

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
                if (!isAnalyzableCrystalFile(element.containingFile)) return
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
        if (isSkippedMethodCall(callExpr, methodName)) return

        val arguments = CrystalCallArguments.extractArguments(callExpr)
        if (arguments.isEmpty()) return

        // Record constructors check their own params and return early.
        if (CrystalRecordTypeCheck.checkRecordConstructor(callExpr, arguments, holder)) return

        val methods = CrystalOverloadEvaluator.findCandidateMethods(methodName, callExpr.project)
        if (methods.isEmpty()) return

        // Check each value argument against all overloads (block bodies carry
        // no value type). NOTE: argIndex counts the block marker too, so
        // overload arity math must use value-only indexing — see
        // collectsExpectedType below.
        for ((argIndex, argInfo) in arguments.withIndex()) {
            checkValueArgument(argInfo, argIndex, methods, holder)
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
        // Spec matchers accept anything — never arity/type-check them.
        if (CrystalLocalScope.isSpecMatcherCall(info.methodName)) return
        val arguments = CrystalCallArguments.extractArgumentsFromArgsElement(argsElement)
        if (arguments.isEmpty()) return

        val methods = resolveDotCallMethods(argsElement, info, arguments, holder)
        if (methods.isEmpty()) return

        // Reuse the same type-checking logic (block bodies carry no value type).
        for ((argIndex, argInfo) in arguments.withIndex()) {
            checkValueArgument(argInfo, argIndex, methods, holder)
        }
    }

    /** One value argument against every overload; block markers skipped silently. */
    private fun checkValueArgument(
        argInfo: ArgumentInfo,
        argIndex: Int,
        methods: List<CrystalMethodDefinition>,
        holder: ProblemsHolder,
    ) {
        if (argInfo.isBlock) return
        val resolvedType = CrystalExpressionTypeResolver.resolveType(argInfo.expression) ?: return
        checkArgumentAgainstOverloads(argInfo, argIndex, methods, resolvedType, holder)
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
        val methods = CrystalDotCallOverloads.resolve(info, argsElement)

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
            return params.firstOrNull { it.node.findChildByType(CrystalTypes.IDENTIFIER)?.text == argName }
        }

        // Positional: find by index. A bare `*` makes every later parameter
        // keyword-only, so the positional walk stops there.
        var positionalIndex = 0
        for (param in params) {
            if (isKeywordOnlyBoundary(param)) return null
            if (isSplatParameter(param) || positionalIndex == argIndex) return param
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

/**
 * True when a bare `*` (keyword-only marker) precedes [param] in its
 * parameter list, so [param] cannot be filled positionally.
 */
private fun isKeywordOnlyBoundary(param: CrystalParameter): Boolean {
    var sibling = param.prevSibling
    while (sibling != null) {
        if (sibling.node?.elementType == CrystalTypes.STAR) return true
        if (sibling is CrystalParameter) return false
        sibling = sibling.prevSibling
    }
    return false
}

/**
 * True when a method call needs no checks: local variable/parameter
 * shadowing (binary-op ambiguity) or a spec matcher (accepts anything).
 * File-level: keeps the inspection class under TooManyFunctions.
 */
private fun isSkippedMethodCall(
    callExpr: PsiElement,
    methodName: String,
): Boolean =
    CrystalLocalScope.isLocalVariableOrParameter(callExpr, methodName) ||
        CrystalLocalScope.isSpecMatcherCall(methodName)
