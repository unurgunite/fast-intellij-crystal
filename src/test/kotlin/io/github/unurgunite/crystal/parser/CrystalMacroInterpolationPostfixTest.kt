package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A macro interpolation may end with a postfix modifier: `{{ " : x".id if type }}`.
 * `expression` carries no postfix modifier on its own (statements add one), so
 * without the suffix in `macro_interpolation` the whole rule failed to match and
 * the macro body stopped at the newline before it.
 */
class CrystalMacroInterpolationPostfixTest : BasePlatformTestCase() {
    private fun assertParses(code: String) {
        val file = myFixture.configureByText("a.cr", code)
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertEmpty(errors.joinToString { it.errorDescription }, errors)
    }

    fun testCallWithPostfixIf() = assertParses("macro m\n  {{ foo(1) if type }}\nend\n")

    fun testDotCallWithPostfixIf() = assertParses("macro m\n  {{ \" : x\".id if type }}\nend\n")

    fun testPostfixIfAfterAnotherInterpolation() = assertParses("macro m\n  property? {{ name }}{{ \" : x\".id if type }} = 1\nend\n")

    fun testPlainInterpolationStillParses() = assertParses("macro m\n  {{ name }}\nend\n")

    fun testInterpolationInsideMacroControl() {
        assertParses(
            """
            macro properties(&block)
              {% for kv in [1] %}
                {% if type == Bool %}
                  property? {{ name }}{{ " : x".id if type }} = 1
                {% end %}
              {% end %}
            end
            """.trimIndent() + "\n",
        )
    }
}
