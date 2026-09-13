package io.github.unurgunite.crystal.highlighting

import junit.framework.TestCase

class CrystalColorSettingsPageTest : TestCase() {

    private val page = CrystalColorSettingsPage()

    fun testIdentity() {
        assertEquals("Crystal", page.displayName)
        assertNotNull(page.icon)
        assertTrue(page.highlighter is CrystalSyntaxHighlighter)
    }

    fun testDemoTextMentionsCoreConstructs() {
        val demo = page.demoText
        assertTrue(demo.isNotBlank())
        for (keyword in listOf("class", "def", "end")) {
            assertTrue("Demo text should contain '$keyword'", keyword in demo)
        }
    }

    fun testAttributeDescriptorsUniqueAndNonEmpty() {
        val descriptors = page.attributeDescriptors
        assertTrue(descriptors.isNotEmpty())
        val names = descriptors.map { it.displayName }
        assertEquals("Duplicate attribute names: $names", names.size, names.toSet().size)
        for (name in names) {
            assertTrue(name.isNotBlank())
        }
    }

    fun testNoColorDescriptors() {
        assertEquals(0, page.colorDescriptors.size)
    }
}
