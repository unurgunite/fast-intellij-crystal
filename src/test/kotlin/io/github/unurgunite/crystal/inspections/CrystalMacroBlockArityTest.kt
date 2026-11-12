package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Arity checks inside a macro-driven configuration block. The block's macro
 * expands each entry into a generated method, so the entry name must not be
 * matched against an unrelated same-named method from the indexed stdlib.
 */
class CrystalMacroBlockArityTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
    }

    fun testMacroConfigBlockEntryNotChecked() {
        myFixture.configureByText(
            "t.cr",
            """
            class Node
              def line_number : Int64?
                nil
              end
            end

            macro properties(&block)
            end

            class Rule
              properties do
                line_number 1
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testPlainCallStillCheckedOutsideMacroBlock() {
        myFixture.configureByText(
            "t.cr",
            """
            class Node
              def line_number : Int64?
                nil
              end
            end

            class Rule
              def run
                Node.line_number <error descr="Too many arguments: expected at most 0, got 1">1</error>
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
