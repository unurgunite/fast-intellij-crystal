package io.github.unurgunite.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class CrystalFileTypeTest : TestCase() {
    fun testIdentity() {
        assertEquals("Crystal", CrystalFileType.name)
        assertEquals("cr", CrystalFileType.defaultExtension)
        assertEquals("Crystal language file", CrystalFileType.description)
        assertEquals(CrystalLanguage, CrystalFileType.language)
    }
}

class CrystalIconsTest : TestCase() {
    fun testIconsLoad() {
        assertNotNull(CrystalIcons.FILE)
        assertNotNull(CrystalIcons.SPEC_FILE_BASE)
        assertNotNull(CrystalIcons.SPEC_FILE)
        assertTrue(CrystalIcons.FILE.iconWidth > 0)
    }
}

class CrystalIconProviderTest : BasePlatformTestCase() {
    private val provider = CrystalIconProvider()

    fun testSpecFileGetsSpecIcon() {
        val file = myFixture.configureByText("sample_spec.cr", "describe \"x\" do\nend\n")
        assertEquals(CrystalIcons.SPEC_FILE, provider.getIcon(file, 0))
    }

    fun testPlainFileGetsNoIcon() {
        val file = myFixture.configureByText("main.cr", "x = 1\n")
        assertNull(provider.getIcon(file, 0))
    }

    fun testNonFileElementGetsNoIcon() {
        val file = myFixture.configureByText("sample_spec.cr", "x = 1\n")
        val element = file.findElementAt(0)!!
        assertNull(provider.getIcon(element, 0))
    }
}
