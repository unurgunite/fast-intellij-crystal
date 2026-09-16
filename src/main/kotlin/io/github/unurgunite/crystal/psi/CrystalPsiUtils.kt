package io.github.unurgunite.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.stubs.CrystalNamedStub

/**
 * Utility functions for Crystal PSI elements.
 */
object CrystalPsiUtils {
    /**
     * Builds the fully-qualified name of a class/module/struct/enum definition
     * by walking up the PSI tree and collecting enclosing type names.
     *
     * Examples:
     * - `class Foo; class Sub; end; end` → `"Foo::Sub"`
     * - `class Foo::Sub` (namespace-defined) → `"Foo::Sub"` (from stub.name)
     * - `class C` in `class B` in `class A` → `"A::B::C"`
     * - `module Foo; class Bar; end; end` → `"Foo::Bar"`
     */
    fun buildQualifiedName(element: PsiElement): String? {
        val parts = mutableListOf<String>()
        var current: PsiElement? = element
        while (current != null) {
            val name =
                when (current) {
                    is CrystalClassDefinition -> extractQualifiedTypeName(current) ?: current.name
                    is CrystalModuleDefinition -> extractQualifiedTypeName(current) ?: current.name
                    is CrystalStructDefinition -> extractQualifiedTypeName(current) ?: current.name
                    is CrystalEnumDefinition -> extractQualifiedTypeName(current) ?: current.name
                    else -> null
                }
            if (name != null) parts.add(0, name)
            current = current.parent
        }
        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * Builds the fully-qualified name of a class/module/struct/enum definition
     * from a stub element, using the stub tree for faster traversal.
     */
    fun buildQualifiedNameFromStub(stub: com.intellij.psi.stubs.StubElement<*>): String? {
        val parts = mutableListOf<String>()
        var current: com.intellij.psi.stubs.StubElement<*>? = stub
        while (current != null) {
            if (current is CrystalNamedStub) {
                val name = current.name
                if (name != null) {
                    parts.add(0, name)
                }
            }
            current = current.parentStub
        }
        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * Returns the immediate enclosing class/module/struct/enum of an element.
     */
    fun getEnclosingType(element: PsiElement): PsiElement? = PsiTreeUtil.findFirstParent(element, ::isTypeDefinition)

    /**
     * True for type definitions: class, module, struct, enum.
     * Centralizes the `is A || is B || ...` chains scattered across navigation,
     * completion and documentation code.
     */
    fun isTypeDefinition(element: PsiElement): Boolean =
        element is CrystalClassDefinition ||
            element is CrystalModuleDefinition ||
            element is CrystalStructDefinition ||
            element is CrystalEnumDefinition

    /**
     * True for any named definition: method, type definition or parameter.
     * (Macros excluded: hover/navigation treat macro definitions separately.)
     */
    fun isDefinition(element: PsiElement): Boolean =
        element is CrystalMethodDefinition ||
            element is CrystalParameter ||
            isTypeDefinition(element)

    /**
     * True for scope boundaries that PSI walks must not cross: methods, macros
     * and type definitions. Crossing them would leak resolution into unrelated
     * scopes (and, for files, trigger lazy parsing of the whole project).
     */
    fun isScopeBoundary(element: PsiElement): Boolean =
        element is CrystalMethodDefinition ||
            element is CrystalMacroDefinition ||
            isTypeDefinition(element)

    /**
     * True for method-call-shaped composites: parenthesized calls, bare calls,
     * bare commands (`greet "Hans"`) and property macros (`getter name : Type`).
     */
    fun isCallExpression(element: PsiElement): Boolean =
        element is CrystalMethodCallExpression ||
            element is CrystalBareMethodCallExpression ||
            element is CrystalBareCommandExpression ||
            element is CrystalBareCommandSpaceFirst ||
            element is CrystalPropertyMacro

    // Labels, splats and whitespace carry no value inside argument wrappers
    // (`foo(name: 1)`, `foo(*args)`); the meaningful child is the value expression.
    internal val SKIPPABLE_WRAPPER_TOKENS =
        com.intellij.psi.tree.TokenSet.create(
            CrystalTypes.IDENTIFIER,
            CrystalTypes.COLON,
            CrystalTypes.STAR,
            CrystalTypes.DOUBLE_STAR,
            com.intellij.psi.TokenType.WHITE_SPACE,
        )

    /**
     * First child that is not a label, splat or whitespace (null when all children
     * are skippable). Shared by highlight-target and expression-container lookups.
     */
    fun firstSignificantChild(element: PsiElement): PsiElement? {
        var child = element.firstChild
        while (child != null) {
            if (!isSkippableWrapperChild(child)) return child
            child = child.nextSibling
        }
        return null
    }

    /**
     * Previous non-whitespace sibling, or null at the start. Shared by DOT-call
     * detection in inspections and the bare-call scanner's forward validation.
     */
    fun prevMeaningfulSibling(element: PsiElement): PsiElement? {
        var sibling = element.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        return sibling
    }

    /**
     * Receiver expression before a DOT: the sibling itself, or — when DOT is the
     * first child of `dot_call_access` — the sibling before the composite parent
     * in the flattened postfix sequence.
     */
    fun receiverBeforeDot(
        methodNameNode: PsiElement,
        dot: PsiElement,
    ): PsiElement? {
        prevMeaningfulSibling(dot)?.let { return it }
        val dotCallAccess = methodNameNode.parent
        if (dotCallAccess is CrystalDotCallAccess) {
            return prevMeaningfulSibling(dotCallAccess)
        }
        return null
    }

    /**
     * Builds the full namespace path from a [CrystalNamespaceAccess] element
     * by walking left through preceding [CrystalNamespaceAccess] and
     * [CrystalVariableReference] elements.
     *
     * Example: for `Foo::Sub.space`, when called on the `::Sub` element,
     * returns `"Foo::Sub"`.
     */
    fun buildNamespacePath(namespaceAccess: CrystalNamespaceAccess): String {
        val parts = mutableListOf<String>()

        // Get the CONSTANT from this namespace_access
        namespaceAccess.node
            .findChildByType(io.github.unurgunite.crystal.psi.CrystalTypes.CONSTANT)
            ?.text
            ?.let { parts.add(0, it) }

        // Walk left through preceding namespace_access and variable_reference
        var current = namespaceAccess.prevSibling
        while (current != null && collectPathPart(current, parts)) {
            current = current.prevSibling
        }

        return parts.joinToString("::")
    }
}

/** Whitespace/label/splat child inside an argument wrapper — carries no value. */
private fun isSkippableWrapperChild(child: PsiElement): Boolean {
    val type = child.node?.elementType
    return (type == null && child is PsiWhiteSpace) || type in CrystalPsiUtils.SKIPPABLE_WRAPPER_TOKENS
}

/**
 * Collects one CONSTANT segment from [current] into [parts].
 * Returns false at the path start (leading variable reference) or at any
 * non-namespace element, ending the walk without a jump statement.
 */
private fun collectPathPart(
    current: PsiElement,
    parts: MutableList<String>,
): Boolean {
    if (current is PsiWhiteSpace ||
        current.node?.elementType == CrystalTypes.NEWLINE
    ) {
        return true
    }
    if (current is CrystalNamespaceAccess) {
        current.node
            .findChildByType(CrystalTypes.CONSTANT)
            ?.text
            ?.let { parts.add(0, it) }
        return true
    }
    if (current is CrystalVariableReference) {
        current.node
            .findChildByType(CrystalTypes.CONSTANT)
            ?.text
            ?.let { parts.add(0, it) }
    }
    return false
}

/**
 * Extracts the fully-qualified type name from a type definition's PSI children.
 * For `class Foo::Bar`, returns "Foo::Bar". For `class Baz`, returns null.
 *
 * Works by scanning the PSI children for CONSTANT tokens (type_name is inlined as
 * direct children of the definition node) and joining them with "::" if there are
 * multiple CONSTANTS before the class_body.
 */
private fun extractQualifiedTypeName(element: PsiElement): String? {
    val constants = mutableListOf<String>()
    var child = element.node.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.CONSTANT) {
            constants.add(child.text)
        }
        // Stop at class_body — CONSTANTS inside the body are not part of the type_name
        if (child.elementType == CrystalTypes.CLASS_BODY) break
        child = child.treeNext
    }
    // If there are multiple CONSTANTS (e.g. Foo::Bar), return the full qualified name
    return if (constants.size >= 2) constants.joinToString("::") else null
}
