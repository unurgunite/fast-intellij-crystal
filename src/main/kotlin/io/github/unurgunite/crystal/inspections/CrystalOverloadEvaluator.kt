package io.github.unurgunite.crystal.inspections

import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.extractParameterName
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex
import io.github.unurgunite.crystal.type.CrystalCallArguments.ArgumentInfo

/**
 * Overload matching for arity checks: evaluates each candidate signature against
 * the effective (splat-expanded) argument list and picks the closest failure
 * for error reporting. Shared by regular methods and `record` constructors.
 */
internal object CrystalOverloadEvaluator {
    data class OverloadMatch(
        val isValid: Boolean,
        val missingParams: List<String> = emptyList(),
        val excessStartIndex: Int = -1,
        val maxArgs: Int = 0,
        val unknownNamedArgs: Set<String> = emptySet(),
    ) {
        fun isBetterThan(other: OverloadMatch): Boolean {
            // Prefer match with fewer missing params
            return missingParams.size < other.missingParams.size
        }
    }

    data class ParamInfo(
        val name: String,
        val hasDefault: Boolean,
    )

    /** Non-regular parameter kinds encountered while classifying a parameter list. */
    enum class ParamMarker {
        SPLAT,
        DOUBLE_SPLAT,
    }

    /**
     * Best (closest) non-matching overload, or null when some overload accepts the call.
     */
    fun findBestOverloadMatch(
        methods: List<CrystalMethodDefinition>,
        effectiveArgCount: Int,
        effectivePositionalCount: Int,
        namedArgNames: Set<String>,
    ): OverloadMatch? {
        var bestMatch: OverloadMatch? = null

        for (method in methods) {
            val params = method.parameterList?.parameterList ?: emptyList()
            val match = evaluateOverload(params, effectiveArgCount, effectivePositionalCount, namedArgNames)

            if (match.isValid) return null // At least one overload accepts this call

            // Track best (closest) match for error reporting
            if (bestMatch == null || match.isBetterThan(bestMatch)) {
                bestMatch = match
            }
        }
        return bestMatch
    }

    /**
     * Method overloads named [methodName] from the StubIndex.
     */
    fun findCandidateMethods(
        methodName: String,
        project: com.intellij.openapi.project.Project,
    ): List<CrystalMethodDefinition> {
        val scope =
            com.intellij.psi.search.GlobalSearchScope
                .allScope(project)
        return com.intellij.psi.stubs.StubIndex
            .getElements(
                CrystalMethodIndex.KEY,
                methodName,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            ).toList()
    }

    private fun evaluateOverload(
        params: List<CrystalParameter>,
        argCount: Int,
        positionalCount: Int,
        namedArgNames: Set<String>,
    ): OverloadMatch {
        val regularParams = mutableListOf<ParamInfo>()
        var hasSplat = false
        var hasDoubleSplat = false

        for (param in params) {
            collectRegularParam(param, regularParams)?.let {
                hasSplat = hasSplat || it == ParamMarker.SPLAT
                hasDoubleSplat = hasDoubleSplat || it == ParamMarker.DOUBLE_SPLAT
            }
        }

        val paramNames = regularParams.map { it.name }.toSet()

        // Check unknown named args (only if no double-splat)
        checkUnknownNamedArgs(namedArgNames, paramNames, hasDoubleSplat)?.let { return it }

        // Check: which required params are satisfied?
        val requiredParams = regularParams.filter { !it.hasDefault }
        checkMissingParams(requiredParams, namedArgNames, positionalCount)?.let { return it }

        // Check too many args (only if no splat)
        return checkExcessArgs(
            regularCount = regularParams.size,
            paramNames = paramNames,
            namedArgNames = namedArgNames,
            positionalCount = positionalCount,
            argCount = argCount,
            hasSplat = hasSplat,
        ) ?: OverloadMatch(isValid = true)
    }

    /** Unknown named arguments, unless a double-splat absorbs them. Null when clean. */
    private fun checkUnknownNamedArgs(
        namedArgNames: Set<String>,
        paramNames: Set<String>,
        hasDoubleSplat: Boolean,
    ): OverloadMatch? {
        if (hasDoubleSplat) return null
        val unknown = namedArgNames - paramNames
        if (unknown.isNotEmpty()) {
            return OverloadMatch(isValid = false, unknownNamedArgs = unknown)
        }
        return null
    }

    /** Required params not satisfied by name or position. Null when all satisfied. */
    private fun checkMissingParams(
        requiredParams: List<ParamInfo>,
        namedArgNames: Set<String>,
        positionalCount: Int,
    ): OverloadMatch? {
        val satisfiedByName = namedArgNames.intersect(requiredParams.map { it.name }.toSet())
        val requiredNotSatisfiedByName = requiredParams.filter { it.name !in satisfiedByName }

        // Positional args fill remaining params in order
        val positionallyRequired = requiredNotSatisfiedByName.size
        if (positionalCount < positionallyRequired) {
            val missing = requiredNotSatisfiedByName.drop(positionalCount).map { it.name }
            return OverloadMatch(isValid = false, missingParams = missing)
        }
        return null
    }

    /** Excess positional args beyond the signature, unless a splat absorbs them. */
    private fun checkExcessArgs(
        regularCount: Int,
        paramNames: Set<String>,
        namedArgNames: Set<String>,
        positionalCount: Int,
        argCount: Int,
        hasSplat: Boolean,
    ): OverloadMatch? {
        if (hasSplat) return null
        val maxPositional = regularCount - namedArgNames.intersect(paramNames).size
        if (positionalCount > maxPositional) {
            return OverloadMatch(
                isValid = false,
                excessStartIndex = argCount - (positionalCount - maxPositional),
                maxArgs = regularCount,
            )
        }
        return null
    }

    /**
     * Classifies one parameter: splat markers, skipped block-passes, or a regular
     * parameter appended to [regularParams]. Returns the marker kind, if any.
     */
    private fun collectRegularParam(
        param: CrystalParameter,
        regularParams: MutableList<ParamInfo>,
    ): ParamMarker? {
        val prefix =
            param.node
                .getChildren(null)
                .firstOrNull()
                ?.elementType
        when (prefix) {
            // &block — skip
            CrystalTypes.AMPERSAND -> return null

            CrystalTypes.STAR -> return ParamMarker.SPLAT

            CrystalTypes.DOUBLE_STAR -> return ParamMarker.DOUBLE_SPLAT
        }
        val name = extractParameterName(param) ?: return null
        regularParams.add(ParamInfo(name, param.expression != null))
        return null
    }

    /** True when any splat/double-splat argument cannot be resolved to a literal. */
    fun hasUnresolvedSplat(arguments: List<ArgumentInfo>): Boolean {
        val hasUnresolvedSplat = arguments.any { it.isSplat && it.resolvedSplatCount == null }
        val hasUnresolvedDoubleSplat = arguments.any { it.isDoubleSplat && it.resolvedDoubleSplatKeys == null }
        return hasUnresolvedSplat || hasUnresolvedDoubleSplat
    }

    /** Positional argument count with resolved splats expanded (block-passes excluded). */
    fun effectivePositionalCount(arguments: List<ArgumentInfo>): Int =
        arguments.sumOf { arg ->
            when {
                arg.isBlockPass -> 0

                // block-pass (&block) is not a positional argument
                arg.isSplat -> arg.resolvedSplatCount ?: 1

                arg.isDoubleSplat -> 0

                // double-splat contributes named args, not positional
                arg.name != null -> 0

                // named args aren't positional
                else -> 1
            }
        }

    /** Named argument names from explicit labels plus resolved double-splat keys. */
    fun collectNamedArgNames(arguments: List<ArgumentInfo>): Set<String> {
        val namedArgNames = mutableSetOf<String>()
        for (arg in arguments) {
            if (arg.name != null) namedArgNames.add(arg.name)
            if (arg.isDoubleSplat && arg.resolvedDoubleSplatKeys != null) {
                namedArgNames.addAll(arg.resolvedDoubleSplatKeys)
            }
        }
        return namedArgNames
    }
}
