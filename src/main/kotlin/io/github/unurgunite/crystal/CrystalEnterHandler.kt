package io.github.unurgunite.crystal

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class CrystalEnterHandler : EnterHandlerDelegateAdapter() {
    /**
     * Context captured once at Enter time: document, caret position and the
     * previous line's text. Passed through the case handlers below so none of
     * them re-derives offsets or re-reads lines.
     */
    internal data class EnterContext(
        val document: Document,
        val editor: Editor,
        val caretOffset: Int,
        val caretLine: Int,
        val prevLineNumber: Int,
        val prevLineText: String,
    ) {
        val trimmed: String get() = prevLineText.trimEnd()

        val prevIndent: String get() = prevLineText.takeWhile { it == ' ' || it == '\t' }

        fun indentCurrentLine(
            newIndent: String,
            moveCaret: Boolean = true,
        ): EnterHandlerDelegate.Result {
            val lineStart = document.getLineStartOffset(caretLine)
            val lineEnd = document.getLineEndOffset(caretLine)
            val content = document.getText(TextRange(lineStart, lineEnd)).trimStart()
            document.replaceString(lineStart, lineEnd, "$newIndent$content")
            if (moveCaret) editor.caretModel.moveToOffset(lineStart + newIndent.length)
            return EnterHandlerDelegate.Result.Stop
        }
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext,
    ): EnterHandlerDelegate.Result {
        if (file.fileType != CrystalFileType) return EnterHandlerDelegate.Result.Continue
        val ctx = captureContext(editor) ?: return EnterHandlerDelegate.Result.Continue
        if (ctx.trimmed.isEmpty()) return EnterHandlerDelegate.Result.Continue
        return dispatchEnter(ctx)
    }

    /** Caret/previous-line snapshot, or null above the first line. */
    private fun captureContext(editor: Editor): EnterContext? {
        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val caretLine = document.getLineNumber(caretOffset)

        // We need to check the line ABOVE the caret (where the user pressed Enter)
        if (caretLine < 1) return null
        val prevLineNumber = caretLine - 1
        val prevLineStart = document.getLineStartOffset(prevLineNumber)
        val prevLineEnd = document.getLineEndOffset(prevLineNumber)
        val prevLineText = document.getText(TextRange(prevLineStart, prevLineEnd))
        return EnterContext(document, editor, caretOffset, caretLine, prevLineNumber, prevLineText)
    }

    /** Case dispatch: heredoc end, dedent keywords, brackets/collections, heredoc start, block openers. */
    private fun dispatchEnter(ctx: EnterContext): EnterHandlerDelegate.Result =
        CrystalEnterHeredoc.handleHeredocEnd(ctx, ctx.trimmed)
            ?: CrystalEnterBlocks.handleDedentKeyword(ctx)
            ?: braceCollectionOrClosing(ctx)
            ?: CrystalEnterHeredoc.handleHeredocStart(ctx, ctx.trimmed)
            ?: blockOpenerOrContinue(ctx)

    /** Brace/bracket splits, collection continuations and closing-bracket lines. */
    private fun braceCollectionOrClosing(ctx: EnterContext): EnterHandlerDelegate.Result? {
        val trimmed = ctx.trimmed
        val document = ctx.document
        return when {
            // Handle brace/bracket enter: { | } or [ | ]
            trimmed.endsWith("{") || trimmed.endsWith("[") -> {
                CrystalEnterBrackets.handleBraceOrBracketEnter(ctx)
            }

            // Handle continuation inside collections (lines ending with ,)
            trimmed.endsWith(",") && CrystalEnterBrackets.isInsideUnclosedBracket(document, ctx.prevLineNumber) -> {
                val elemIndent = CrystalEnterBrackets.findFirstElementIndent(document, ctx.prevLineNumber) ?: ""
                ctx.indentCurrentLine(elemIndent)
            }

            // Handle closing bracket/brace on its own line inside a collection
            // e.g. pressing Enter after 3 in: a = [1,\n     2,3<caret>]
            // The ] should align with the opener (variable name), not the previous element
            else -> {
                CrystalEnterBrackets.handleClosingBracketLine(ctx)
            }
        }
    }

    /** Block opener indents (with `end` insertion) or fall through. */
    private fun blockOpenerOrContinue(ctx: EnterContext): EnterHandlerDelegate.Result {
        if (!CrystalBlockOpenerDetect.endsWithBlockOpener(ctx.trimmed)) return EnterHandlerDelegate.Result.Continue
        return CrystalEnterBlocks.handleBlockOpenerEnter(ctx, ctx.trimmed)
    }

    /** First word, trimmed text and raw text of a document line. */
    internal data class LineParts(
        val firstWord: String,
        val trimmed: String,
        val text: String,
    )

    internal companion object {
        fun lineParts(
            document: Document,
            line: Int,
        ): LineParts {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val text = document.getText(TextRange(lineStart, lineEnd))
            val trimmed = text.trimStart()
            return LineParts(trimmed.split(Regex("[\\s({]"), 2)[0], trimmed, text)
        }
    }
}

/**
 * Word-constituent character for the balance tokenizer: letters, digits,
 * underscore and the Crystal method suffix `?` (`empty?` is one word).
 */
internal fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '?'
