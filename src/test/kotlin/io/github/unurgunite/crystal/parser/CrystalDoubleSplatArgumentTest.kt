package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import io.github.unurgunite.crystal.CrystalParserDefinition

/**
 * `**opts` as a bare (parenthesis-free) call argument. `foo **a` parsed alone
 * only by accident: `**` was read as the power operator of the callee
 * (`foo ** a`), so any further argument (`foo **a, b`) hit an ERROR_ELEMENT on
 * the COMMA. A `**` whose operand is followed by a comma, a closer or a line
 * end belongs to the argument list, not to a binary expression.
 */
class CrystalDoubleSplatArgumentTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {
    override fun getTestDataPath(): String = "src/test/testData/parser"

    private fun assertParses(
        name: String,
        code: String,
    ) {
        myFile = createPsiFile(name, code)
        ensureParsed(myFile)
        val errors = PsiTreeUtil.findChildrenOfType(myFile, PsiErrorElement::class.java).toList()
        assertEmpty(errors.map { it.errorDescription + " at " + it.text }.toString(), errors)
    }

    fun testDoubleSplatAlone() = assertParses("d.cr", "foo **a\n")

    fun testDoubleSplatThenArg() = assertParses("d.cr", "foo **a, b\n")

    fun testDoubleSplatThenTwoArgs() = assertParses("d.cr", "foo **a, b, c\n")

    fun testDoubleSplatThenDoubleSplat() = assertParses("d.cr", "foo **a, **b\n")

    fun testDoubleSplatThenNamedArg() = assertParses("d.cr", "foo **a, b: 1\n")

    fun testDoubleSplatInAssignment() = assertParses("d.cr", "x = foo **a, b\n")

    fun testDoubleSplatInGroupedExpression() = assertParses("d.cr", "x = (foo **a, b)\n")

    fun testDoubleSplatInParenthesizedCall() = assertParses("d.cr", "foo(**a, b)\n")

    // Guards: `**` must keep working as the power operator.
    fun testPowerOperatorStillParses() = assertParses("d.cr", "x = a ** b\n")

    fun testPowerOperatorWithFollowingTerm() = assertParses("d.cr", "x = a ** b + c\n")

    fun testPowerOperatorOnLiterals() = assertParses("d.cr", "x = 2 ** 8\n")

    fun testPowerOperatorWithDotCall() = assertParses("d.cr", "y = a ** b.c\n")

    fun testPowerOperatorInParens() = assertParses("d.cr", "z = (a ** b)\n")
}
