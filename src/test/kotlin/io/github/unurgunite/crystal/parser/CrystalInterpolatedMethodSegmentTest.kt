package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A method-name interpolation reached through `&.` (`&.{{ name }}?(...)`): the
 * `&.` form parses through `implicit_object_call`, whose segment list must
 * accept `macro_interpolation` like `dot_call_access` does.
 */
class CrystalInterpolatedMethodSegmentTest : BasePlatformTestCase() {
    private fun assertParses(code: String) {
        val psi = myFixture.configureByText("a.cr", code)
        val errors = PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java)
        assertEquals(errors.joinToString("\n") { it.errorDescription ?: "" }, 0, errors.size)
    }

    fun testInterpolationAfterAmpersandDot() = assertParses("value.try(&.{{ name }})")

    fun testInterpolationAfterAmpersandDotWithArgs() = assertParses("value.try(&.{{ name }}(flag: true))")

    fun testInterpolationAfterAmpersandDotWithQuestion() = assertParses("value.try(&.{{ name }}?(flag: true))")

    fun testInterpolationAfterAmpersandDotWithoutReceiver() = assertParses("&.{{ name }}")

    fun testInterpolationAfterDotWithoutReceiver() = assertParses(".{{ name }}")

    fun testPlainAmpersandDotStillParses() = assertParses("value.try(&.name)")
}
