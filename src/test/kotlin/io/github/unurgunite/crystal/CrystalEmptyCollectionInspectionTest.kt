package io.github.unurgunite.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.inspections.CrystalEmptyCollectionInspection

class CrystalEmptyCollectionInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalEmptyCollectionInspection())
    }

    fun testEmptyArrayLiteralReported() {
        myFixture.configureByText("test.cr", "a = []")
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Empty array should be reported as error",
            highlights.any { it.description?.contains("Empty array literal") == true },
        )
    }

    fun testEmptyHashLiteralReported() {
        myFixture.configureByText("test.cr", "h = {}")
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "Empty hash should be reported as error",
            highlights.any { it.description?.contains("Empty hash literal") == true },
        )
    }

    fun testArrayWithElementsNotReported() {
        myFixture.configureByText("test.cr", "a = [1, 2, 3]")
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Non-empty array should not be reported",
            highlights.any { it.description?.contains("Empty array literal") == true },
        )
    }

    fun testHashWithEntriesNotReported() {
        myFixture.configureByText("test.cr", "h = {\"a\" => 1}")
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Non-empty hash should not be reported",
            highlights.any { it.description?.contains("Empty hash literal") == true },
        )
    }

    fun testArrayWithOfNotReported() {
        myFixture.configureByText("test.cr", "a = [] of String")
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Array with 'of' should not be reported",
            highlights.any { it.description?.contains("Empty array literal") == true },
        )
    }

    fun testHashWithOfNotReported() {
        myFixture.configureByText("test.cr", "h = {} of String => Int32")
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Hash with 'of' should not be reported",
            highlights.any { it.description?.contains("Empty hash literal") == true },
        )
    }

    /**
     * `spawn { }` is a call with an empty brace BLOCK, not a hash argument.
     * The callee was previously parsed as `spawn({})`, so the inspection
     * reported a phantom empty-hash error inside a benchmark fixture.
     */
    fun testEmptyBraceBlockOnBareCallNotReported() {
        myFixture.configureByText("test.cr", "spawn { }")
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "An empty brace block is a block, not an empty hash literal",
            highlights.any { it.description?.contains("Empty hash literal") == true },
        )
    }

    fun testBraceBlockBodyOnBareCallNotReported() {
        myFixture.configureByText("test.cr", "n.times { spawn { } }")
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "Nested empty brace blocks are blocks, not empty hash literals",
            highlights.any { it.description?.contains("Empty hash literal") == true },
        )
    }
}
