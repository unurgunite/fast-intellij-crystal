package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalArgumentCountRecordNewTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
    }

    // ==================== .new Resolves to initialize ====================
    fun testNewResolvesToInitialize() {
        myFixture.configureByText(
            "test.cr",
            """
            class Apfelsaft
              def initialize(cool : String, other : Int32)
                super
              end
            end
            a = Apfelsaft.new("lol", 123)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewTooManyArgsForInitialize() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(x : Int32)
              end
            end
            Foo.new(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewMissingArgsForInitialize() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(x : Int32, y : String)
              end
            end
            Foo.<error descr="Missing required argument(s): 'y'">new</error>(1)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== .new Bare Call (no parens) ====================
    fun testNewBareCallResolvesToInitialize() {
        myFixture.configureByText(
            "test.cr",
            """
            class Apfelsaft
              def initialize(cool : String, other : Int32)
              end
            end
            a = Apfelsaft.new "lol", 123
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewBareCallTooManyArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(x : Int32)
              end
            end
            Foo.new 1, <error descr="Too many arguments: expected at most 1, got 2">2</error>
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewBareCallMissingArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(x : Int32, y : String)
              end
            end
            Foo.<error descr="Missing required argument(s): 'y'">new</error> 1
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== @param shorthand (instance var assignment) ====================
    fun testNewWithShorthandInstanceVar() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(@x : Int32, @y : String)
              end
            end
            Foo.new(1, "hi")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewWithShorthandTooManyArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(@x : Int32)
              end
            end
            Foo.new(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewWithShorthandMissingArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(@x : Int32, @y : String)
              end
            end
            Foo.<error descr="Missing required argument(s): 'y'">new</error>(1)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Record macro support ====================
    fun testRecordNewWithValidNamedArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new(host: "localhost", port: 8080, ssl: true)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithTooManyArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new(<error descr="Unknown named argument 'ssl'">ssl: true</error>, host: "localhost", port: 8080)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithMissingRequiredArg() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.<error descr="Missing required argument(s): 'host'">new</error>(port: 8080)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithPositionalArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new("localhost", 8080)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewUnknownNamedArg() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new(<error descr="Unknown named argument 'unknown'">unknown: "value"</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithValidNamedArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new host: "localhost", port: 8080, ssl: true
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithUnknownNamedArg() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new host: "localhost", port: 8080, <error descr="Unknown named argument 'unknown'">unknown: "value"</error>
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithAssignment() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            config = Config.new host: "lol"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithMissingRequiredArg() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.<error descr="Missing required argument(s): 'host'">new</error> port: 8080
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithPositionalArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new "localhost", 8080
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBareDotCallWithBinaryMultiplication() {
        myFixture.configureByText(
            "test.cr",
            """
            struct Vector2D
              def initialize(@x : Float64, @y : Float64)
              end
            end
            scalar = 2.0
            Vector2D.new x * scalar, y * scalar
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testParenthesizedDotCallWithBinaryMultiplication() {
        myFixture.configureByText(
            "test.cr",
            """
            struct Vector2D
              def initialize(@x : Float64, @y : Float64)
              end
            end
            scalar = 2.0
            Vector2D.new(x * scalar, y * scalar)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBareDotCallWithNamedArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new host: "localhost", port: 8080, ssl: true
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
