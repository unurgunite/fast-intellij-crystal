package io.github.unurgunite.crystal.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * Heredoc Enter handling: indent after a `<<-IDENTIFIER` start (inserting the
 * closing delimiter unless present) and dedent after the end delimiter.
 * Split out of `CrystalEnterHandler` (which exceeded the function budget).
 */
internal object CrystalEnterHeredoc {
    /**
     * Heredoc start (`<<-IDENTIFIER` / `<<-'IDENTIFIER'`): indent the new line and
     * insert the closing delimiter below unless it already exists.
     */
    fun handleHeredocStart(
        ctx: CrystalEnterHandler.EnterContext,
        trimmed: String,
    ): EnterHandlerDelegate.Result? {
        val heredocDelimiter = extractHeredocDelimiter(trimmed) ?: return null
        val lineIndent = ctx.prevIndent
        val newIndent = "$lineIndent  "
        val document = ctx.document
        val currentLineStart = document.getLineStartOffset(ctx.caretLine)
        val currentLineEnd = document.getLineEndOffset(ctx.caretLine)
        val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
        val currentLineContent = currentLineText.trimStart()

        if (!hasDelimiterBelow(document, ctx.caretLine, heredocDelimiter)) {
            // End delimiter indented +2 spaces (like 'end' in 'def')
            document.replaceString(
                currentLineStart,
                currentLineEnd,
                "$newIndent$currentLineContent\n$lineIndent  $heredocDelimiter",
            )
        } else {
            document.replaceString(currentLineStart, currentLineEnd, "$newIndent$currentLineContent")
        }
        ctx.editor.caretModel.moveToOffset(currentLineStart + newIndent.length)
        return EnterHandlerDelegate.Result.Stop
    }

    /** True when the delimiter already sits on a line below [fromLine]. */
    private fun hasDelimiterBelow(
        document: Document,
        fromLine: Int,
        heredocDelimiter: String,
    ): Boolean {
        val totalLines = document.lineCount
        for (line in (fromLine + 1) until totalLines) {
            val lineText = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            if (lineText.trim() == heredocDelimiter) return true
        }
        return false
    }

    /**
     * Extract the heredoc delimiter name if the line contains a heredoc start.
     * Supports <<-IDENTIFIER and <<-'IDENTIFIER'.
     * The returned delimiter is always unquoted (e.g., "TEXT" for <<-'TEXT').
     */
    private fun extractHeredocDelimiter(trimmed: String): String? {
        // Pattern for <<-'IDENTIFIER' (quoted)
        val quotedMatch = Regex("""<<-'([A-Za-z_][A-Za-z0-9_]*)'""").find(trimmed)
        if (quotedMatch != null) {
            return quotedMatch.groupValues[1]
        }
        // Pattern for <<-IDENTIFIER (unquoted)
        val unquotedMatch = Regex("""<<-([A-Za-z_][A-Za-z0-9_]*)""").find(trimmed)
        if (unquotedMatch != null) {
            return unquotedMatch.groupValues[1]
        }
        return null
    }

    /**
     * Heredoc end delimiter on the previous line: dedent the new line to match
     * the heredoc start indent.
     */
    fun handleHeredocEnd(
        ctx: CrystalEnterHandler.EnterContext,
        trimmed: String,
    ): EnterHandlerDelegate.Result? {
        val heredocStartIndent = findHeredocStartIndent(ctx.document, ctx.prevLineNumber, trimmed) ?: return null
        val lineStart = ctx.document.getLineStartOffset(ctx.caretLine)
        val lineEnd = ctx.document.getLineEndOffset(ctx.caretLine)
        val content = ctx.document.getText(TextRange(lineStart, lineEnd)).trimStart()
        ctx.document.replaceString(lineStart, lineEnd, "$heredocStartIndent$content")
        ctx.editor.caretModel.moveToOffset(lineStart + heredocStartIndent.length)
        return EnterHandlerDelegate.Result.Stop
    }

    /**
     * Check if the given line is a heredoc end delimiter, and if so,
     * find the indentation of the matching heredoc start line.
     * Returns null if the line is not a heredoc end delimiter.
     */
    private fun findHeredocStartIndent(
        document: Document,
        endLine: Int,
        endText: String,
    ): String? {
        val endTrimmed = endText.trim()
        // Must be a single identifier (heredoc end delimiter)
        if (!Regex("""^[A-Za-z_][A-Za-z0-9_]*$""").matches(endTrimmed)) return null
        // Must be indented (not at column 0) — otherwise it's just a normal identifier
        val endIndent = endText.takeWhile { it == ' ' || it == '\t' }
        if (endIndent.isEmpty()) return null

        // Scan backwards to find <<-IDENTIFIER or <<-'IDENTIFIER'
        for (line in (endLine - 1) downTo 0) {
            findHeredocStartOnLine(document, line, endTrimmed)?.let { return it }
        }
        return null
    }

    /** Start-line indent when line [line] opens [delimiter], else null. */
    private fun findHeredocStartOnLine(
        document: Document,
        line: Int,
        delimiter: String,
    ): String? {
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val text = document.getText(TextRange(lineStart, lineEnd))
        val trimmed = text.trimEnd()

        val quotedMatch = Regex("""<<-'([A-Za-z_][A-Za-z0-9_]*)'""").find(trimmed)
        if (quotedMatch != null && quotedMatch.groupValues[1] == delimiter) {
            return text.takeWhile { it == ' ' || it == '\t' }
        }

        val unquotedMatch = Regex("""<<-([A-Za-z_][A-Za-z0-9_]*)""").find(trimmed)
        if (unquotedMatch != null && unquotedMatch.groupValues[1] == delimiter) {
            return text.takeWhile { it == ' ' || it == '\t' }
        }
        return null
    }
}
