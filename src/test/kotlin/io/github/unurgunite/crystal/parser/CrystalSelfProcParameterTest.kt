package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A parameter may be typed as a bodyless block receiving the receiver itself:
 * `def initialize(source, &@on_call : V::Call, self ->)`. `self` is a
 * parameter-name keyword and `->` is not, so the generic named alternatives
 * committed on `self` and left the arrow unparsed.
 */
class CrystalSelfProcParameterTest : BasePlatformTestCase() {
    private fun assertParses(code: String) {
        val file = myFixture.configureByText("a.cr", code)
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertEmpty(errors.joinToString { it.errorDescription }, errors)
    }

    fun testBodylessSelfBlockParameter() = assertParses("def m(self ->)\n  x\nend\n")

    fun testBodylessSelfBlockParameterWithReturnType() = assertParses("def m(self -> self)\n  x\nend\n")

    fun testBodylessSelfBlockParameterAfterTypedBlockParameter() {
        assertParses("def initialize(source, &@on_call : V::Call, self ->)\n  source.ast.accept(self)\nend\n")
    }

    fun testBodylessSelfBlockAsParameterType() = assertParses("def m(f : self ->)\n  x\nend\n")

    fun testPlainSelfTypeStillParses() = assertParses("def m(x : self)\n  x\nend\n")

    fun testSelfNothing() = assertParses("def m(self)\n  x\nend\n")
}
