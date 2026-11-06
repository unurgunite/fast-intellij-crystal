package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * `header.ids = [...]` is an attribute write, not a rebinding: `header` is read
 * as the receiver. It used to be recorded as a new assignment of `header`, so
 * the earlier value looked dead and `Value assigned to 'header' is never used`
 * was reported even though the variable was used right afterwards.
 */
class CrystalUnusedVariableSetterTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection())
    }

    fun testAttributeWriteCountsAsRead() {
        myFixture.configureByText(
            "test.cr",
            """
            class Thing
              def ids=(value : Array(Int32))
              end
            end

            def go
              header = Thing.new
              header.ids = [1]
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testAttributeWriteThenChainRead() {
        myFixture.configureByText(
            "test.cr",
            """
            class Thing
              def ids=(value : Array(Int32))
              end

              def ids : Array(Int32)
                [] of Int32
              end
            end

            def go
              header = Thing.new
              header.ids = [1]
              header.ids.should eq [1]
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** Attribute writes must not silence a genuine dead rebinding. */
    fun testDeadRebindingStillReported() {
        myFixture.configureByText(
            "test.cr",
            """
            class Thing
              def ids=(value : Array(Int32))
              end
            end

            def go
              <weak_warning descr="Value assigned to 'header' is never used">header</weak_warning> = Thing.new
              header = Thing.new
              header.ids = [1]
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A dotted path of any depth is still a member write, not a rebinding. */
    fun testDeepAttributeWriteCountsAsRead() {
        myFixture.configureByText(
            "test.cr",
            """
            class Inner
              property c = 0
            end

            class Outer
              getter b = Inner.new
            end

            def go
              a = Outer.new
              a.b.c = 1
              puts a
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** An index write reads the receiver too. */
    fun testIndexWriteCountsAsRead() {
        myFixture.configureByText(
            "test.cr",
            """
            def go
              x = [0, 0]
              x[1] = 2
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A plain local binding written and never read is still reported. */
    fun testPlainLocalAssignmentStillReported() {
        myFixture.configureByText(
            "test.cr",
            """
            def go
              <weak_warning descr="Variable 'y' is never used">y</weak_warning> = 1
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
