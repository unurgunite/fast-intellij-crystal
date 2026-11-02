package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.type.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.type.CrystalMethodLookup

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
                )
            }

            info.receiverName.isNotEmpty() && info.receiverName[0].isUpperCase() -> {
                CrystalOverloadEvaluator
                    .findCandidateMethods(info.methodName, argsElement.project)
                    .filter { CrystalMethodLookup.getEnclosingClassName(it) == info.receiverName }
            }

            else -> {
                CrystalOverloadEvaluator.findCandidateMethods(info.methodName, argsElement.project)
            }
        }
}
