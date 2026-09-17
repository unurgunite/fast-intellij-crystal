package io.github.unurgunite.crystal.generation

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInsight.hint.HintManager
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.CrystalFile
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * "Implement Methods" (Code → Generate) for Crystal: generates stubs for
 * `abstract def` methods the enclosing type does not implement yet.
 * Registered via `com.intellij.codeInsight.implementMethod` for the Crystal
 * language. V1 implements all missing members without a chooser dialog.
 */
class CrystalImplementMethodsHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(
        editor: Editor,
        file: PsiFile,
    ): Boolean {
        if (file !is CrystalFile) return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val enclosing = CrystalPsiUtils.getEnclosingType(element) ?: return false
        return classBodyOf(enclosing) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor,
        file: PsiFile,
    ) {
        val target = prepareInvocation(project, editor, file) ?: return
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(target, project)
        if (missing.isEmpty()) {
            HintManager.getInstance().showErrorHint(editor, "No abstract methods to implement")
            return
        }
        val endOffset = endTokenOffset(target) ?: return
        val indent = memberIndent(editor.document, target)
        val text = buildStubsText(missing, typeDisplayName(target), indent, endOffset, editor.document)
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(endOffset, text)
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        }
    }

    /** Editor checks + caret target, or null when the action cannot run here. */
    private fun prepareInvocation(
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): PsiElement? {
        if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return null
        if (!FileDocumentManager.getInstance().requestWriting(editor.document, project)) return null
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return CrystalPsiUtils.getEnclosingType(element)
    }

    override fun startInWriteAction(): Boolean = false

    /**
     * Stub block for every missing method: signature copied verbatim, body
     * raises `NotImplementedError`. Ends with a newline so `end` (already in
     * the document) lands on its own line.
     */
    internal fun buildStubsText(
        missing: List<CrystalAbstractMethodInfo>,
        typeName: String,
        indent: String,
        endOffset: Int,
        document: Document,
    ): String {
        val separator = if (endOnOwnLine(document, endOffset)) "" else "\n"
        val stubs =
            missing.joinToString("\n") { info ->
                // Receiver prefix stripped for the message (`Foo.bar` → `bar`).
                val shortName = info.name.substringAfterLast(".")
                "$indent${stubHeader(info)}\n" +
                    "$indent  raise NotImplementedError.new(\"$typeName#$shortName not implemented\")\n" +
                    "${indent}end"
            }
        return "$separator$stubs\n"
    }

    private fun stubHeader(info: CrystalAbstractMethodInfo): String =
        "def ${info.name}${info.paramsText}${info.returnTypeText}${info.forallText}"

    /** True when only whitespace precedes `end` on its line (the usual shape). */
    private fun endOnOwnLine(
        document: Document,
        endOffset: Int,
    ): Boolean {
        if (endOffset <= 0) return false
        val text = document.charsSequence
        var i = endOffset - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        return i < 0 || text[i] == '\n'
    }

    /** Offset of the type's closing `end` token, or null. */
    private fun endTokenOffset(target: PsiElement): Int? =
        target.node
            .getChildren(null)
            .lastOrNull { it.elementType == CrystalTypes.END }
            ?.textRange
            ?.startOffset

    /** Member indent: enclosing type's own indent plus one level. */
    private fun memberIndent(
        document: Document,
        target: PsiElement,
    ): String {
        val line = document.getLineNumber(target.textRange.startOffset)
        val lineStart = document.getLineStartOffset(line)
        val indent = document.charsSequence.substring(lineStart, target.textRange.startOffset)
        return indent + "  "
    }

    /** Display name of the type for error messages. */
    private fun typeDisplayName(target: PsiElement): String = (target as? CrystalNamedElement)?.name ?: "Type"

    /** Test seam: pure write without editor plumbing. */
    internal fun insertForTest(
        project: Project,
        document: Document,
        target: PsiElement,
        missing: List<CrystalAbstractMethodInfo>,
    ) {
        val endOffset = endTokenOffset(target) ?: return
        val text = buildStubsText(missing, typeDisplayName(target), memberIndent(document, target), endOffset, document)
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(endOffset, text) }
    }
}
