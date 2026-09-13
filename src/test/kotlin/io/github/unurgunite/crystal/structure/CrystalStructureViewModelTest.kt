package io.github.unurgunite.crystal.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalStructureViewModelTest : BasePlatformTestCase() {

    private fun model(): CrystalStructureViewModel {
        val file = myFixture.configureByText("test.cr", "class Foo\nend\n")
        return CrystalStructureViewModel(file, myFixture.editor)
    }

    fun testSorters() {
        assertTrue(model().sorters.contains(Sorter.ALPHA_SORTER))
    }

    fun testFlags() {
        val model = model()
        assertFalse(model.isAlwaysShowsPlus(null))
        assertFalse(model.isAlwaysLeaf(null))
    }

    fun testRootListsDefinitions() {
        val root: StructureViewTreeElement = model().root
        assertNotNull(root)
        val names = root.children.map { it.presentation.presentableText }
        assertTrue("Foo in $names", "Foo" in names)
    }
}
