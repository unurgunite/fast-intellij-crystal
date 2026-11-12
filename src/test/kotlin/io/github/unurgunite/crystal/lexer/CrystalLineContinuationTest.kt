package io.github.unurgunite.crystal.lexer

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A backslash at the end of a line continues the literal onto the next one.
 *
 * JFlex's `.` does not match a line terminator, so the catch-all
 * `"\" .` escape rule could not consume `\` followed by a newline. The
 * backslash was emitted as an error token and the string was split in two,
 * which broke the surrounding expression.
 */
class CrystalLineContinuationTest : BasePlatformTestCase() {
    private fun assertParses(code: String) {
        val file = myFixture.configureByText("a.cr", code)
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertEquals("unexpected parse errors: ${errors.map { it.errorDescription }}", 0, errors.size)
    }

    fun testStringLiteral() {
        assertParses("x = \"abc \\\n  def\"")
    }

    fun testStringWithCrlf() {
        assertParses("x = \"abc \\\r\n  def\"")
    }

    fun testBareCallArgument() {
        assertParses("foo \"abc \\\n  def\"")
    }

    fun testMultiLineDescriptionInDslBlock() {
        assertParses(
            """
            class C
              properties do
                since_version "0.1.0"
                description "Disallows useless statements that contain \
                  a literal in place of a variable"
              end
            end
            """.trimIndent(),
        )
    }

    fun testRegexLiteral() {
        assertParses("x = /abc \\\n  def/")
    }

    fun testCommandLiteral() {
        assertParses("x = `abc \\\n  def`")
    }

    fun testSymbolStringLiteral() {
        assertParses("x = :\"abc \\\n  def\"")
    }

    fun testPercentLiteral() {
        assertParses("x = %(abc \\\n  def)")
    }
}
