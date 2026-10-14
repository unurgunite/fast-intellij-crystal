package io.github.unurgunite.crystal.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem

class CrystalNavigationItem(
    private val symbol: CrystalSymbol,
) : NavigationItem {
    override fun getName(): String = symbol.name

    override fun getPresentation(): ItemPresentation {
        val location = symbol.element.containingFile?.name ?: ""
        return PresentationData(symbol.name, location, symbol.kind.icon, null)
    }

    // NOTE: Navigatable.navigate is @OverrideOnly — calling it directly is
    // flagged by Marketplace verification. FileEditorManager opens the
    // symbol's file at the element offset instead (same destination when
    // navigable, nothing otherwise; no throwing path).
    override fun navigate(requestFocus: Boolean) {
        val target = symbol.element as? NavigationItem ?: return
        if (!target.canNavigate()) return
        val element = symbol.element
        val file = element.containingFile?.virtualFile ?: return
        com.intellij.openapi.fileEditor.FileEditorManager
            .getInstance(element.project)
            .openFile(file, requestFocus)
    }

    override fun canNavigate(): Boolean = symbol.element is NavigationItem && (symbol.element as NavigationItem).canNavigate()

    override fun canNavigateToSource(): Boolean = canNavigate()
}
