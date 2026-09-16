package io.github.unurgunite.crystal.psi.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import io.github.unurgunite.crystal.psi.CrystalNamespaceAccess
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver
import io.github.unurgunite.crystal.type.CrystalTypeInference

/**
 * Receiver class name (for [CrystalMethodByClassIndex] lookup),
 * whether it is a CONSTANT (static) or IDENTIFIER (instance) reference,
 * and the full qualified name (for namespace disambiguation).
 *
 * - CONSTANT receiver: the name IS the class name → exact static lookup.
 * - Namespace receiver (e.g. `Foo::Sub`): walks left to build full path.
 * - IDENTIFIER receiver: infer the variable's type via [CrystalTypeInference].
 *   If no type can be inferred, className stays `null` and resolution aborts.
 */
internal data class ReceiverInfo(
    val classNames: List<String>,
    // inferred member type names (empty = unknown → no guessing)
    val isStatic: Boolean,
    val rawName: String,
    // full path for disambiguation, null if not a namespace path
    val qualifiedName: String? = null,
)

/**
 * Receiver scanning for DOT-call references: walks `prevSibling` (skipping
 * whitespace) from the `dot_call_access` element — the DOT is its first child,
 * so the receiver is the preceding sibling in the flattened `postfix_expression`
 * sequence. Split out of [CrystalDotCallReference].
 */
internal object CrystalDotCallReceiver {
    fun resolve(element: PsiElement): ReceiverInfo? {
        // Walk siblings before this dot_call_access, skipping whitespace,
        // to find the receiver expression. The DOT is inside this composite,
        // so the receiver lives on the prevSibling of this element.
        var prev = element.prevSibling
        while (prev is PsiWhiteSpace || prev?.node?.elementType.toString() == "WHITE_SPACE" ||
            prev?.node?.elementType == CrystalTypes.NEWLINE
        ) {
            prev = prev?.prevSibling
        }
        if (prev == null) return null

        // Namespace receiver: previous element is a CrystalNamespaceAccess (e.g. `::Sub` in `Foo::Sub.space`)
        if (prev is CrystalNamespaceAccess) {
            return buildNamespaceReceiver(prev)
        }
        return receiverFromElement(prev, element.project)
    }

    /** Receiver from a non-namespace element: CONSTANT, identifier (inferred), `self`, or literal. */
    private fun receiverFromElement(
        prev: PsiElement,
        project: com.intellij.openapi.project.Project,
    ): ReceiverInfo? {
        constantReceiver(prev)?.let { return it }
        if (isIdentifierReceiver(prev)) return inferredIdentifierReceiver(prev, project)
        if (prev.node?.elementType == CrystalTypes.SELF) return selfReceiver(prev)
        return literalReceiver(prev)
    }

    /** Direct CONSTANT token or a composite wrapping one (e.g. variable_reference). */
    private fun constantReceiver(prev: PsiElement): ReceiverInfo? {
        // Direct CONSTANT token (e.g. `Apfel` in `Apfel.tanzen`)
        if (prev.node?.elementType == CrystalTypes.CONSTANT) {
            return ReceiverInfo(listOf(prev.text), isStatic = true, rawName = prev.text)
        }
        // Composite wrapping a CONSTANT (e.g. variable_reference wrapping `Apfel`).
        val constantChild = prev.node?.findChildByType(CrystalTypes.CONSTANT) ?: return null
        return ReceiverInfo(listOf(constantChild.text), isStatic = true, rawName = constantChild.text)
    }

    /** IDENTIFIER token or a wrapper containing one (e.g. `a` in `a.essen`). */
    private fun isIdentifierReceiver(prev: PsiElement): Boolean =
        prev.node?.elementType == CrystalTypes.IDENTIFIER ||
            prev.node?.findChildByType(CrystalTypes.IDENTIFIER) != null

    /** IDENTIFIER receiver: inferred variable type(s); empty list means unknown, no guessing. */
    private fun inferredIdentifierReceiver(
        prev: PsiElement,
        project: com.intellij.openapi.project.Project,
    ): ReceiverInfo {
        val varName = prev.text
        val inferredTypes = CrystalTypeInference.inferTypeList(varName, prev, project)
        return ReceiverInfo(inferredTypes, isStatic = false, rawName = varName)
    }

    /**
     * `self` receiver (e.g. `self.foo`) → the enclosing type. `self` is a keyword
     * token (SELF), not an IDENTIFIER. Works for both instance (`self` = instance)
     * and class (`def self.foo`, `self` = the class) methods.
     */
    private fun selfReceiver(prev: PsiElement): ReceiverInfo? {
        val enclosing = CrystalPsiUtils.getEnclosingType(prev)
        val className = enclosing?.let { CrystalPsiUtils.buildQualifiedName(it) } ?: return null
        return ReceiverInfo(listOf(className), isStatic = false, rawName = "self")
    }

    /**
     * Literal / expression receiver (e.g. `1.foo`, `"s".upcase`, `[].size`, `{}.keys`)
     * → infer the type via the expression type resolver, stripping generics
     * (Array(Int32) → Array) so the class name matches the symbol-table key.
     */
    private fun literalReceiver(prev: PsiElement): ReceiverInfo? {
        val typeName = inferReceiverTypeFromExpression(prev) ?: return null
        return ReceiverInfo(listOf(typeName), isStatic = false, rawName = prev.text)
    }

    /** Descend through wrapper nodes until [CrystalExpressionTypeResolver] yields a type. */
    private fun inferReceiverTypeFromExpression(expr: PsiElement): String? {
        var current: PsiElement? = expr
        while (current != null) {
            val resolved = CrystalExpressionTypeResolver.resolveType(current)
            if (resolved != null) return resolved.typeName.substringBefore("(")
            current = current.children.firstOrNull { it !is PsiWhiteSpace }
        }
        return null
    }

    /**
     * Builds a [ReceiverInfo] from a [CrystalNamespaceAccess] element by walking
     * left through preceding [CrystalNamespaceAccess] and [CrystalVariableReference]
     * elements to reconstruct the full qualified name.
     *
     * Example: for `Foo::Sub.space`, when called on `::Sub`, returns:
     * ReceiverInfo(className="Sub", qualifiedName="Foo::Sub")
     */
    private fun buildNamespaceReceiver(namespaceAccess: CrystalNamespaceAccess): ReceiverInfo {
        val pathParts = mutableListOf<String>()

        // Get the CONSTANT from this namespace_access
        namespaceAccess.node
            .findChildByType(CrystalTypes.CONSTANT)
            ?.text
            ?.let { pathParts.add(0, it) }

        // Walk left through preceding namespace_access and variable_reference
        var current = namespaceAccess.prevSibling
        while (current != null) {
            val next = namespaceStep(current, pathParts) ?: break
            current = next
        }

        val simpleName = pathParts.last()
        val qualifiedName = pathParts.joinToString("::")
        return ReceiverInfo(listOf(simpleName), isStatic = true, rawName = simpleName, qualifiedName = qualifiedName)
    }

    /**
     * One step of the leftward namespace walk: prepends the CONSTANT and returns
     * the next sibling to visit, or null to stop (variable reference or other).
     */
    private fun namespaceStep(
        current: PsiElement,
        pathParts: MutableList<String>,
    ): PsiElement? {
        when {
            current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE -> {
                return current.prevSibling
            }

            current is CrystalNamespaceAccess || current is CrystalVariableReference -> {
                current.node
                    .findChildByType(CrystalTypes.CONSTANT)
                    ?.text
                    ?.let { pathParts.add(0, it) }
                if (current is CrystalNamespaceAccess) return current.prevSibling
                return null
            }

            else -> {
                return null
            }
        }
    }
}
