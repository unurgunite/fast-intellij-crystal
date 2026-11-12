package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import io.github.unurgunite.crystal.CrystalParserDefinition

/**
 * A brace block attached to a call must not swallow the following argument.
 * `x = a.b { 1 }, 2` is a multiple assignment at statement level, but the same
 * shape appears legally as an argument (`foo a.b { 1 }, 2`) and as the value of
 * a scoped call.
 */
class CrystalBraceBlockArgumentTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {
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

    fun testBraceBlockThenArgument() = assertParses("a.cr", "foo a.b { 1 }, 2\n")

    fun testBraceBlockOnChainThenArgument() = assertParses("a.cr", "foo a.b.c { 1 }, 2\n")

    fun testBraceBlockThenNamedArgument() = assertParses("a.cr", "foo q: a.b { 1 }, r: 2\n")

    fun testBraceBlockThenDotChain() = assertParses("a.cr", "foo a.b { 1 }.c, 2\n")

    fun testMultipleAssignmentStillParses() = assertParses("a.cr", "a, b = 1, 2\n")

    // A paren-less callee followed by `{` takes a block, never a hash argument
    // (`spawn { }` used to parse as `spawn({})`).
    fun testEmptyBraceBlockOnBareCallee() = assertParses("a.cr", "spawn { }\n")

    fun testBraceBlockBodyOnBareCallee() = assertParses("a.cr", "spawn { 1 }\n")

    fun testNestedEmptyBraceBlockOnBareCallee() = assertParses("a.cr", "n.times do\n  spawn { }\nend\n")
}
