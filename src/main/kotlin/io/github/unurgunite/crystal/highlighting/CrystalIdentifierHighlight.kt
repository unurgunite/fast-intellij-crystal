package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalArgument
import io.github.unurgunite.crystal.psi.CrystalBareArgument
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalClassBody
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalHashEntry
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalParameterList
import io.github.unurgunite.crystal.psi.CrystalStatementList
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference

/**
 * Context-sensitive IDENTIFIER highlighting:
 * - Method/macro name definitions → CONSTANT
 * - Parameter definitions and usages → PARAMETER
 * - Built-in macro calls (`getter`, `record`, ...) → KEYWORD
 * - Hash-shorthand and named-argument keys → SYMBOL
 * - Everything else → IDENTIFIER
 */
internal object CrystalIdentifierHighlight {
    fun annotateIdentifierToken(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val parent = element.parent

        // Method/macro name definition (method_name is now private, IDENTIFIER is direct child of method_definition)
        if (parent is CrystalMethodDefinition || parent is CrystalMacroDefinition) {
            CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
            return
        }

        // Parameter definition
        if (parent is CrystalParameter) {
            CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.PARAMETER)
            return
        }

        // Built-in macros (getter, setter, property, etc.) highlighted as keywords
        if (isBuiltinMacroCall(element)) {
            CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.KEYWORD)
            return
        }

        // Hash key in shorthand syntax (name: value) or named argument (key: value) → Symbol color (like Ruby)
        if (isKeyPositionIdentifier(element)) {
            CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.SYMBOL)
            return
        }

        // Parameter usage inside method/macro body
        if (isParameterUsage(element)) {
            CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.PARAMETER)
            return
        }

        // Default: normal identifier
        CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.IDENTIFIER)
    }

    /**
     * Key-position identifier: hash shorthand (`name: value`) or named argument
     * (`key: value`) — both act like symbols and are colored accordingly.
     */
    private fun isKeyPositionIdentifier(element: PsiElement): Boolean = isHashShorthandKey(element) || isNamedArgumentKey(element)

    /**
     * Check if an IDENTIFIER token is a hash key in shorthand syntax (name: value).
     * In this form, the key acts like a symbol and should be colored accordingly.
     */
    private fun isHashShorthandKey(element: PsiElement): Boolean {
        // Structure: IDENTIFIER → CrystalVariableReference → CrystalExpression → CrystalHashEntry
        val varRef = element.parent as? CrystalVariableReference ?: return false
        val expr = varRef.parent as? CrystalExpression ?: return false
        val hashEntry = expr.parent as? CrystalHashEntry ?: return false

        // Must be the first expression (the key, not the value)
        val expressions = hashEntry.expressionList
        if (expressions.isEmpty() || expressions[0] != expr) return false

        // Must use COLON separator (shorthand), not DOUBLE_ARROW (rocket =>)
        return hashEntry.node.findChildByType(CrystalTypes.COLON) != null
    }

    /**
     * Check if an IDENTIFIER token is a named argument key (key: value).
     * Named arguments act like symbols and should be colored accordingly.
     */
    private fun isNamedArgumentKey(element: PsiElement): Boolean {
        val parent = element.parent ?: return false
        // In argument or bare_argument, the IDENTIFIER is a direct child followed by COLON
        if (parent !is CrystalArgument && parent !is CrystalBareArgument) return false

        // Check that this IDENTIFIER is followed by a COLON sibling
        val nextSibling = element.nextSibling ?: return false
        return nextSibling.node.elementType == CrystalTypes.COLON
    }

    /**
     * Check if an IDENTIFIER is a built-in macro call (getter, setter, property, etc.)
     * These are highlighted as keywords since they act like language constructs.
     */
    private fun isBuiltinMacroCall(element: PsiElement): Boolean {
        val text = element.text
        if (text !in BUILTIN_MACROS) return false
        // Must be a statement-level call (first token on a logical line / inside class body)
        val parent = element.parent
        // Bare method call expression or direct child of statement list
        return parent is CrystalBareMethodCallExpression ||
            (parent?.parent is CrystalStatementList) ||
            (parent?.parent is CrystalClassBody)
    }

    /**
     * Check if an IDENTIFIER token is a usage of a method/macro parameter
     * or a block parameter.
     */
    private fun isParameterUsage(element: PsiElement): Boolean {
        val name = element.text
        if (name.isBlank()) return false
        return isMethodParamUsage(element, name) || isBlockParamUsage(element, name)
    }

    /** Usage of a parameter of the enclosing method or macro definition. */
    private fun isMethodParamUsage(
        element: PsiElement,
        name: String,
    ): Boolean {
        val methodDef =
            PsiTreeUtil.getParentOfType(
                element,
                CrystalMethodDefinition::class.java,
                CrystalMacroDefinition::class.java,
            ) ?: return false
        val paramList =
            when (methodDef) {
                is CrystalMethodDefinition -> methodDef.parameterList
                is CrystalMacroDefinition -> methodDef.parameterList
                else -> null
            } ?: return false
        return name in parameterNames(paramList)
    }

    /** Usage of a block parameter (e.g. `3.times do |i|`). */
    private fun isBlockParamUsage(
        element: PsiElement,
        name: String,
    ): Boolean {
        val block = PsiTreeUtil.getParentOfType(element, CrystalBlock::class.java) ?: return false
        val paramList = block.parameterList ?: return false
        return name in parameterNames(paramList)
    }

    /** Declared parameter names of a parameter list. */
    private fun parameterNames(paramList: CrystalParameterList): Set<String> =
        paramList.parameterList
            .mapNotNull { param ->
                param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
            }.toSet()

    private val BUILTIN_MACROS =
        setOf(
            "getter",
            "setter",
            "property",
            "class_getter",
            "class_setter",
            "class_property",
            "record",
            "delegate",
            "forward_missing_to",
            "def_equals",
            "def_hash",
            "def_equals_and_hash",
            "def_clone",
            "def_clone_as",
        )
}
