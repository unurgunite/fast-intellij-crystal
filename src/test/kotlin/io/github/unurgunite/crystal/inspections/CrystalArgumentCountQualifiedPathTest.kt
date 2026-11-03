package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalArgumentList
import io.github.unurgunite.crystal.type.CrystalCallArguments

/**
 * A qualified constant path as the first argument (`f(A::B, 2)`) used to be
 * parsed as a bare command with callee `A`: the whole argument list collapsed
 * into one argument, so arity checking saw a single argument and reported
 * "Missing required argument(s)" for the rest.
 */
class CrystalArgumentCountQualifiedPathTest : BasePlatformTestCase() {
    private fun argumentCount(code: String): Int {
        myFixture.configureByText("c.cr", code)
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertEquals("unexpected parse errors: ${errors.map { it.errorDescription }}", 0, errors.size)
        val list = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalArgumentList::class.java).first()
        return CrystalCallArguments.extractArgumentsFromArgsElement(list.parent).size
    }

    fun testQualifiedFirstArgumentIsSeparate() {
        assertEquals(2, argumentCount("f(A::B, 2)\n"))
    }

    fun testQualifiedFirstArgumentWithMore() {
        assertEquals(3, argumentCount("f(A::B, 2, 3)\n"))
    }

    fun testDeepQualifiedFirstArgument() {
        assertEquals(2, argumentCount("f(A::B::C, 2)\n"))
    }

    fun testQualifiedReceiverWithMethodArgument() {
        assertEquals(2, argumentCount("f(A::B.new, 2)\n"))
    }

    fun testPlainArgumentsUnchanged() {
        assertEquals(2, argumentCount("f(a, 2)\n"))
        assertEquals(2, argumentCount("f(A, 2)\n"))
    }

    /** A bare command keeps its `::`-led argument: `expect_raises ::Spec::Failure, "m"`. */
    fun testBareCommandKeepsLeadingColonColonArgument() {
        myFixture.configureByText("c.cr", "expect_raises ::Spec::AssertionFailed, \"message\"\n")
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertEquals("unexpected parse errors: ${errors.map { it.errorDescription }}", 0, errors.size)
    }
}
