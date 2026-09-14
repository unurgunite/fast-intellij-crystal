package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypeCheckRecordDotTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testRecordNewWithCorrectTypes() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new(host: "localhost", port: 8080, ssl: true)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithStringTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new(host: <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>, port: 8080)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithIntTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new(host: "localhost", port: <error descr="Type mismatch: expected 'Int32', got 'String'">"wrong"</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithPositionalArgsCorrectTypes() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32
            Config.new("localhost", 8080)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewWithPositionalArgsTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32
            Config.new(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>, 8080)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithCorrectTypes() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new host: "localhost", port: 8080, ssl: true
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRecordNewBareDotCallWithStringTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            record Config, host : String, port : Int32 = 80
            Config.new host: <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>, port: 8080
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Bare DOT-call type checking ====================

    fun testBareDotCallTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            def hika(name : String)
            end
            class Foo
              def self.bar(x : String)
              end
            end
            puts Foo.bar <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBareDotCallCorrectType() {
        myFixture.configureByText(
            "test.cr",
            """
            def hika(name : String)
            end
            class Foo
              def self.bar(x : String)
              end
            end
            puts Foo.bar "hello"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Scalar Literal Inference (Regression Guards) ====================

    fun testIntLiteralWhereStringExpectedStillWorks() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet(<error descr="Type mismatch: expected 'String', got 'Int32'">123</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testStringLiteralWhereStringExpectedNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet("hello")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNilLiteralWhereNilableExpectedNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String?)
            end
            greet(nil)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Bare DOT-call type checking ====================

    fun testBareDotCallAfterMethodCallTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            class RvmCli
              class Tools
                def self.hello(name : String)
                end
              end
            end
            puts RvmCli::Tools.hello <error descr="Type mismatch: expected 'String', got 'Int32'">123</error>
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Trivial Expression Types ====================
}
