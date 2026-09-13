package io.github.unurgunite.crystal

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalQuoteHandlerTest : BasePlatformTestCase() {

    private val handler = CrystalQuoteHandler()

    private fun iteratorAt(code: String, caretMarker: String = "<caret>"): com.intellij.openapi.editor.highlighter.HighlighterIterator {
        myFixture.configureByText("test.cr", code)
        val offset = myFixture.caretOffset
        val highlighter = (myFixture.editor as EditorEx).highlighter
        val iterator = highlighter.createIterator(offset)
        assertNotNull(iterator)
        return iterator!!
    }

    fun testOpeningDoubleQuote() {
        // Iterator positioned at the opening quote of a string
        val file = myFixture.configureByText("test.cr", "x = \"hello\"\n")
        val offset = file.text.indexOf('"')
        val iterator = (myFixture.editor as EditorEx).highlighter.createIterator(offset)!!
        assertTrue(handler.isOpeningQuote(iterator, offset))
    }

    fun testIdentifierIsNotQuote() {
        val it = iteratorAt("x<caret> = 1\n")
        assertFalse(handler.isOpeningQuote(it, myFixture.caretOffset))
        assertFalse(handler.isClosingQuote(it, myFixture.caretOffset))
    }

    fun testInsideStringLiteral() {
        val it = iteratorAt("x = \"hel<caret>lo\"\n")
        assertTrue(handler.isInsideLiteral(it))
    }

    fun testOutsideStringIsNotInsideLiteral() {
        val it = iteratorAt("x<caret> = 1\n")
        assertFalse(handler.isInsideLiteral(it))
    }
}
