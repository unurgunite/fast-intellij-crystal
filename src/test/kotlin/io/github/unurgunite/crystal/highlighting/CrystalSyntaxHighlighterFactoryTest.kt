package io.github.unurgunite.crystal.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalSyntaxHighlighterFactoryTest : BasePlatformTestCase() {
    fun testCreatesHighlighter() {
        val factory = CrystalSyntaxHighlighterFactory()
        val highlighter = factory.getSyntaxHighlighter(project, null)
        assertTrue(highlighter is CrystalSyntaxHighlighter)
        assertNotNull(highlighter.highlightingLexer)
    }
}
