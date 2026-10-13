package io.github.unurgunite.crystal.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import io.github.unurgunite.crystal.psi.CrystalTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nested percent literals inside string interpolation
 * (`%([#{x %([#{y}])}])` — the autoreg `app.cr` shape).
 *
 * Before the fix the inner `%(` lexed as `PERCENT LPAREN` (INTERPOLATION had
 * no `%`-family rules) and the inner `#{` fell into `LINE_COMMENT`,
 * swallowing the rest of the line.
 */
class CrystalNestedPercentLexerTest {
    private fun tokenize(text: String): List<Pair<IElementType, String>> {
        val lexer = CrystalLexer(null)
        lexer.reset(text, 0, text.length, CrystalLexer.YYINITIAL)
        val tokens = mutableListOf<Pair<IElementType, String>>()
        var tokenType: IElementType? = lexer.advance()
        while (tokenType != null) {
            tokens.add(tokenType to text.substring(lexer.tokenStart, lexer.tokenEnd))
            tokenType = lexer.advance()
        }
        return tokens
    }

    private fun countTypes(text: String): Map<IElementType, Int> = tokenize(text).groupingBy { it.first }.eachCount()

    @Test
    fun testNestedPercentInInterpolationHasNoComment() {
        val counts = countTypes("%({\"p\":[#{rows.map { |r| %({\"id\":#{r[0]}}) }.join(\",\")}]} )")
        assertEquals(0, counts[CrystalTypes.LINE_COMMENT] ?: 0)
        assertEquals(0, counts[TokenType.BAD_CHARACTER] ?: 0)
    }

    @Test
    fun testNestedPercentOpensLiteralBegin() {
        val counts = countTypes("%({\"p\":[#{rows.map { |r| %({\"id\":#{r[0]}}) }.join(\",\")}]} )")
        assertEquals(2, counts[CrystalTypes.PERCENT_LITERAL_BEGIN])
        assertEquals(2, counts[CrystalTypes.PERCENT_LITERAL_END])
    }

    @Test
    fun testNestedInterpolationOpensTwice() {
        val counts = countTypes("%({\"p\":[#{rows.map { |r| %({\"id\":#{r[0]}}) }.join(\",\")}]} )")
        assertEquals(2, counts[CrystalTypes.STRING_INTERPOLATION_BEGIN])
        assertEquals(2, counts[CrystalTypes.STRING_INTERPOLATION_END])
    }

    @Test
    fun testAllPercentKindsNest() {
        for (kind in listOf("%w", "%i", "%q", "%Q", "%r", "%x")) {
            val counts = countTypes("%([#{x $kind(a b)}])")
            assertTrue(
                "$kind should open a nested literal inside interpolation",
                (counts[CrystalTypes.PERCENT_LITERAL_BEGIN] ?: 0) + (counts[CrystalTypes.PERCENT_SYMBOL_BEGIN] ?: 0) == 2,
            )
            assertEquals(0, counts[CrystalTypes.LINE_COMMENT] ?: 0)
        }
    }

    @Test
    fun testHashCommentStillCommentInInterpolation() {
        val counts = countTypes("\"#{x # comment\n}\"")
        assertEquals(1, counts[CrystalTypes.LINE_COMMENT])
    }

    @Test
    fun testQNoEscapeSurvivesNesting() {
        val types = tokenize("%([#{x %q(a\\))}])").map { it.first }
        assertTrue(types.none { it == CrystalTypes.LINE_COMMENT })
        assertEquals(2, types.count { it == CrystalTypes.PERCENT_LITERAL_BEGIN })
        assertEquals(2, types.count { it == CrystalTypes.PERCENT_LITERAL_END })
    }
}
