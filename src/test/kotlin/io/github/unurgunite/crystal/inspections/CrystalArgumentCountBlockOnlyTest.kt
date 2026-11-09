package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A method that takes only a block (`def unsync(&)`) is called with a brace
 * block and nothing else. Real code does this constantly around mutex
 * helpers (`unsync { wait_for_available }`), and such a call must not count
 * the block as a positional argument.
 */
class CrystalArgumentCountBlockOnlyTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection())
    }

    fun testBlockOnlyMethodAcceptsBraceBlock() {
        myFixture.configureByText(
            "a.cr",
            """
            class Pool
              private def unsync(&)
                yield
              end

              private def wait_for_available
              end

              def checkout
                unsync { wait_for_available }
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBlockOnlyMethodAcceptsEmptyBraceBlock() {
        myFixture.configureByText(
            "b.cr",
            """
            class Pool
              private def unsync(&)
                yield
              end

              def checkout
                unsync { }
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBlockOnlyMethodAcceptsDoBlock() {
        myFixture.configureByText(
            "c.cr",
            """
            class Pool
              private def unsync(&)
                yield
              end

              private def work
              end

              def checkout
                unsync do
                  work
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBlockOnlyMethodStillRejectsPositionalArgument() {
        myFixture.configureByText(
            "d.cr",
            """
            class Pool
              private def unsync(&)
                yield
              end

              def checkout
                unsync(<error descr="Too many arguments: expected at most 0, got 1">:done</error>)
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBlockAndOneParamAcceptsBlockPlusOne() {
        myFixture.configureByText(
            "e.cr",
            """
            def run_with(io : String, &)
              yield
            end

            def go
              run_with("x") { 1 }
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
