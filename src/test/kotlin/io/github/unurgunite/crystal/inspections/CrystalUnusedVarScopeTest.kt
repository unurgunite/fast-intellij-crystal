package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Assignments inside closures (`-> { ... }`) and macro blocks (`{% for %}`,
 * `{% if %}`) do not bound the lifetime of an outer value: a closure may run
 * later, a macro block may not run at all. Reads after them must still count.
 */
class CrystalUnusedVarScopeTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection())
    }

    fun testProcAssignmentThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              callback = ->{ value = 2 }
              callback.call
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testProcParameterShadowsOuter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = compute
              callback = ->(value : Int32) { puts value }
              callback.call(2)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroForAssignmentThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% for item in [1, 2] %}
                value = item
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroIfAssignmentThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% if flag %}
                value = 2
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testPlainRebindingStillReports() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDeadValueBesideMacroBlockStillReports() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Variable 'value' is never used">value</weak_warning> = compute
              {% for item in [1] %}
                puts item
              {% end %}
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroBeginAssignmentThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% begin %}
                value = 2
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroUnlessAssignmentThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% unless flag %}
                value = 2
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroIfElseBranchesThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% if flag %}
                value = 2
              {% else %}
                value = 3
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNestedMacroBlocksAssignmentThenReadAfter() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% for item in [1] %}
                {% if flag %}
                  value = item
                {% end %}
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroTextInStringIsNotABlock() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              puts "{% if flag %}"
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroTextInCommentIsNotABlock() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              # {% end %}
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroElseBranchIsNotAnOpener() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              {% for item in [1] %}
                puts item
              {% else %}
                puts 2
              {% end %}
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroElsifBranchIsNotAnOpener() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              {% if flag %}
                puts 1
              {% elsif other %}
                puts 2
              {% end %}
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testStandaloneMacroDirectiveIsNotAnOpener() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              {% debug %}
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testVerbatimBodyDoesNotSuppressLaterReports() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = compute
              {% verbatim %}
                literal
              {% end %}
              value = 2
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroForWithFilterKeepsReadWindow() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              {% for item in [1] if item.positive? %}
                value = item
              {% end %}
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // A block body may run zero times, so the outer value survives inside it and the
    // block's own write is the only dead one.
    fun testBlockRebindingReportsOnlyTheBlockWrite() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              value = compute
              [1].each do |x|
                <weak_warning descr="Value assigned to 'value' is never used">value</weak_warning> = x
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testProcRebindingWithoutLaterReadIsDead() {
        myFixture.configureByText(
            "spec.cr",
            """
            def outer
              callback = -> { compute }
              callback.call
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
