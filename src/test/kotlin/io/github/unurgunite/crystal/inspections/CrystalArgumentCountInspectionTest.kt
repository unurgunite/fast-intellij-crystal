package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalArgumentCountInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
    }

    // ==================== Missing Required Arguments ====================
    fun testMissingOneRequiredArg() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String, age : Int32)
            end
            <error descr="Missing required argument(s): 'age'">greet</error>("Hans")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMissingAllRequiredArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String, age : Int32)
            end
            <error descr="Missing required argument(s): 'name', 'age'">greet</error>()
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNoArgsMissingWithDefaults() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String, age : Int32 = 30)
            end
            greet("Hans")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testAllDefaultsNoArgsRequired() {
        myFixture.configureByText(
            "test.cr",
            """
            def config(host : String = "localhost", port : Int32 = 8080)
            end
            config()
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Too Many Arguments ====================
    fun testTooManyArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet("Hans", <error descr="Too many arguments: expected at most 1, got 2">"extra"</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testTooManyArgsMultipleExcess() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet("Hans", <error descr="Too many arguments: expected at most 1, got 3">"extra1"</error>, <error descr="Too many arguments: expected at most 1, got 3">"extra2"</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Named Arguments ====================
    fun testNamedArgSatisfiesRequired() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String, age : Int32)
            end
            greet(age: 30, name: "Hans")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUnknownNamedArg() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet(<error descr="Unknown named argument 'unknown'">unknown: "value"</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Correct Argument Count ====================
    fun testExactArgCount() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32)
            end
            add(1, 2)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNoParamsNoArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def hello
            end
            hello()
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Bare Calls ====================
    fun testBareCallMissingArg() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String, age : Int32)
            end
            <error descr="Missing required argument(s): 'age'">greet</error> "Hans"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBareCallCorrect() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet "Hans"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== DOT-calls ====================
    fun testDotCallMissingArg() {
        myFixture.configureByText(
            "test.cr",
            """
            def self.create(name : String, age : Int32)
            end
            Foo.<error descr="Missing required argument(s): 'age'">create</error>("Hans")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDotCallCorrect() {
        myFixture.configureByText(
            "test.cr",
            """
            def self.create(name : String)
            end
            Foo.create("Hans")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Overloads ====================
    fun testOverloadOneMatches() {
        myFixture.configureByText(
            "test.cr",
            """
            def process(a : Int32)
            end
            def process(a : Int32, b : String)
            end
            process(42)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testOverloadNoneMatches() {
        myFixture.configureByText(
            "test.cr",
            """
            def process(a : Int32, b : String)
            end
            def process(a : Int32, b : String, c : Float64)
            end
            <error descr="Missing required argument(s): 'b'">process</error>(42)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Mixed Named and Positional ====================
    fun testMixedNamedAndPositional() {
        myFixture.configureByText(
            "test.cr",
            """
            def connect(host : String, port : Int32, ssl : Bool)
            end
            connect("localhost", ssl: true, port: 443)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Unknown Method (no false positive) ====================
    fun testUnknownMethodNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            unknown_method(1, 2, 3, 4, 5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Parameter variable shadowing ====================
    fun testParameterVariableNotConfusedWithMethod() {
        myFixture.configureByText(
            "test.cr",
            """
            def dance(count : Int32)
              return count + 87
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testParameterVariableNotConfusedWithMethodInClass() {
        myFixture.configureByText(
            "test.cr",
            """
            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testLocalVariableAssignmentNotConfusedWithMethod() {
        myFixture.configureByText(
            "test.cr",
            """
            count = 42
            result = count + 87
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMethodWithShorthandTooManyArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(@x : Int32)
            end
            foo(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
