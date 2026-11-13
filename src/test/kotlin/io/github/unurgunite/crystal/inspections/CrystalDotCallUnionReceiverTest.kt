package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A union receiver must be checked against every member, not only the first.
 *
 * Resolving `a | b` to its first member alone meant a method defined on the
 * second member found no owner among the candidates, so the call was either
 * silently skipped or reported against whatever else shared the name.
 */
class CrystalDotCallUnionReceiverTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testMethodOnSecondUnionMemberIsAccepted() {
        myFixture.configureByText(
            "test.cr",
            """
            class First
              def only_here(x : Int32)
              end
            end

            class Second
              def only_there(x : String)
              end
            end

            def run(pick : First | Second)
              pick.only_there("ok")
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMismatchOnSecondUnionMemberIsStillReported() {
        myFixture.configureByText(
            "test.cr",
            """
            class First
              def only_here(x : Int32)
              end
            end

            class Second
              def only_there(x : String)
              end
            end

            def run(pick : First | Second)
              pick.only_there(<error descr="Type mismatch: expected 'String', got 'Int32'">1</error>)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
