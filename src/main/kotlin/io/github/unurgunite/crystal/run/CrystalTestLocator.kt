package io.github.unurgunite.crystal.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope

/**
 * Locates Crystal spec test sources from file:line references in test output.
 * Enables clicking on failure locations or double-clicking on any test to navigate to source.
 *
 * URL format: crystal_spec://file_path:line_number
 */
class CrystalTestLocator : SMTestLocator {
    companion object {
        const val PROTOCOL = "crystal_spec"
        val INSTANCE = CrystalTestLocator()
    }

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope,
    ): List<Location<*>> {
        // Path format: "file_path:line_number"
        val (filePath, line) = parsePath(protocol, path) ?: return emptyList()

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return emptyList()

        // Navigate to the specific line (1-based line number → 0-based offset).
        // Clamp out-of-range lines to the file instead of throwing.
        val element = findElementAtLine(virtualFile, psiFile, line)
        return listOf(if (element != null) PsiLocation(element) else PsiLocation(psiFile))
    }

    /**
     * Splits a `file_path:line_number` reference, or null when the protocol
     * or format does not match. Unparseable line numbers mean line 0 (the file).
     */
    private fun parsePath(
        protocol: String,
        path: String,
    ): Pair<String, Int>? {
        if (protocol != PROTOCOL) return null
        val lastColon = path.lastIndexOf(':')
        if (lastColon < 0) return null
        val filePath = path.substring(0, lastColon)
        val line = path.substring(lastColon + 1).toIntOrNull() ?: 0
        return filePath to line
    }

    private fun findElementAtLine(
        virtualFile: VirtualFile,
        psiFile: PsiFile,
        line: Int,
    ): PsiElement? {
        if (line <= 0) return null
        val document: Document? = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (document == null || document.lineCount <= 0) return null
        val safeLine = line.coerceIn(1, document.lineCount)
        val offset = document.getLineStartOffset(safeLine - 1)
        return psiFile.findElementAt(offset)
    }
}
