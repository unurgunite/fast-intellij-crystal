package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalClassBody
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalForStatement
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalMethodBody
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalNamespaceAccess
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex

/**
 * Computes the completion prefix from the raw document text before the caret,
 * treating a leading `@` or `@@` as part of the variable name.
 *
 * Examples (text before caret → returned prefix):
 *   "@"       → "@"
 *   "@@"      → "@@"
 *   "@@vari"  → "@@vari"
 *   "@foo"    → "@foo"
 *   "mein"    → "mein"
 *   "Str"     → "Str"
 *   "foo.bar" → "bar"
 */
internal fun computeCompletionPrefix(
    editor: com.intellij.openapi.editor.Editor,
    offset: Int,
): String {
    val text =
        editor.document.charsSequence
            .subSequence(0, offset)
            .toString()
    val match = "([@]@?[A-Za-z0-9_]*)$".toRegex().find(text)
    return match?.groupValues?.get(1) ?: ""
}

/**
 * Code completion contributor for Crystal.
 *
 * Provides 3 completion modes:
 * 1. Dot after CONSTANT (Class.): static methods of that class
 * 2. Dot after identifier (var.): instance methods based on inferred type
 * 3. Free-text: all classes + all methods + local variables/parameters
 */
class CrystalCompletionContributor : CompletionContributor() {
    init {
        // General pattern: any identifier position in Crystal files
        val crystalPattern =
            PlatformPatterns
                .psiElement()
                .withLanguage(CrystalLanguage)

        extend(CompletionType.BASIC, crystalPattern, CrystalCompletionProvider())
    }

    /**
     * Single provider that dispatches based on context (dot-completion vs free-text).
     * Case handlers live in [CrystalCompletionCases]; this class keeps only the
     * IntelliJ dispatch surface plus shared leaf helpers.
     */
    private class CrystalCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            CrystalCompletionCases.addCompletions(parameters, result)
        }
    }

    companion object {
        // Lookup priorities (higher wins). Ordered by specificity: block parameters
        // shadow everything, then explicit parameters, loop variables, locals, and
        // finally class members (nearest class first, then superclass).
        internal const val PRIORITY_BLOCK_PARAMETER = 120.0
        internal const val PRIORITY_EXPLICIT_PARAMETER = 100.0
        internal const val PRIORITY_FOR_VARIABLE = 90.0
        internal const val PRIORITY_LOCAL = 50.0
        internal const val PRIORITY_CLASS_VARIABLE = 40.0
        internal const val PRIORITY_OWN_CLASS_METHOD = 30.0
        internal const val PRIORITY_SUPERCLASS_METHOD = 20.0

        // Type names: above the unprioritized index tail (so stdlib basics are
        // not buried past the lookup cap by thousands of indexed symbols),
        // below scope items (locals/params/methods of the user's own code win
        // in free text). Annotation context offers only types, so the split
        // between stdlib basics and project types is just stable ordering.
        internal const val PRIORITY_STDLIB_TYPE = 60.0
        internal const val PRIORITY_PROJECT_TYPE = 55.0
        internal const val PRIORITY_FREE_TEXT_TYPE = 15.0

        internal fun prioritizedLookup(
            name: String,
            icon: javax.swing.Icon,
            typeText: String,
            priority: Double,
        ): com.intellij.codeInsight.lookup.LookupElement {
            val lookup =
                com.intellij.codeInsight.lookup.LookupElementBuilder
                    .create(name)
                    .withIcon(icon)
                    .withTypeText(typeText, true)
                    .withBoldness(true)
            return com.intellij.codeInsight.completion.PrioritizedLookupElement
                .withPriority(lookup, priority)
        }

        internal fun computeCompletionPrefix(
            editor: com.intellij.openapi.editor.Editor,
            offset: Int,
        ): String =
            io.github.unurgunite.crystal.completion
                .computeCompletionPrefix(editor, offset)

        internal fun getPreviousNonWhitespaceLeaf(element: com.intellij.psi.PsiElement): com.intellij.psi.PsiElement? {
            var prev =
                com.intellij.psi.util.PsiTreeUtil
                    .prevLeaf(element)
            while (prev != null && prev.text.isBlank()) {
                prev =
                    com.intellij.psi.util.PsiTreeUtil
                        .prevLeaf(prev)
            }
            return prev
        }

        internal fun isAfterNumericLiteral(position: com.intellij.psi.PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            val tokenType = prev.node?.elementType
            if (tokenType != CrystalTypes.INTEGER_LITERAL && tokenType != CrystalTypes.FLOAT_LITERAL) return false
            val prevLine =
                prev.containingFile
                    ?.viewProvider
                    ?.document
                    ?.getLineNumber(prev.textRange.endOffset)
            val posLine =
                position.containingFile
                    ?.viewProvider
                    ?.document
                    ?.getLineNumber(position.textRange.startOffset)
            return prevLine == posLine
        }

        internal fun isAfterDefKeywordInClassBody(position: com.intellij.psi.PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            if (prev.node.elementType != CrystalTypes.DEF) return false
            val classBody =
                com.intellij.psi.util.PsiTreeUtil
                    .getParentOfType(position, CrystalClassBody::class.java)
            if (classBody != null) return true
            val structDef =
                com.intellij.psi.util.PsiTreeUtil
                    .getParentOfType(position, CrystalStructDefinition::class.java)
            return structDef != null
        }

        internal fun isInTypeAnnotationContext(position: com.intellij.psi.PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            return when (prev.node.elementType) {
                CrystalTypes.COLON -> {
                    isTypePositionAfterLeaf(
                        prev,
                        setOf(CrystalTypes.IDENTIFIER, CrystalTypes.RPAREN, CrystalTypes.INSTANCE_VAR, CrystalTypes.CLASS_VAR),
                    )
                }

                CrystalTypes.PIPE -> {
                    isTypePositionAfterLeaf(prev, setOf(CrystalTypes.CONSTANT, CrystalTypes.QUESTION, CrystalTypes.RPAREN))
                }

                CrystalTypes.LPAREN -> {
                    isTypePositionAfterLeaf(prev, setOf(CrystalTypes.CONSTANT))
                }

                CrystalTypes.COMMA -> {
                    prev.parent?.node?.elementType == CrystalTypes.TYPE_ARGUMENTS
                }

                else -> {
                    false
                }
            }
        }

        /** True when the leaf before [marker] is one of [expected] (e.g. `name : <caret>`). */
        private fun isTypePositionAfterLeaf(
            marker: com.intellij.psi.PsiElement,
            expected: Set<com.intellij.psi.tree.IElementType>,
        ): Boolean {
            val before = getPreviousNonWhitespaceLeaf(marker) ?: return false
            return before.node.elementType in expected
        }

        internal fun isInClassBodyNotMethod(position: com.intellij.psi.PsiElement): Boolean {
            val classBody =
                com.intellij.psi.util.PsiTreeUtil
                    .getParentOfType(position, CrystalClassBody::class.java)
                    ?: return false
            val methodBody =
                com.intellij.psi.util.PsiTreeUtil
                    .getParentOfType(position, CrystalMethodBody::class.java)
            if (methodBody != null) return false
            val prev = getPreviousNonWhitespaceLeaf(position)
            if (prev != null && prev.node.elementType == CrystalTypes.DEF) return false
            return true
        }

        internal fun isInAnnotationContext(position: com.intellij.psi.PsiElement): Boolean {
            val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
            if (prev.node.elementType != CrystalTypes.LBRACKET) return false
            val beforeBracket = getPreviousNonWhitespaceLeaf(prev) ?: return false
            return beforeBracket.node.elementType == CrystalTypes.AT
        }
    }
}

/**
 * Checks if the position is inside a string literal (not inside interpolation).
 * Returns true if completion should be suppressed.
 */
internal fun isInsideStringLiteral(position: PsiElement): Boolean {
    // If the dummy identifier is placed inside a STRING_LITERAL token context
    val tokenType = position.node?.elementType
    if (tokenType == CrystalTypes.STRING_LITERAL) return true

    // Check if parent is a string_expression — we might be between string parts
    val parent = position.parent
    if (parent?.node?.elementType == CrystalTypes.STRING_EXPRESSION) {
        // If we're inside interpolation (between INTERPOLATION_BEGIN and END), allow completion
        var sibling = position.prevSibling
        while (sibling != null) {
            val sibType = sibling.node?.elementType
            if (sibType == CrystalTypes.STRING_INTERPOLATION_BEGIN) return false
            if (sibType == CrystalTypes.STRING_INTERPOLATION_END) break
            sibling = sibling.prevSibling
        }
        return true
    }
    return false
}
