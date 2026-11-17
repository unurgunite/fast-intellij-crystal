package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import io.github.unurgunite.crystal.CrystalParserDefinition
import io.github.unurgunite.crystal.psi.CrystalHeredocDeferredBody
import io.github.unurgunite.crystal.psi.CrystalHeredocLiteral
import io.github.unurgunite.crystal.psi.CrystalTypes

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

    /**
     * The body of a heredoc starts after its opener's line, so it reaches the
     * parser BEFORE any bracket that closes on a later line. Both sides of the
     * closer have to accept it, otherwise every `.lines`-style heredoc argument
     * reported `RPAREN expected` on the body's last line.
     */
    fun testHeredocWithDotCallClosedOnLaterLine() = assertParses("a.cr", "foo(<<-TXT.lines\n  body\n  TXT\n)\n")

    fun testTwoHeredocsClosedOnLaterLine() =
        assertParses(
            "a.cr",
            "subject.should eq({<<-PRE.lines, <<-POST.lines\n" +
                "      before\n" +
                "    PRE\n" +
                "      after\n" +
                "    POST\n" +
                "  })\n",
        )

    fun testTwoHeredocsInHashAndArrayClosedOnLaterLine() =
        assertParses(
            "a.cr",
            "h = {first: <<-PRE, second: <<-POST\n  a\nPRE\n  b\nPOST\n}\n" +
                "x = [<<-ONE, <<-TWO\n  c\nONE\n  d\nTWO\n]\n",
        )

    fun testTwoHeredocsAssignedThenTheirsEndMarkers() = assertParses("a.cr", "x = [<<-PRE.lines, <<-POST.lines\n  a\nPRE\n  b\nPOST\n]\n")

    /**
     * Each opener owns exactly one end marker. With one shared slot the first
     * body ran until the SECOND marker, reporting a missing delimiter.
     */
    fun testTwoHeredocsPairTheirOwnEndMarkers() {
        myFile = createPsiFile("a.cr", "foo(<<-ONE, <<-TWO)\n  first\n  ONE\n  second\n  TWO\n")
        ensureParsed(myFile)
        val ends =
            PsiTreeUtil
                .collectElementsOfType(myFile, LeafPsiElement::class.java)
                .filter { it.elementType == CrystalTypes.HEREDOC_END }
        assertEquals(2, ends.size)
    }

    /**
     * A body follows its opener's line, so with two openers on that line BOTH
     * bodies arrive after the second one. Only the first opener may claim a
     * body contiguously: the later one used to swallow the first heredoc's
     * text, leaving its own body attached to the enclosing list.
     */
    fun testStackedHeredocBodyStaysWithTheEarliestOpener() {
        myFile = createPsiFile("a.cr", "x = {<<-ONE, <<-TWO\n  first\n  ONE\n  second\n  TWO\n}\n")
        ensureParsed(myFile)

        val literals = PsiTreeUtil.findChildrenOfType(myFile, CrystalHeredocLiteral::class.java).toList()
        assertEquals(listOf("<<-ONE", "<<-TWO"), literals.map { it.text })

        val bodies = PsiTreeUtil.findChildrenOfType(myFile, CrystalHeredocDeferredBody::class.java).toList()
        assertEquals(2, bodies.size)
        assertTrue(bodies[0].text.contains("first") && bodies[0].text.contains("ONE"))
        assertTrue(bodies[1].text.contains("second") && bodies[1].text.contains("TWO"))
    }
}
