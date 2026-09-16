package io.github.unurgunite.crystal.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * Block-keyword Enter handling: `def`/`if`/`do`/... openers (append a matching
 * `end` unless balanced) and electric dedent for `else`/`end`/`when`/....
 * Split out of `CrystalEnterHandler` (which exceeded the function budget).
 */
internal object CrystalEnterBlocks {
    // Keywords that should be dedented to match their opening block keyword
    private val DEDENT_KEYWORDS = setOf("else", "elsif", "end", "when", "ensure", "rescue", "in")

    /** Block keywords that can open a block as a line's first word. */
    private val LINE_BLOCK_OPENERS =
        setOf(
            "if",
            "unless",
            "case",
            "while",
            "until",
            "begin",
            "def",
            "class",
            "module",
            "struct",
            "enum",
            "lib",
            "do",
            "for",
            "macro",
            "select",
            "fun",
            "annotation",
        )

    /** Mid-block keywords at the same level as the opener (`else` pairs with `if`). */
    private val MID_BLOCK_KEYWORDS = setOf("else", "elsif", "when", "in", "ensure", "rescue")

    /**
     * Block opener (`def`/`if`/`do`/...) at end of line: indent the new line and
     * append a matching `end` below unless the document is already balanced.
     */
    fun handleBlockOpenerEnter(
        ctx: CrystalEnterHandler.EnterContext,
        trimmed: String,
    ): EnterHandlerDelegate.Result {
        val (baseIndent, newIndent) = blockIndents(trimmed, ctx.prevIndent)

        val document = ctx.document
        ctx.indentCurrentLine(newIndent, moveCaret = true)

        // Check if 'end' already exists below the cursor at the same indent level.
        val updatedCaretLine = document.getLineNumber(ctx.editor.caretModel.offset)
        if (hasEndBelow(document, updatedCaretLine, baseIndent)) return EnterHandlerDelegate.Result.Stop

        // Balance check: scan the entire document to decide if 'end' is needed
        if (CrystalBalanceTokens.isDocumentBalanced(document.text)) return EnterHandlerDelegate.Result.Stop

        // Insert 'end' on a new line below the cursor, aligned with the block keyword
        val updatedLineEnd = document.getLineEndOffset(updatedCaretLine)
        document.insertString(updatedLineEnd, "\n${baseIndent}end")

        return EnterHandlerDelegate.Result.Stop
    }

    /**
     * Indent pair for a block opener: body indent and `end` indent. For
     * `a = if expr` patterns both align with the keyword column, not line start.
     */
    private fun blockIndents(
        trimmed: String,
        lineIndent: String,
    ): Pair<String, String> {
        val assignMatch = CrystalBlockOpenerDetect.ASSIGN_KEYWORD_PATTERN.find(trimmed)
        if (assignMatch != null) {
            // Find the keyword column in the trimmed line
            val kw = assignMatch.groupValues[1]
            val kwCol = trimmed.indexOf(kw, assignMatch.range.first)
            // end aligns with keyword; body = keyword + 2
            return Pair(" ".repeat(kwCol), " ".repeat(kwCol + 2))
        }
        return Pair(lineIndent, "$lineIndent  ")
    }

    /**
     * True when a matching `end` already sits below [fromLine] at [baseIndent].
     * Stops early at another same-level opener (its `end` is not ours) or at
     * content dedented below the block (we left the block — no end found).
     */
    private fun hasEndBelow(
        document: Document,
        fromLine: Int,
        baseIndent: String,
    ): Boolean {
        val totalLines = document.lineCount
        for (line in (fromLine + 1) until totalLines) {
            scanEndLine(document, line, baseIndent)?.let { return it }
        }
        return false
    }

    /**
     * One line of the end-scan: true = matching `end` found, false = stop
     * (another opener or dedented content), null = keep scanning.
     */
    private fun scanEndLine(
        document: Document,
        line: Int,
        baseIndent: String,
    ): Boolean? {
        val lineText = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
        val lineTrimmed = lineText.trim()
        if (lineTrimmed.isEmpty()) return null
        val lineIndent = lineText.takeWhile { it == ' ' || it == '\t' }
        if (lineIndent == baseIndent && CrystalBlockOpenerDetect.endsWithBlockOpener(lineTrimmed)) return false
        if (lineTrimmed == "end" && lineIndent == baseIndent) return true
        if (lineIndent.length < baseIndent.length) return false
        return null
    }

    /**
     * Electric dedent for `else`/`elsif`/`end`/`when`/`ensure`/`rescue`/`in`:
     * fix the previous line's indent, then indent the new line one level deeper
     * (or to the same level for a closing `end`).
     */
    fun handleDedentKeyword(ctx: CrystalEnterHandler.EnterContext): EnterHandlerDelegate.Result? {
        val prevTrimmedStart = ctx.prevLineText.trimStart()
        val dedentKeyword =
            DEDENT_KEYWORDS.find { kw ->
                prevTrimmedStart == kw || prevTrimmedStart.startsWith("$kw ") || prevTrimmedStart.startsWith("$kw\t")
            } ?: return null
        fixPreviousLineIndent(ctx)
        // For dedent keywords that also open a sub-block (else, elsif, rescue, ensure, when, in),
        // we still want to indent the new line one level deeper
        val (updatedCtx, baseIndent) = refreshedContext(ctx)
        val newIndent = if (dedentKeyword != "end") "$baseIndent  " else baseIndent
        return updatedCtx.indentCurrentLine(newIndent)
    }

    /** Fixes the previous line's indent to the opening block's indent (caret shifts along). */
    private fun fixPreviousLineIndent(ctx: CrystalEnterHandler.EnterContext) {
        val prevLineStart = ctx.document.getLineStartOffset(ctx.prevLineNumber)
        val currentIndentLen = ctx.prevLineText.length - ctx.prevLineText.trimStart().length
        val expectedIndent = findOpeningBlockIndent(ctx.document, ctx.prevLineNumber) ?: return
        val currentIndent = ctx.prevLineText.substring(0, currentIndentLen)
        if (currentIndent == expectedIndent) return
        // Fix the previous line's indentation
        ctx.document.replaceString(prevLineStart, prevLineStart + currentIndentLen, expectedIndent)
        val delta = expectedIndent.length - currentIndentLen
        // Also fix caret line position (it shifted)
        ctx.editor.caretModel.moveToOffset(ctx.editor.caretModel.offset + delta)
    }

    /** Context re-read after an edit, plus the previous line's indent. */
    private fun refreshedContext(ctx: CrystalEnterHandler.EnterContext): Pair<CrystalEnterHandler.EnterContext, String> {
        val updatedCaretOffset = ctx.editor.caretModel.offset
        val updatedCaretLine = ctx.document.getLineNumber(updatedCaretOffset)
        val updatedPrevLineStart = ctx.document.getLineStartOffset(updatedCaretLine - 1)
        val updatedPrevLineEnd = ctx.document.getLineEndOffset(updatedCaretLine - 1)
        val updatedPrevLineText = ctx.document.getText(TextRange(updatedPrevLineStart, updatedPrevLineEnd))
        val baseIndent = updatedPrevLineText.takeWhile { it == ' ' || it == '\t' }
        return Pair(ctx.copy(caretLine = updatedCaretLine), baseIndent)
    }

    /**
     * Scans backwards from the current line to find the opening block keyword
     * and returns its indentation string.
     */
    fun findOpeningBlockIndent(
        document: Document,
        currentLine: Int,
    ): String? {
        val scanner = BlockIndentScanner()
        for (line in (currentLine - 1) downTo 0) {
            scanner.visitLine(document, line)?.let { return it }
        }
        return null
    }

    /** Backwards opener search with depth tracking across nested `end`s. */
    private class BlockIndentScanner {
        private var depth = 0

        /** One line: null = keep scanning, indent = opener found. */
        fun visitLine(
            document: Document,
            line: Int,
        ): String? {
            val (firstWord, trimmed, text) = CrystalEnterHandler.lineParts(document, line)
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
            val isOpener = firstWord in LINE_BLOCK_OPENERS || CrystalBlockOpenerDetect.endsWithBlockOpener(trimmed.trimEnd())
            when {
                firstWord == "end" -> depth++
                depth > 0 && isOpener -> depth--
                depth == 0 && firstWord !in MID_BLOCK_KEYWORDS && isOpener -> return indentAtKeywordColumn(text, trimmed)
            }
            return null
        }
    }

    /**
     * Indentation for a block body: the opener line's indent, except for
     * `var = if expr` patterns where `end` aligns with the keyword column.
     */
    private fun indentAtKeywordColumn(
        text: String,
        trimmed: String,
    ): String {
        val lineIndent = text.substring(0, text.length - text.trimStart().length)
        // For "var = if expr" patterns, return indent at keyword position
        val assignMatch = CrystalBlockOpenerDetect.ASSIGN_KEYWORD_PATTERN.find(trimmed) ?: return lineIndent
        val kw = assignMatch.groupValues[1]
        val kwCol = trimmed.indexOf(kw, assignMatch.range.first)
        return " ".repeat(lineIndent.length + kwCol)
    }
}
