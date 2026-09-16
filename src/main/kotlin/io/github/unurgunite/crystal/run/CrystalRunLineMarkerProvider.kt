package io.github.unurgunite.crystal.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Provides gutter run icons (▶) next to `describe` and `it` blocks in Crystal spec files.
 * Allows running individual tests or test suites directly from the editor.
 */
class CrystalRunLineMarkerProvider : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        // Only trigger on leaf IDENTIFIER tokens (to avoid duplicates on parent nodes)
        if (element !is LeafPsiElement || element.elementType != CrystalTypes.IDENTIFIER) return null
        if (element.parent == null) return null

        // Only in spec files, on `describe` / `it` / `context` calls
        val fileName = element.containingFile?.virtualFile?.name
        if (fileName == null || !fileName.endsWith("_spec.cr")) return null
        if (!isSpecKeyword(element.text)) return null

        val actions = ExecutorAction.getActions(0)
        val text = element.text
        val tooltipProvider = { _: PsiElement ->
            when (text) {
                "describe", "context" -> "Run spec suite"
                "it" -> "Run spec"
                else -> "Run"
            }
        }

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            tooltipProvider,
            *actions,
        )
    }

    private fun isSpecKeyword(text: String): Boolean = text == "describe" || text == "it" || text == "context"
}
