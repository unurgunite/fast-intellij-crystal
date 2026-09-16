package io.github.unurgunite.crystal.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * Bracket/collection Enter handling: `{ | }` / `[ | ]` splits, continuation
 * lines inside collections and closing-bracket alignment. Split out of
 * `CrystalEnterHandler` (which exceeded the function budget).
 */
internal object CrystalEnterBrackets {
    /**
     * Enter between matching braces/brackets (`{ | }`, `[ | ]`), or after an
     * opener with no closer on the next line (fallback: indent one level deeper).
     */
    fun handleBraceOrBracketEnter(ctx: CrystalEnterHandler.EnterContext): EnterHandlerDelegate.Result {
        val document = ctx.document
        if (ctx.caretLine < document.lineCount) {
            splitAroundClosingBracket(ctx)?.let { return it }
        }

        // Fallback: previous line ends with { or [ but closing brace is not on next line
        // Just indent the cursor one level deeper
        return ctx.indentCurrentLine("${ctx.prevIndent}  ")
    }

    /**
     * Caret between an opener line and a closing-bracket line: indent the cursor
     * line and move the closer down to the opener's indent. Null when the next
     * line does not start with a closer.
     */
    private fun splitAroundClosingBracket(ctx: CrystalEnterHandler.EnterContext): EnterHandlerDelegate.Result? {
        val document = ctx.document
        val nextLineStart = document.getLineStartOffset(ctx.caretLine)
        val nextLineEnd = document.getLineEndOffset(ctx.caretLine)
        val nextTrimmed = document.getText(TextRange(nextLineStart, nextLineEnd)).trimStart()
        if (!nextTrimmed.startsWith("}") && !nextTrimmed.startsWith("]")) return null
        // We're between matching braces — indent cursor and move close brace down
        // Use opener line's indent (not the [ line) so ] aligns with a = [...]
        val baseIndent =
            findOpeningBracketIndent(document, ctx.prevLineNumber)
                ?: ctx.prevLineText.takeWhile { it == ' ' || it == '\t' }
        val newIndent = "$baseIndent  "

        // Replace current line (which has the closing brace) with:
        // - indented cursor line
        // - closing brace on its own line with opener indent
        document.replaceString(nextLineStart, nextLineEnd, "$newIndent\n$baseIndent$nextTrimmed")
        ctx.editor.caretModel.moveToOffset(nextLineStart + newIndent.length)
        return EnterHandlerDelegate.Result.Stop
    }

    /**
     * Closing bracket/brace typed on its own line inside a collection:
     * align it with the opener (variable name), not the previous element.
     */
    fun handleClosingBracketLine(ctx: CrystalEnterHandler.EnterContext): EnterHandlerDelegate.Result? {
        val document = ctx.document
        val caretLineText =
            document.getText(TextRange(document.getLineStartOffset(ctx.caretLine), document.getLineEndOffset(ctx.caretLine)))
        val caretLineTrimmed = caretLineText.trimStart()
        if (!isClosingBracketLine(caretLineTrimmed) || !isInsideUnclosedBracket(document, ctx.prevLineNumber)) {
            return null
        }
        val baseIndent = findOpeningBracketIndent(document, ctx.prevLineNumber) ?: ""
        val currentLineStart = document.getLineStartOffset(ctx.caretLine)
        val currentLineEnd = document.getLineEndOffset(ctx.caretLine)
        document.replaceString(currentLineStart, currentLineEnd, "$baseIndent$caretLineTrimmed")
        ctx.editor.caretModel.moveToOffset(currentLineStart + baseIndent.length)
        return EnterHandlerDelegate.Result.Stop
    }

    /** `]`/`}` opener on its own line. */
    private fun isClosingBracketLine(trimmed: String): Boolean = trimmed.startsWith("]") || trimmed.startsWith("}")

    /**
     * Check if the caret is inside an unclosed `[` or `{` by scanning backwards.
     */
    fun isInsideUnclosedBracket(
        document: Document,
        currentLine: Int,
    ): Boolean = scanBrackets(document, currentLine) { _, _ -> true } ?: false

    /**
     * Scan backwards to find the unclosed `[` or `{` and return its line's indentation.
     */
    private fun findOpeningBracketIndent(
        document: Document,
        currentLine: Int,
    ): String? =
        scanBrackets(document, currentLine) { text, _ ->
            text.takeWhile { it == ' ' || it == '\t' }
        }

    /**
     * Shared backwards bracket scan for [isInsideUnclosedBracket],
     * [findOpeningBracketIndent] and [findFirstElementIndent]: walks lines bottom-up
     * tracking `]`/`}` closers, and applies [onUnclosed] at the first unmatched
     * `[`/`{`. Returns null when every bracket is closed.
     */
    private fun <T> scanBrackets(
        document: Document,
        currentLine: Int,
        onUnclosed: (lineText: String, bracketIndex: Int) -> T,
    ): T? {
        val closers = BracketClosers()
        for (line in currentLine downTo 0) {
            val text = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            val trimmed = text.trimEnd()
            for (i in (trimmed.length - 1) downTo 0) {
                scanBracketChar(trimmed, i, closers, onUnclosed)?.let { return it }
            }
        }
        return null
    }

    /** Open/close counts while scanning a line right-to-left. */
    private class BracketClosers {
        var square = 0
        var curly = 0
    }

    /** One char: closers counted, unmatched openers fire [onUnclosed]. */
    private fun <T> scanBracketChar(
        trimmed: String,
        i: Int,
        closers: BracketClosers,
        onUnclosed: (lineText: String, bracketIndex: Int) -> T,
    ): T? =
        when (trimmed[i]) {
            ']' -> {
                closers.square++
                null
            }

            '[' -> {
                if (closers.square > 0) {
                    closers.square--
                    null
                } else {
                    onUnclosed(trimmed, i)
                }
            }

            '}' -> {
                closers.curly++
                null
            }

            '{' -> {
                if (closers.curly > 0) {
                    closers.curly--
                    null
                } else {
                    onUnclosed(trimmed, i)
                }
            }

            else -> {
                null
            }
        }

    /**
     * Find the unclosed `[` or `{` and return the indentation string that aligns
     * with the first element after the bracket.
     * For `a = [1, 2, 3]` returns "     " (5 spaces, column of `1`).
     * For `a = [\n  1,` returns "  " (2 spaces, column of `1` on next line).
     */
    fun findFirstElementIndent(
        document: Document,
        currentLine: Int,
    ): String? =
        scanBrackets(document, currentLine) { text, i ->
            firstElementIndentAfterBracket(document, text, i)
        }

    /**
     * Indentation of the first collection element after an unclosed bracket:
     * same-line content wins, otherwise the next line's indent, otherwise the
     * opener line's indent plus one level.
     *
     * The bracket's own line number is recovered by matching [lineText] content,
     * so the shared [scanBrackets] callback stays line-agnostic.
     */
    private fun firstElementIndentAfterBracket(
        document: Document,
        lineText: String,
        bracketIndex: Int,
    ): String {
        val trimmed = lineText.trimEnd()
        // First element is on the same line as [
        val restOfLine = trimmed.substring(bracketIndex + 1).trimStart()
        if (restOfLine.isNotEmpty() && !restOfLine.startsWith("\n")) {
            val leadingWs = trimmed.substring(bracketIndex + 1).length - restOfLine.length
            return " ".repeat(bracketIndex + 1 + leadingWs)
        }
        // First element is on the next line — use that line's indentation
        return nextLineIndent(document, lineText)
            ?: (lineText.takeWhile { it == ' ' || it == '\t' } + "  ")
    }

    /**
     * Indentation of the document line right after the one whose text is [lineText],
     * or null when [lineText] is the last line.
     */
    private fun nextLineIndent(
        document: Document,
        lineText: String,
    ): String? {
        for (candidate in 0 until document.lineCount - 1) {
            val start = document.getLineStartOffset(candidate)
            val end = document.getLineEndOffset(candidate)
            if (document.getText(TextRange(start, end)) != lineText) continue
            val nextText = document.getText(TextRange(document.getLineStartOffset(candidate + 1), document.getLineEndOffset(candidate + 1)))
            val nextIndentLen = nextText.length - nextText.trimStart().length
            return nextText.substring(0, nextIndentLen)
        }
        return null
    }
}
