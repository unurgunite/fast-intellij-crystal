package io.github.unurgunite.crystal.structure

import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.CrystalFile

class CrystalStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is CrystalFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return CrystalStructureViewModel(psiFile, editor)
            }
        }
    }
}
