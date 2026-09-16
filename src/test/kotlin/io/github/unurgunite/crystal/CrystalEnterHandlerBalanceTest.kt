package io.github.unurgunite.crystal

import io.github.unurgunite.crystal.editor.isWordChar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the block-balance logic used by CrystalEnterHandler.
 * Tests the word tokenizer and balance counting independently.
 */
class CrystalEnterHandlerBalanceTest {
    // Replicates the balance logic from CrystalEnterHandler
    private val openerKeywords =
        setOf(
            "def",
            "class",
            "module",
            "struct",
            "enum",
            "if",
            "unless",
            "while",
            "until",
            "case",
            "begin",
            "do",
            "macro",
            "lib",
            "fun",
            "annotation",
            "for",
        )

    private fun tokenizeWords(text: String): List<String> {
        val words = mutableListOf<String>()
        val state = TokenizerState(text)
        while (state.pos < state.len) {
            advanceTokenizer(state, words)
        }
        return words
    }

    /** Mutable cursor for [tokenizeWords]; [pos] advances past consumed characters. */
    private class TokenizerState(
        val text: String,
    ) {
        val len: Int = text.length
        var pos: Int = 0

        /** Consume a quoted span (`"..."` or `'...'`), honouring backslash escapes. */
        fun skipQuoted(quote: Char) {
            pos++ // opening quote
            while (pos < len && text[pos] != quote) {
                if (text[pos] == '\\') pos++
                pos++
            }
            if (pos < len) pos++ // closing quote
        }

        /** Consume a word starting at [pos] into [words]. */
        fun consumeWord(words: MutableList<String>) {
            val start = pos
            while (pos < len && isWordChar(text[pos])) pos++
            words.add(text.substring(start, pos))
        }

        /** Skip to end of line (comment). */
        fun skipComment() {
            while (pos < len && text[pos] != '\n') pos++
        }
    }

    /** Advances [state] by one token: comment, quoted span, word, or single char. */
    private fun advanceTokenizer(
        state: TokenizerState,
        words: MutableList<String>,
    ) {
        val text = state.text
        when {
            text[state.pos] == '#' -> state.skipComment()
            text[state.pos] == '"' || text[state.pos] == '\'' -> state.skipQuoted(text[state.pos])
            text[state.pos].isLetter() || text[state.pos] == '_' -> state.consumeWord(words)
            else -> state.pos++
        }
    }

    private fun isBalanced(text: String): Boolean {
        var depth = 0
        for (word in tokenizeWords(text)) {
            when {
                word in openerKeywords -> depth++
                word == "end" -> depth--
            }
        }
        return depth <= 0
    }

    @Test
    fun testEmptyTextIsBalanced() {
        assertTrue(isBalanced(""))
    }

    @Test
    fun testSingleEndIsBalanced() {
        assertTrue(isBalanced("\nend"))
    }

    @Test
    fun testNoEndIsUnbalanced() {
        // Simulates: after "def foo\n", there's code but no end
        assertFalse(isBalanced("\n  def bar\n    puts 'hello'\n"))
    }

    @Test
    fun testMatchedIfEnd() {
        assertTrue(isBalanced("\n  if x\n    y\n  end\nend"))
    }

    @Test
    fun testNestedBlocksBalanced() {
        val text =
            """
            def foo
              if x
                puts x
              end
            end
            """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testNestedBlocksMissingOuterEnd() {
        val text =
            """
            def foo
              if x
                puts x
              end
            """.trimIndent()
        assertFalse(isBalanced(text))
    }

    @Test
    fun testKeywordsInStringsIgnored() {
        // "if" inside string should not count
        val text =
            """
            puts "if this end"
            end
            """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testKeywordsInCommentsIgnored() {
        val text =
            """
            # def another_method
            end
            """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testDoBlockBalanced() {
        val text =
            """
            items.each do |item|
              puts item
            end
            """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testDoBlockMissingEnd() {
        val text =
            """
            items.each do |item|
              puts item
            """.trimIndent()
        assertFalse(isBalanced(text))
    }

    @Test
    fun testSpecStyleBalanced() {
        val text =
            """
            describe App do
              it "works" do
                true.should eq(true)
              end
            end
            """.trimIndent()
        assertTrue(isBalanced(text))
    }
}
