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
}
