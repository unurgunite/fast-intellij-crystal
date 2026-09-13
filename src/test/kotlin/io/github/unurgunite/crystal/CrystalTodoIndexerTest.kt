package io.github.unurgunite.crystal

import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.id.IdDataConsumer
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Runs on BasePlatformTestCase (not a pure unit test): the filter lexer counts
 * TODO items via the application extension point, which requires a test app.
 */
class CrystalTodoIndexerTest : BasePlatformTestCase() {
    /** Indexes text through the real filter+cap lexer stack, returns occurrence masks. */
    private fun indexedMasks(text: String): List<Int> {
        val ids = IdDataConsumer()
        val lexer = CrystalTodoIndexer().createLexer(OccurrenceConsumer(ids, true))
        lexer.start(text)
        while (lexer.tokenType != null) {
            lexer.advance()
        }
        return ids.result.values.toList()
    }

    fun testTodoInCommentIsIndexed() {
        val masks = indexedMasks("# TODO: fix this\nx = 1\n")
        assertFalse("Comment words should be indexed, got nothing", masks.isEmpty())
        val inComments = UsageSearchContext.IN_COMMENTS.toInt()
        assertTrue(
            "All indexed words must carry the IN_COMMENTS bit, got: $masks",
            masks.all { it and inComments != 0 },
        )
    }

    fun testPlainCodeHasNoTodo() {
        assertTrue(indexedMasks("x = 1\ny = 2\n").isEmpty())
    }

    fun testTodoInStringIsNotIndexed() {
        // Only comments are reported by the filter lexer — string content is skipped.
        val masks = indexedMasks("x = \"TODO: not a task\"\n")
        assertTrue("String content must not be indexed, got: $masks", masks.isEmpty())
    }

    fun testLexerTerminatesOnPathologicalInput() {
        // Long unterminated construct: the cap lexer must still terminate
        val text = "\"" + "a".repeat(5000)
        val ids = IdDataConsumer()
        val lexer = CrystalTodoIndexer().createLexer(OccurrenceConsumer(ids, true))
        lexer.start(text)
        var steps = 0
        while (lexer.tokenType != null && steps < 20000) {
            lexer.advance()
            steps++
        }
        assertTrue("Lexer must terminate, took $steps steps", lexer.tokenType == null)
    }
}
