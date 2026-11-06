package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A nested method or block that declares its own parameter shadows a same-named
 * outer variable, so the outer value stays unused.
 */
class CrystalUnusedVarShadowTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection())
    }

    fun testNestedMethodParameterShadowsOuter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = compute
              def inner(value)
                puts value
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBlockParameterShadowsOuter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = compute
              [1].each do |value|
                puts value
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReadInNestedMethodStillCounts() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              def inner
                puts value
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUnusedValueStaysReported() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = compute
              def inner
                puts 1
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A nested method's own local does not rebind the outer variable. */
    fun testNestedMethodLocalDoesNotRebindOuter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = compute
              def inner
                value = 1
                puts value
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A nested method assigning its own local must not close the outer read window. */
    fun testNestedMethodLocalKeepsOuterRead() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              def inner
                value = 1
                puts value
              end
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A read two methods deep still uses the outer value. */
    fun testDeeplyNestedReadCounts() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              def middle
                def inner
                  puts value
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A block shares the scope: assigning there rebinds, reading keeps alive. */
    fun testBlockAssignmentThenOuterRead() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              [1].each do |x|
                value = x
              end
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
