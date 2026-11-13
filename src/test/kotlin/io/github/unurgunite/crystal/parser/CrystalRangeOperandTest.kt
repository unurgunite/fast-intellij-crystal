package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalVariableReference

/**
 * A range operator after an operand is a binary operator, not the start of a
 * bare-command argument list. Without this, `a...b` parsed as the command `a`
 * with argument `...b`, and an open slice index as a command with a `..`
 * argument — so the operands were never variable references and the
 * unused-variable inspection reported live variables as dead.
 */
class CrystalRangeOperandTest : BasePlatformTestCase() {
    fun testRangeBetweenTwoVariables() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def foo\n  a = 1\n  b = 5\n  c = a...b\n  puts c\nend\n",
            )
        assertNoErrors(file)
        assertTrue(
            "both operands must be variable references",
            refNames(file).containsAll(listOf("a", "b")),
        )
    }

    fun testOpenRangeAsSliceIndex() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def foo(dedent)\n  puts line[dedent..]\nend\n",
            )
        assertNoErrors(file)
        assertTrue("slice index must be a read", refNames(file).contains("dedent"))
    }

    fun testOpenRangeWithLeadingDot() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def foo(n)\n  puts line[n..]\nend\n",
            )
        assertNoErrors(file)
    }

    private fun assertNoErrors(file: com.intellij.psi.PsiFile) {
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).toList()
        assertTrue("unexpected parse errors: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    private fun refNames(file: com.intellij.psi.PsiFile): Set<String> =
        PsiTreeUtil
            .findChildrenOfType(file, CrystalVariableReference::class.java)
            .mapNotNull { it.text }
            .toSet()
}
