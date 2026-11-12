package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import io.github.unurgunite.crystal.CrystalParserDefinition

/**
 * Squiggly heredocs (`<<-NAME`) allow the terminator to be indented to match the
 * body. The plugin reported "Heredoc end delimiter is indented too deeply" on
 * real spec files that `crystal tool format` accepts.
 */
class CrystalHeredocTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {
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

    fun testIndentedTerminator() =
        assertParses(
            "a.cr",
            "def foo\n  text = <<-CRYSTAL\n    hello\n    CRYSTAL\n  text\nend\n",
        )

    fun testTerminatorAtColumnZero() = assertParses("a.cr", "def foo\n  text = <<-CRYSTAL\n    hello\nCRYSTAL\n  text\nend\n")

    fun testTwoIndentedHeredocs() =
        assertParses(
            "a.cr",
            "def foo\n" +
                "  a = <<-ONE\n    x\n    ONE\n" +
                "  b = <<-TWO\n    y\n    TWO\n" +
                "  a + b\nend\n",
        )

    fun testHeredocAsCallArgument() =
        assertParses(
            "a.cr",
            "def foo(filename, content : String)\n" +
                "end\n" +
                "foo(<<-TXT, \"x\")\n" +
                "  body\n" +
                "  TXT\n",
        )

    fun testHeredocAsOnlyCallArgument() = assertParses("a.cr", "def foo(content : String)\nend\nfoo(<<-TXT)\n  body\n  TXT\n")

    fun testHeredocInsideHashLiteral() = assertParses("a.cr", "h = {<<-PRE, 1}\n  body\n  PRE\n")

    fun testHeredocInsideTupleAndArrayLiterals() = assertParses("a.cr", "t = {<<-PRE, 1}\n  body\n  PRE\nx = [<<-TWO, 2]\n  body\n  TWO\n")

    fun testHeredocFollowedByBlock() =
        assertParses("a.cr", "def foo(content : String, &)\n  yield\nend\nfoo(<<-TXT) { 1 }\n  body\n  TXT\n")

    fun testTwoHeredocsOnOneLine() =
        assertParses(
            "a.cr",
            "def foo(a : String, b : String)\nend\n" +
                "foo(<<-ONE, <<-TWO)\n  first\n  ONE\n  second\n  TWO\n",
        )
}
