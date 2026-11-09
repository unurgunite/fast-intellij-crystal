package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import io.github.unurgunite.crystal.CrystalParserDefinition

/**
 * A leading-`::` value that is an index (`File.open ::Path[a, b], "w" do`) used
 * to leave the `[...]` dangling because the bare-argument arms for `::Const`
 * stopped before `[`.
 */
class CrystalLeadingColonIndexTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {
    override fun getTestDataPath(): String = "src/test/testData/parser"

    private fun assertParses(code: String) {
        myFile = createPsiFile("a.cr", code)
        ensureParsed(myFile)
        val errors = PsiTreeUtil.findChildrenOfType(myFile, PsiErrorElement::class.java).toList()
        assertEmpty(errors.map { it.errorDescription + " at " + it.text }.toString(), errors)
    }

    fun testLeadingColonIndexArgument() = assertParses("File.open ::Path[a, b], \"w\" do |r|\n  r.print 1\nend\n")

    fun testLeadingColonIndexWithoutBlock() = assertParses("Dir.glob ::Path[dir, \"x.json\"]\n")

    // Guards: the other leading-`::` argument shapes must keep parsing.
    fun testLeadingColonPlainArgument() = assertParses("foo ::A::B, 1\n")

    fun testLeadingColonCallArgument() = assertParses("raise ::DB::E.new(\"x\")\n")

    fun testQualifiedConstantMethod() = assertParses("x = IO::Memory.new\n")
}
