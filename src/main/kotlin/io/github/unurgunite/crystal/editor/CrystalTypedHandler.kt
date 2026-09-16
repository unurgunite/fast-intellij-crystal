package io.github.unurgunite.crystal.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.CrystalFileType

/**
 * Handles Crystal-specific auto-completion and auto-insertion:
 * - Auto-inserts closing `}` when typing `{` after `#` inside a string (string interpolation).
 * - Triggers auto-completion popup when typing `::` (namespace access).
 */
class CrystalTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (file.fileType != CrystalFileType) return Result.CONTINUE

        val offset = editor.caretModel.offset

        return when (charTyped) {
            ':' -> handleColonTyped(project, editor, offset)
            '@' -> handleAtTyped(project, editor, offset)
            else -> Result.CONTINUE
        }
    }

    /**
     * Trigger auto-popup when typing '::' (namespace access): the second colon
     * must directly follow the first one.
     */
    private fun handleColonTyped(
        project: Project,
        editor: Editor,
        offset: Int,
    ): Result {
        if (offset < 2) return Result.CONTINUE
        if (editor.document.getText(TextRange.create(offset - 1, offset)) != ":") return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    /**
     * Trigger auto-popup when typing '@' (instance/class variable sigil), unless
     * inside a string literal or starting an annotation (`@[`).
     */
    private fun handleAtTyped(
        project: Project,
        editor: Editor,
        offset: Int,
    ): Result {
        if (offset < 1) return Result.CONTINUE
        val document = editor.document
        if (isInsideString(document.text, offset - 1)) return Result.CONTINUE
        if (offset < document.textLength && document.charsSequence[offset] == '[') return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    override fun charTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (c != '{' || file.fileType != CrystalFileType) return Result.CONTINUE

        val offset = editor.caretModel.offset
        if (offset < 2) return Result.CONTINUE

        val document = editor.document
        val text = document.text

        // The character before `{` must be `#`, inside a string, with no `}` already after.
        val readyToClose =
            text[offset - 2] == '#' &&
                isInsideString(text, offset - 2) &&
                !(offset < text.length && text[offset] == '}')
        if (!readyToClose) return Result.CONTINUE

        document.insertString(offset, "}")
        return Result.STOP
    }

    /**
     * Determines if the given position is inside a double-quoted string.
     * Scans backwards counting unescaped `"` characters.
     */
    private fun isInsideString(
        text: String,
        position: Int,
    ): Boolean {
        var quoteCount = 0
        var i = position - 1
        while (i >= 0) {
            if (text[i] == '"') {
                // Check if escaped
                var backslashes = 0
                var j = i - 1
                while (j >= 0 && text[j] == '\\') {
                    backslashes++
                    j--
                }
                if (backslashes % 2 == 0) {
                    quoteCount++
                }
            }
            i--
        }
        // Odd number of unescaped quotes means we're inside a string
        return quoteCount % 2 == 1
    }
}
