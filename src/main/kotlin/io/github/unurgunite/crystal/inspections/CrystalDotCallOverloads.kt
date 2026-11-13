package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.type.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver
import io.github.unurgunite.crystal.type.CrystalMethodLookup
import io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference

/**
 * Candidate overloads for a DOT-call, resolved per receiver.
 *
 * `.new` maps to the receiver class's `initialize` overloads through the
 * namespace-aware hierarchy chain: a name-only index lookup pulled unrelated
 * `initialize` definitions (e.g. `Exception`'s) into the candidate set, which
 * made `Runner.new(rules, ...)` report against `Config`. Other methods are
 * filtered by enclosing class name for CONSTANT receivers, which keeps
 * `ENV.fetch(...)` off `Hash#fetch`.
 *
 * File-level so `CrystalTypeCheckInspection` stays inside detekt's
 * TooManyFunctions budget.
 */
internal object CrystalDotCallOverloads {
    fun resolve(
        info: DotCallInfo,
        argsElement: PsiElement,
    ): List<CrystalMethodDefinition> =
        when {
            info.methodName == "new" && info.receiverName.isNotEmpty() -> {
                CrystalMethodLookup.getInitializeMethods(
                    info.receiverName,
                    argsElement.project,
                    argsElement.containingFile,
                    argsElement,
                )
            }

            info.receiverName.isNotEmpty() && info.receiverName[0].isUpperCase() -> {
                // The receiver may be qualified (`RvmCli::Tools`) while the
                // method's owner is recorded under its short name (`Tools`).
                CrystalOverloadEvaluator
                    .findCandidateMethods(info.methodName, argsElement.project)
                    .filter { ownedByReceiver(it, info.receiverName) }
            }

            else -> {
                // Lowercase receiver: `receiverName` is its TEXT (`row`, `db`),
                // not a class. Resolve the receiver's inferred class and filter
                // by it — an unfiltered name-only lookup pulled every method of
                // that name from the index, so `row[0]`/`proc.call(...)` were
                // reported against unrelated classes (a whole false-positive
                // family in the 1.2.2 export).
                val candidates = CrystalOverloadEvaluator.findCandidateMethods(info.methodName, argsElement.project)
                val receiverClasses = inferredReceiverClasses(info.receiverElement)
                if (receiverClasses.isEmpty()) {
                    candidates
                } else {
                    candidates.filter { method -> receiverClasses.any { ownedByReceiver(method, it) } }
                }
            }
        }

    /**
     * Classes a lowercase receiver may have. A union (`A | B`) yields every
     * member, so a method defined on the second member is still a candidate.
     */
    private fun inferredReceiverClasses(receiver: PsiElement?): List<String> {
        if (receiver == null) return emptyList()
        val typeName = CrystalExpressionTypeResolver.resolveType(receiver)?.typeName ?: return emptyList()
        return CrystalMethodReturnTypeInference
            .splitTypeNames(typeName)
            .mapNotNull { member ->
                member
                    .trim()
                    .removeSuffix("?")
                    .substringAfterLast("::")
                    .takeIf { it.isNotEmpty() && it.first().isUpperCase() }
            }
    }

    /** True when [method]'s enclosing type is [receiverName] or its tail (`Tools` for `RvmCli::Tools`). */
    private fun ownedByReceiver(
        method: CrystalMethodDefinition,
        receiverName: String,
    ): Boolean {
        val owner = CrystalMethodLookup.getEnclosingClassName(method) ?: return false
        return owner == receiverName || receiverName.substringAfterLast("::") == owner
    }
}
