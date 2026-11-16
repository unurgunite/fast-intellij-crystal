package io.github.unurgunite.crystal.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * `record` is a macro, not a reserved word, so a method of that name can be
 * called with parentheses from inside a method body (`record(node)`), while
 * `record Foo(x : Int32)` still declares a record. Verified with crystal 1.22.3:
 * the call compiles inside a class and fails only at top level, where the
 * definition form wins.
 */
class CrystalRecordCallTest : BasePlatformTestCase() {
    private fun syntaxErrors(): List<HighlightInfo> =
        myFixture.doHighlighting().filter {
            it.description?.contains("expected", ignoreCase = true) == true
        }

    fun testRecordMethodCallWithParens() {
        myFixture.configureByText(
            "a.cr",
            """
            class Collector
              def record(node : Int32) : Nil
              end

              def visit(node : Int32) : Bool
                record(node) if node > 0
                true
              end
            end
            """.trimIndent(),
        )
        assertEquals("record(node) must parse as a call", emptyList<HighlightInfo>(), syntaxErrors())
    }

    fun testRecordDefinitionStillParses() {
        myFixture.configureByText(
            "a.cr",
            """
            record Point, x : Int32, y : Int32

            record Hollow, x : Int32 do
              def zero?
                x.zero?
              end
            end
            """.trimIndent(),
        )
        assertEquals("record definitions must keep parsing", emptyList<HighlightInfo>(), syntaxErrors())
    }
}
