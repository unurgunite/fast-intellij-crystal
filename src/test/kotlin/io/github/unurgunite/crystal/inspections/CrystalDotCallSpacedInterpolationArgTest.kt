package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList

/**
 * A spaced macro interpolation after a dot-call name is an ARGUMENT, not part of
 * the name. Crystal splices a name only when the parts are glued
 * (`build_{{name}}2`), but the grammar accepted the interpolation at any distance,
 * so `obj.new {{x}}, 1` read `new {{x}}` as one callee name, left the call with no
 * argument list and reported the remaining comma as a parse error.
 */
class CrystalDotCallSpacedInterpolationArgTest : BasePlatformTestCase() {
    private fun errors(code: String): List<String> {
        val psi = myFixture.configureByText("a.cr", code)
        return PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java).map { it.errorDescription ?: "" }
    }

    private fun bareArgumentCount(code: String): Int =
        PsiTreeUtil.findChildrenOfType(myFixture.configureByText("a.cr", code), CrystalBareArgumentList::class.java).size

    fun testSpacedInterpolationIsAnArgument() {
        assertEquals(emptyList<String>(), errors("B.new {{1}}, x\n"))
        assertEquals(1, bareArgumentCount("B.new {{1}}, x\n"))
    }

    fun testSpacedInterpolationAsTheOnlyArgument() {
        assertEquals(emptyList<String>(), errors("B.new {{1}}\n"))
        assertEquals(1, bareArgumentCount("B.new {{1}}\n"))
    }

    fun testSpacedInterpolationWithTrailingArguments() {
        val code = "B.new {{flag?(:w) ? 4 : 2}}, a, false, b.dup\n"
        assertEquals(emptyList<String>(), errors(code))
        assertEquals(1, bareArgumentCount(code))
    }

    fun testGluedInterpolationIsStillPartOfTheName() {
        assertEquals(emptyList<String>(), errors("b.build_{{name}}2(1)\n"))
        assertEquals(0, bareArgumentCount("b.build_{{name}}2(1)\n"))
    }

    fun testGluedInterpolationWithSuffixAndNoArgs() {
        assertEquals(emptyList<String>(), errors("b.build_{{name}}\n"))
        assertEquals(0, bareArgumentCount("b.build_{{name}}\n"))
    }

    fun testGluedInterpolationAsBareCallArgument() {
        assertEquals(emptyList<String>(), errors("Value.new LibLLVM.build_{{ m }}2(self, 1)\n"))
    }
}
