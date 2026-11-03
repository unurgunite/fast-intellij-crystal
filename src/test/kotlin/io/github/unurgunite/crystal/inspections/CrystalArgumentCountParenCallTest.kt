package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A parenthesised argument that is itself a call (`raise(Foo.new(x))`) must
 * count as a positional argument, not as a brace block body.
 */
class CrystalArgumentCountParenCallTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection())
    }

    fun testParenCallArgumentIsPositional() {
        myFixture.configureByText(
            "a.cr",
            """
            class ArgumentError < Exception
              def initialize(msg : String)
              end
            end

            def raise_it(message : String)
            end

            def fail_with(d : String)
              raise_it(ArgumentError.new("no driver #{d}"))
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testExcessParenCallArgumentStillReported() {
        myFixture.configureByText(
            "b.cr",
            """
            def consume(w : String)
            end

            def build(d : String)
              consume(wrap(d), <error descr="Too many arguments: expected at most 1, got 2">"x"</error>)
            end

            def wrap(a : String) : String
              a
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}

/**
 * `eq` / `expect_raises` are spec expectation helpers resolved through a module
 * method; arity checking them against unrelated resolution results is noise.
 */
class CrystalArgumentCountSpecExpectationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection())
    }

    fun testSpecExpectationHelpersAreSkipped() {
        myFixture.configureByText(
            "c.cr",
            """
            def eq(value)
            end

            def expect_raises(klass)
            end

            def check
              eq(Array(Int32))
              expect_raises(Exception, "message")
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
