package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAsmExpression
import io.github.unurgunite.crystal.psi.CrystalAsmOperand
import io.github.unurgunite.crystal.psi.CrystalRegexExpression
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Semantic highlighter for Crystal.
 *
 * CONSTANT and IDENTIFIER tokens are NOT highlighted by the lexer (EMPTY_KEYS).
 * All context-sensitive coloring happens here, giving the annotator full control
 * over which color each token gets — no conflicts with lexer-level highlighting.
 *
 * Shape-specific logic lives in [CrystalIdentifierHighlight] (identifiers),
 * [CrystalRegexHighlight] (regex literals and escapes) and
 * [CrystalHeredocValidation] (heredoc delimiters); emission in [CrystalAnnotationEmit].
 */
class CrystalAnnotator : Annotator {
    override fun annotate(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val handler = tokenHandlers[element.node.elementType] ?: return
        handler(element, holder)
    }

    /**
     * Context-sensitive highlighting for CONSTANT tokens:
     * - Inside class/module/struct/enum definition → CLASS_DECLARATION
     * - Inside method definition (def self.Foo) → FUNCTION_DECLARATION
     * - Everywhere else → CONSTANT (type references, standalone constants)
     */
    private fun annotateConstantToken(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        CrystalAnnotationEmit.apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
    }

    /**
     * Highlights $0, $1, $2, ... operand references inside asm template strings
     * with the same color as numbers.
     */
    private fun annotateAsmOperandReferences(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        // Only inside asm expressions
        PsiTreeUtil.getParentOfType(element, CrystalAsmExpression::class.java) ?: return

        // Only the first string_expression (template) — check it's the template, not a constraint
        val stringExpr = element.parent
        val asmOperand = PsiTreeUtil.getParentOfType(stringExpr, CrystalAsmOperand::class.java)
        if (asmOperand != null) return // This is a constraint string like "=r", not the template

        val text = element.text
        val startOffset = element.textRange.startOffset
        for (match in ASM_OPERAND_REF.findAll(text)) {
            val range = TextRange(startOffset + match.range.first, startOffset + match.range.last + 1)
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(CrystalSyntaxHighlighter.NUMBER)
                .create()
        }
    }

    /**
     * Highlights TODO, FIXME keywords (and the rest of the line) inside comments
     * with a distinct color matching Ruby's style (same as numbers/symbols).
     * Uses enforcedTextAttributes to override IntelliJ's built-in TODO highlighting.
     */
    private fun annotateTodoComment(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val text = element.text
        val startOffset = element.textRange.startOffset
        val match = TODO_PATTERN.find(text) ?: return
        val range = TextRange(startOffset + match.range.first, startOffset + text.length)
        val scheme = EditorColorsManager.getInstance().globalScheme
        val attrs = scheme.getAttributes(DefaultLanguageHighlighterColors.NUMBER)
        if (attrs != null && attrs.foregroundColor != null) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .enforcedTextAttributes(attrs)
                .create()
        }
    }

    /** Leaf token type → handler. Table-driven so `annotate` stays a single lookup. */
    private val tokenHandlers: Map<IElementType, (PsiElement, AnnotationHolder) -> Unit> =
        mapOf(
            CrystalTypes.CONSTANT to { element, holder -> annotateConstantToken(element, holder) },
            CrystalTypes.IDENTIFIER to { element, holder ->
                CrystalIdentifierHighlight.annotateIdentifierToken(element, holder)
            },
            // Highlight $0, $1, etc. inside asm template strings
            CrystalTypes.STRING_LITERAL to { element, holder -> annotateAsmOperandReferences(element, holder) },
            // Highlight TODO/FIXME/NOTE in comments
            CrystalTypes.LINE_COMMENT to { element, holder -> annotateTodoComment(element, holder) },
            // Highlight regex sub-patterns inside regex literals
            CrystalTypes.REGEX_LITERAL to { element, holder ->
                CrystalRegexHighlight.annotateRegexLiteral(element.text, element.textRange.startOffset, holder)
            },
            // Validate regex escape sequences (lexer tokenizes them as STRING_ESCAPE)
            CrystalTypes.STRING_ESCAPE to { element, holder ->
                if (element.parent is CrystalRegexExpression) {
                    CrystalRegexHighlight.annotateInvalidRegexEscapeToken(element, holder)
                }
            },
            // Validate heredoc pairs: start must have matching end delimiter
            CrystalTypes.HEREDOC_START to { element, holder ->
                CrystalHeredocValidation.validateHeredocStart(element, holder)
            },
            // Validate heredoc end delimiter indent
            CrystalTypes.HEREDOC_END to { element, holder ->
                CrystalHeredocValidation.validateHeredocEnd(element, holder)
            },
        )

    companion object {
        private val TODO_PATTERN = Regex("\\b(TODO|FIXME)\\b")

        private val ASM_OPERAND_REF = Regex("\\$\\d+")
    }
}
