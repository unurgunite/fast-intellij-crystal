package io.github.unurgunite.crystal

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalCodeBlockSupportHandlerTest : BasePlatformTestCase() {
    private val handler = CrystalCodeBlockSupportHandler()

    /**
     * The handler's structure surface is protected and the class is final,
     * so structural assertions go through reflection. Behavioral assertions
     * use the public getCodeBlockMarkerRanges().
     */
    private fun invoke(
        name: String,
        arg: IElementType? = null,
    ): TokenSet {
        val method =
            if (arg == null && name != "getDirectChildrenElementTypes") {
                CrystalCodeBlockSupportHandler::class.java.getDeclaredMethod(name)
            } else {
                CrystalCodeBlockSupportHandler::class.java.getDeclaredMethod(name, IElementType::class.java)
            }
        method.isAccessible = true
        return if (arg == null && name != "getDirectChildrenElementTypes") {
            method.invoke(handler) as TokenSet
        } else {
            method.invoke(handler, arg) as TokenSet
        }
    }

    private fun topLevel(): TokenSet = invoke("getTopLevelElementTypes")

    private fun keywords(): TokenSet = invoke("getKeywordElementTypes")

    private fun childrenOf(parentType: IElementType?): TokenSet = invoke("getDirectChildrenElementTypes", parentType)

    fun testTopLevelCoversAllBlockConstructs() {
        val top = topLevel()
        for (expected in listOf(
            CrystalTypes.IF_STATEMENT,
            CrystalTypes.UNLESS_STATEMENT,
            CrystalTypes.WHILE_STATEMENT,
            CrystalTypes.UNTIL_STATEMENT,
            CrystalTypes.FOR_STATEMENT,
            CrystalTypes.CASE_STATEMENT,
            CrystalTypes.BEGIN_STATEMENT,
            CrystalTypes.METHOD_DEFINITION,
            CrystalTypes.CLASS_DEFINITION,
            CrystalTypes.MODULE_DEFINITION,
            CrystalTypes.STRUCT_DEFINITION,
            CrystalTypes.ENUM_DEFINITION,
            CrystalTypes.ANNOTATION_DEFINITION,
            CrystalTypes.LIB_DEFINITION,
            CrystalTypes.MACRO_DEFINITION,
            CrystalTypes.BLOCK,
        )) {
            assertTrue("$expected should be a top-level block", top.contains(expected))
        }
    }

    fun testKeywordsCoverOpenMiddleAndClose() {
        val kw = keywords()
        for (expected in listOf(
            CrystalTypes.DEF,
            CrystalTypes.CLASS,
            CrystalTypes.DO,
            CrystalTypes.IF,
            CrystalTypes.END,
            CrystalTypes.ELSE,
            CrystalTypes.ELSIF,
            CrystalTypes.WHEN,
            CrystalTypes.RESCUE,
            CrystalTypes.ENSURE,
        )) {
            assertTrue("$expected should be a highlightable keyword", kw.contains(expected))
        }
    }

    fun testEveryTopLevelHasChildrenMapping() {
        for (type in topLevel().types) {
            val children = childrenOf(type)
            assertFalse(
                "$type has no children mapping — cursor on its keywords highlights nothing",
                children == TokenSet.EMPTY,
            )
        }
    }

    fun testChildrenAreSubsetOfKeywords() {
        val kw = keywords().types.toSet()
        // Clause composites (ELSIF_CLAUSE etc.) are structural, not keywords themselves
        val structural =
            setOf(
                CrystalTypes.ELSIF_CLAUSE,
                CrystalTypes.ELSE_CLAUSE,
                CrystalTypes.RESCUE_CLAUSE,
                CrystalTypes.ENSURE_CLAUSE,
                CrystalTypes.WHEN_CLAUSE,
                CrystalTypes.IN_CLAUSE,
            )
        for (parent in topLevel().types) {
            for (child in childrenOf(parent).types) {
                assertTrue(
                    "$child (child of $parent) must be a keyword or a known clause",
                    child in kw || child in structural,
                )
            }
        }
    }

    fun testUnknownTypeYieldsEmpty() {
        assertEquals(TokenSet.EMPTY, childrenOf(CrystalTypes.IDENTIFIER))
        assertEquals(TokenSet.EMPTY, childrenOf(null))
    }

    fun testMethodDefinitionChildren() {
        val children = childrenOf(CrystalTypes.METHOD_DEFINITION)
        assertTrue(children.contains(CrystalTypes.DEF))
        assertTrue(children.contains(CrystalTypes.END))
        assertFalse(children.contains(CrystalTypes.ELSE))
    }

    fun testMarkerRangesCoverIfElseEnd() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "i<caret>f x > 1\n  foo\nelse\n  bar\nend\n",
            )
        val element = file.findElementAt(myFixture.caretOffset)!!
        assertEquals(CrystalTypes.IF, element.node.elementType)
        val ranges = handler.getCodeBlockMarkerRanges(element)
        assertFalse("Cursor on 'if' should yield marker ranges, got none", ranges.isEmpty())
        val text = file.text
        val covered = ranges.map { text.substring(it.startOffset, it.endOffset) }
        assertTrue("'if' covered: $covered", covered.any { "if" in it })
        assertTrue("'else' covered: $covered", covered.any { "else" in it })
        assertTrue("'end' covered: $covered", covered.any { "end" in it })
    }

    fun testMarkerRangesEmptyOutsideBlocks() {
        val file = myFixture.configureByText("test.cr", "x = <caret>1\n")
        val element = file.findElementAt(myFixture.caretOffset)!!
        assertTrue(handler.getCodeBlockMarkerRanges(element).isEmpty())
    }
}
