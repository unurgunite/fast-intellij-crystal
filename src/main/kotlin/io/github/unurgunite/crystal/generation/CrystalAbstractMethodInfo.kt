package io.github.unurgunite.crystal.generation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAbstractMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalParameterList
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypeReference
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * One unimplemented abstract method: everything needed to generate a stub.
 * Signature identity is name + arity (Crystal overloads by arity).
 */
internal data class CrystalAbstractMethodInfo(
    val name: String,
    val arity: Int,
    val paramsText: String,
    val returnTypeText: String,
    val forallText: String,
) {
    val key: String = "$name#$arity"

    companion object {
        /**
         * Extracts [CrystalAbstractMethodInfo] from an `abstract def` node, or null
         * when the name is macro-generated (`{{...}}` — cannot be implemented
         * textually).
         */
        fun from(abstractDef: CrystalAbstractMethodDefinition): CrystalAbstractMethodInfo? {
            val name = abstractMethodName(abstractDef) ?: return null
            val params = abstractDef.parameterList
            return CrystalAbstractMethodInfo(
                name = name,
                arity = params?.parameterList?.size ?: 0,
                paramsText = params?.let { "(${it.text})" } ?: "",
                returnTypeText = abstractDef.typeReference?.text?.let { " : $it" } ?: "",
                forallText = forallSuffix(abstractDef),
            )
        }

        /**
         * Method name from the raw tokens between DEF and the parameter list
         * (`foo`, `foo?`, `<=>`, `self.foo`). The `method_name` BNF rule is
         * inlined (private), so at AST level the name is bare tokens — joined
         * verbatim. Walks AST nodes (not PSI children: bare tokens have no
         * PSI wrapper, only composites do).
         */
        private fun abstractMethodName(abstractDef: CrystalAbstractMethodDefinition): String? {
            var node = abstractDef.node.firstChildNode
            while (node != null && node.elementType != CrystalTypes.DEF) node = node.treeNext
            node = node?.treeNext
            val parts = StringBuilder()
            while (node != null && node.elementType !in NAME_TERMINATORS) {
                if (node.elementType != com.intellij.psi.TokenType.WHITE_SPACE &&
                    node.elementType != CrystalTypes.NEWLINE
                ) {
                    parts.append(node.text)
                }
                node = node.treeNext
            }
            val name = parts.toString()
            if (name.isEmpty() || "{{" in name || "{%" in name) return null
            return name
        }

        /** `forall T, U` suffix copied verbatim (params may reference the type vars). */
        private fun forallSuffix(abstractDef: CrystalAbstractMethodDefinition): String {
            val text = abstractDef.text
            val idx = text.indexOf("forall")
            if (idx < 0) return ""
            return " " + text.substring(idx).trim()
        }
    }
}

/** AST tokens ending the method-name run (parameter list, return type, generics). */
private val NAME_TERMINATORS =
    com.intellij.psi.tree.TokenSet.create(
        CrystalTypes.LPAREN,
        CrystalTypes.COLON,
        CrystalTypes.FORALL,
        CrystalTypes.PARAMETER_LIST,
        CrystalTypes.TYPE_REFERENCE,
    )

/** Type body (class/module/struct) of [element], or null (e.g. enums, top level). */
internal fun classBodyOf(element: PsiElement): PsiElement? =
    when (element) {
        is CrystalClassDefinition -> element.classBody
        is CrystalModuleDefinition -> element.classBody
        is CrystalStructDefinition -> element.classBody
        else -> null
    }

/** Name + arity of every concrete `def` directly owned by [owner]'s body. */
internal fun concreteSignatures(
    body: PsiElement,
    owner: PsiElement,
): Set<String> =
    PsiTreeUtil
        .collectElementsOfType(body, CrystalMethodDefinition::class.java)
        .filter { CrystalPsiUtils.getEnclosingType(it) == owner }
        .mapNotNull { def ->
            (def as? CrystalNamedElement)?.name?.let { name ->
                "$name#${def.parameterList?.parameterList?.size ?: 0}"
            }
        }.toSet()
