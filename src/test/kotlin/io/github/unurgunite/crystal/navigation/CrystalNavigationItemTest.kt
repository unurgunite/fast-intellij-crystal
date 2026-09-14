package io.github.unurgunite.crystal.navigation

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalClassDefinition

class CrystalNavigationItemTest : BasePlatformTestCase() {
    private fun fooItem(): CrystalNavigationItem {
        val file = myFixture.configureByText("test.cr", "class Foo\nend\n")
        val def = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        return CrystalNavigationItem(CrystalSymbol("Foo", CrystalSymbolKind.CLASS, def))
    }

    fun testNameAndPresentation() {
        val item = fooItem()
        assertEquals("Foo", item.name)
        assertEquals("Foo", item.presentation.presentableText)
        assertEquals("test.cr", item.presentation.locationString)
        assertNotNull(item.presentation.getIcon(false))
    }

    fun testCanNavigateDelegatesToElement() {
        val item = fooItem()
        assertTrue(item.canNavigate())
        assertTrue(item.canNavigateToSource())
    }

    fun testSymbolKindsHaveLabels() {
        for (kind in CrystalSymbolKind.values()) {
            assertTrue(kind.label.isNotBlank())
            assertNotNull(kind.icon)
        }
    }
}
