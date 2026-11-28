package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A loop re-tests its condition after the body runs, so a value written in the
 * body is live on the next iteration even when nothing after the loop reads it.
 * Only block bodies were treated that way, so a `while` body write of a name
 * the condition reads was reported as an unused assignment.
 */
class CrystalUnusedVarLoopTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection())
    }

    fun testWhileConditionCarriesBodyWriteToNextIteration() {
        myFixture.configureByText(
            "spec.cr",
            """
            def find(script : String, needle : String) : Nil
              cursor = 0
              while start = script.index(needle, cursor)
                cursor = start + needle.size
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUntilConditionCarriesBodyWriteToNextIteration() {
        myFixture.configureByText(
            "spec.cr",
            """
            def find(script : String, needle : String) : Nil
              cursor = 0
              until (start = script.index(needle, cursor)).nil?
                cursor = start + needle.size
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBodyWriteOfNameTheConditionNeverReadsIsStillUnused() {
        myFixture.configureByText(
            "spec.cr",
            """
            def find(xs : Array(Int32)) : Nil
              i = 0
              while i < xs.size
                <weak_warning descr="Variable 'tmp' is never used">tmp</weak_warning> = xs[i]
                i += 1
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
