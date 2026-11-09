package io.github.unurgunite.crystal.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Range literals (`1..2`, `1...2`) must be typed as `Range(...)`, not as their
 * start operand.
 */
class CrystalTypeCheckRangeTest : BasePlatformTestCase() {
    fun testRangeLiteralsMatchRangeParameter() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.configureByText(
            "t.cr",
            """
            def consume(r : Range(Int32, Int32?))
            end
            def run
              consume(1..2)
              consume(1...2)
            end
            """.trimIndent() + "\n",
        )
        assertEmpty(problems())
    }

    fun testRangeLiteralDoesNotMatchIntParameter() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.configureByText(
            "t.cr",
            """
            def consume(n : Int32)
            end
            def run
              consume(1..2)
            end
            """.trimIndent() + "\n",
        )
        assertEquals(1, problems().size)
    }

    private fun problems() = myFixture.doHighlighting().filter { it.severity >= HighlightSeverity.WARNING }
}
