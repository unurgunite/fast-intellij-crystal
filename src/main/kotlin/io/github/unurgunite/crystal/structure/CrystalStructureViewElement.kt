package io.github.unurgunite.crystal.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAliasDefinition
import io.github.unurgunite.crystal.psi.CrystalAnnotationDefinition
import io.github.unurgunite.crystal.psi.CrystalClassBody
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalConstantAssignment
import io.github.unurgunite.crystal.psi.CrystalEnumBody
import io.github.unurgunite.crystal.psi.CrystalEnumConstant
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalLibDefinition
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVisibilityModifier
import javax.swing.Icon

class CrystalStructureViewElement(
    private val element: PsiElement,
) : StructureViewTreeElement,
    SortableTreeElement {
    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        if (element is NavigationItem) {
            element.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean = element is NavigationItem && element.canNavigate()

    override fun canNavigateToSource(): Boolean = element is NavigationItem && element.canNavigateToSource()

    override fun getAlphaSortKey(): String = presentation.presentableText ?: ""

    override fun getPresentation(): ItemPresentation {
        if (element is PsiFile) {
            return element.presentation ?: PresentationData(element.name, null, null, null)
        }
        val (name, icon, location) = CrystalStructurePresentation.getElementInfo(element)
        return PresentationData(name, location, icon, null)
    }

    override fun getChildren(): Array<TreeElement> {
        val childElements =
            when (element) {
                is PsiFile -> collectTopLevelDefinitions(element)
                is CrystalClassDefinition -> collectClassMembers(element.classBody)
                is CrystalModuleDefinition -> collectClassMembers(element.classBody)
                is CrystalStructDefinition -> collectClassMembers(element.classBody)
                is CrystalEnumDefinition -> collectEnumMembers(element.enumBody)
                else -> emptyList()
            }
        return childElements.map { CrystalStructureViewElement(it) }.toTypedArray()
    }

    private fun collectTopLevelDefinitions(file: PsiFile): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        file.children.forEach { collectDefinitions(it, result) }
        return result
    }

    private fun collectDefinitions(
        element: PsiElement,
        result: MutableList<PsiElement>,
    ) {
        when (element) {
            is CrystalClassDefinition,
            is CrystalModuleDefinition,
            is CrystalStructDefinition,
            is CrystalEnumDefinition,
            is CrystalLibDefinition,
            is CrystalAnnotationDefinition,
            is CrystalMethodDefinition,
            is CrystalMacroDefinition,
            is CrystalAliasDefinition,
            is CrystalConstantAssignment,
            -> {
                result.add(element)
            }

            is CrystalVisibilityModifier -> {
                // Look inside visibility modifier for the actual definition
                element.children.forEach { collectDefinitions(it, result) }
            }

            else -> {
                element.children.forEach { collectDefinitions(it, result) }
            }
        }
    }

    private fun collectClassMembers(classBody: CrystalClassBody?): List<PsiElement> {
        if (classBody == null) return emptyList()
        val result = mutableListOf<PsiElement>()
        classBody.children.forEach { collectDefinitions(it, result) }
        return result
    }

    private fun collectEnumMembers(enumBody: CrystalEnumBody?): List<PsiElement> {
        if (enumBody == null) return emptyList()
        val result = mutableListOf<PsiElement>()
        PsiTreeUtil
            .findChildrenOfAnyType(
                enumBody,
                CrystalEnumConstant::class.java,
                CrystalMethodDefinition::class.java,
            ).forEach { result.add(it) }
        return result
    }
}
