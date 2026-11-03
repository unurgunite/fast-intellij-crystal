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
}
