package io.github.unurgunite.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalTypes
import junit.framework.TestCase

class CrystalFoldingBuilderTest : BasePlatformTestCase() {

    private fun openDocument(code: String): com.intellij.openapi.editor.Document {
        val file = myFixture.configureByText("test.cr", code)
        return myFixture.getDocument(file)
    }

    private fun foldRanges(code: String): List<String> {
        val document = openDocument(code)
        val file = myFixture.file
        return CrystalFoldingBuilder().buildFoldRegions(file, document, false)
            .map { document.getText(it.range!!) }
    }

    private fun foldLines(code: String): List<Pair<Int, Int>> {
        val document = openDocument(code)
        val file = myFixture.file
        return CrystalFoldingBuilder().buildFoldRegions(file, document, false).map {
            document.getLineNumber(it.range!!.startOffset) to document.getLineNumber(it.range.endOffset)
        }
    }

    fun testDefAndClassFold() {
        // Lines: 0:class Foo / 1:def bar / 2:1 / 3:end / 4:end
        val lines = foldLines("class Foo\n  def bar\n    1\n  end\nend\n")
        assertEquals(2, lines.size)
        assertTrue("class fold 0->4 in $lines", (0 to 4) in lines)
        assertTrue("def fold 1->3 in $lines", (1 to 3) in lines)
    }

    fun testSingleLineDoesNotFold() {
        assertTrue(foldRanges("def foo; end\n").isEmpty())
    }

    fun testDoBlockFolds() {
        val ranges = foldRanges("items.each do |x|\n  puts x\nend\n")
        assertEquals(1, ranges.size)
    }

    fun testIfConditionFoldsAfterCondition() {
        val ranges = foldRanges("if x > 1\n  puts x\nend\n")
        assertEquals(1, ranges.size)
        // Fold starts after the condition line (at the newline), not at `if`
        assertTrue("Fold range should start at end of condition line, got: '${ranges[0].take(10)}'",
            ranges[0].startsWith("\n"))
    }

    fun testMultilineArrayAndHashFold() {
        val ranges = foldRanges("x = [\n  1,\n  2\n]\nh = {\n  a: 1\n}\n")
        assertEquals(2, ranges.size)
    }

    fun testSingleLineArrayDoesNotFold() {
        assertTrue(foldRanges("x = [1, 2]\n").isEmpty())
    }

    fun testConsecutiveCommentsFold() {
        val ranges = foldRanges("# one\n# two\n# three\nx = 1\n")
        assertEquals(1, ranges.size)
        assertTrue(ranges[0].startsWith("# one"))
    }

    fun testSingleCommentDoesNotFold() {
        assertTrue(foldRanges("# lonely\nx = 1\n").isEmpty())
    }

    fun testPlaceholderTexts() {
        val builder = CrystalFoldingBuilder()
        val document = openDocument("class Foo\n  def bar\n    1\n  end\nend\n")
        val file = myFixture.file
        val placeholders = CrystalFoldingBuilder().buildFoldRegions(file, document, false)
            .associate { it.element?.elementType to builder.getPlaceholderText(it.element!!) }
        assertEquals(" ... end", placeholders[CrystalTypes.DEF])
        assertEquals(" ... end", placeholders[CrystalTypes.CLASS])
        assertFalse(builder.isCollapsedByDefault(file.node.firstChildNode))
    }
}

class CrystalCommenterTest : TestCase() {
    fun testLineCommentPrefix() {
        val commenter = CrystalCommenter()
        assertEquals("# ", commenter.lineCommentPrefix)
        assertNull(commenter.blockCommentPrefix)
        assertNull(commenter.blockCommentSuffix)
        assertNull(commenter.commentedBlockCommentPrefix)
        assertNull(commenter.commentedBlockCommentSuffix)
    }
}

class CrystalBraceMatcherTest : TestCase() {
    fun testPairsCoverStructuralKeywords() {
        val pairs = CrystalBraceMatcher().pairs.associate { it.leftBraceType to it.rightBraceType }
        assertEquals(CrystalTypes.RPAREN, pairs[CrystalTypes.LPAREN])
        assertEquals(CrystalTypes.RBRACKET, pairs[CrystalTypes.LBRACKET])
        assertEquals(CrystalTypes.RBRACE, pairs[CrystalTypes.LBRACE])
        assertEquals(CrystalTypes.END, pairs[CrystalTypes.DEF])
        assertEquals(CrystalTypes.END, pairs[CrystalTypes.CLASS])
        assertEquals(CrystalTypes.END, pairs[CrystalTypes.DO])
        assertEquals(CrystalTypes.END, pairs[CrystalTypes.IF])
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, pairs[CrystalTypes.PERCENT_LITERAL_BEGIN])
    }

    fun testMatchingPairsHaveMatchingCount() {
        // Every opening structural keyword that folds must also match braces
        val lefts = CrystalBraceMatcher().pairs.map { it.leftBraceType }.toSet()
        for (keyword in listOf(
            CrystalTypes.DEF, CrystalTypes.CLASS, CrystalTypes.MODULE,
            CrystalTypes.STRUCT, CrystalTypes.ENUM, CrystalTypes.IF,
            CrystalTypes.UNLESS, CrystalTypes.WHILE, CrystalTypes.DO,
            CrystalTypes.BEGIN, CrystalTypes.CASE, CrystalTypes.MACRO,
            CrystalTypes.LIB, CrystalTypes.ANNOTATION
        )) {
            assertTrue("$keyword should have a brace pair", keyword in lefts)
        }
    }
}
