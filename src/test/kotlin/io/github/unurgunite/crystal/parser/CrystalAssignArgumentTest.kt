package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import io.github.unurgunite.crystal.CrystalParserDefinition

/**
 * `name = value` as a call argument (spec suites build fixtures this way:
 * `Message.new(headers = ..., body = ...)`). Before the grammar fix the
 * argument list's COMMA was read as a multi-assign separator, collapsing the
 * call into ERROR_ELEMENTs.
 */
class CrystalAssignArgumentTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {
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

    fun testSingleAssignArg() = assertParses("a.cr", "f(a = 1)\n")

    fun testTwoAssignArgs() = assertParses("a.cr", "f(a = 1, b = 2)\n")

    fun testAssignThenPlainArg() = assertParses("a.cr", "f(a = 1, 2)\n")

    fun testPlainThenAssignArg() = assertParses("a.cr", "f(1, a = 2)\n")

    fun testAssignArgsOnDotCall() = assertParses("a.cr", "x.f(a = 1, b = 2)\n")

    fun testMultilineAssignArgs() = assertParses("a.cr", "m = M.new(\n  headers = C.new,\n  body = T.new(\"c\"),\n)\n")

    // Guards: multi-assign and plain named args must keep their old shape.
    fun testMultiAssignmentStillParses() = assertParses("a.cr", "a, b = 1, 2\n")

    fun testChainedAssignmentStillParses() = assertParses("a.cr", "a = b = 1\n")

    fun testColonNamedArgsStillParse() = assertParses("a.cr", "f(a: 1, b: 2)\n")

    fun testAndAssignArgumentStillParses() = assertParses("a.cr", "f(a &&= 1)\n")
}
