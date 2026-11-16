package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A brace literal as an argument counts as one positional argument, never a
 * block body — including when it carries a trailing call
 * (`JSON.parse({...}.to_json)`) or spans several lines
 * (`File.write(path, {...})`).
 */
class CrystalArgumentCountBraceLiteralArgTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection())
    }

    fun testMultiLineHashIsSecondArgument() {
        myFixture.configureByText(
            "a.cr",
            """
            def write(path : String, content : String)
            end
            id = "a1"
            write("out.json", {
              "attempt_id" => id,
              "name" => "n",
            })
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMultiLineHashSecondArgumentNoParens() {
        myFixture.configureByText(
            "a.cr",
            """
            def write(path : String, content : String)
            end
            id = "a1"
            write "out.json", {
              "attempt_id" => id,
              "name" => "n",
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMultiLineHashWithTrailingCallIsOneArgument() {
        myFixture.configureByText(
            "a.cr",
            """
            def parse(input : String)
            end
            t = 1
            parse({
              "time" => t,
              "req"  => t,
            }.to_json)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMissingSecondArgumentStillReported() {
        myFixture.configureByText(
            "a.cr",
            """
            def write(path : String, content : String)
            end
            <error descr="Missing required argument(s): 'content'">write</error>("out.json")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
