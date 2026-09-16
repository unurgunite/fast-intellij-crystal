package io.github.unurgunite.crystal.documentation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.escapeHtml
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.highlightCrystalCode
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.wrapTypeLinks
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.type.CrystalTypeInference

/**
 * Variable documentation: name extraction behind reference/expression
 * wrappers and inferred-type signatures. Split out of `CrystalDocSignatures`
 * (which exceeded the function budget).
 */
internal object CrystalDocVariables {
    /**
     * Variable signature: inferred type(s) (linked, unions as `A | B`, `Any` when
     * unknown) + muted `(Variable)`, then the variable name.
     */
    fun buildVariableSignatureHtml(
        target: PsiElement,
        name: String,
        project: Project,
    ): String {
        val sb = StringBuilder()

        // Line 1: inferred type(s) (linked) + muted "(Variable)". Unions shown as "A | B".
        val inferredTypes = CrystalTypeInference.inferTypeList(name, target, project)
        appendInferredType(sb, inferredTypes, target, project)
        sb.append(" <span style='color:gray'>(Variable)</span>")
        sb.append("\n")

        // Line 2: variable name
        sb.append(escapeHtml(name))

        return sb.toString()
    }

    /** Linked inferred type(s), or `Any` when inference yields nothing. */
    private fun appendInferredType(
        sb: StringBuilder,
        inferredTypes: List<String>,
        target: PsiElement,
        project: Project,
    ) {
        if (inferredTypes.isEmpty()) {
            sb.append(escapeHtml("Any"))
            return
        }
        val inferredType = inferredTypes.joinToString(" | ")
        val highlighted = highlightCrystalCode(inferredType, target) ?: escapeHtml(inferredType)
        sb.append(wrapTypeLinks(highlighted, project))
    }

    /** Variable name behind reference/expression wrappers, or null when indeterminate. */
    fun variableNameOf(target: PsiElement): String? =
        when {
            target is CrystalVariableReference -> {
                target.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: target.text
            }

            target is CrystalExpression -> {
                variableNameOfExpression(target)
            }

            else -> {
                target.text
            }
        }

    /** Name of a variable reference inside an expression wrapper, else the text. */
    private fun variableNameOfExpression(target: CrystalExpression): String {
        val varRef = target.variableReferenceList.firstOrNull()
        if (varRef != null) {
            return varRef.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: varRef.text
        }
        return target.text
    }
}
