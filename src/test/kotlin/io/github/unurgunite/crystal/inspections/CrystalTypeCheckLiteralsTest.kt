package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypeCheckLiteralsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testRegexLiteralMatchesRegexType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(r : Regex)
            end
            foo(/pattern/)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRegexLiteralMismatchString() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Regex'">/pattern/</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testCommandExpressionMatchesStringType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(`ls`)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testCommandExpressionMismatchInt() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(n : Int32)
            end
            foo(<error descr="Type mismatch: expected 'Int32', got 'String'">`ls`</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testHeredocLiteralMatchesStringType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(<<-HEREDOC
            hello
            HEREDOC
            )
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testHeredocLiteralMismatchInt() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(n : Int32)
            end
            foo(<error descr="Type mismatch: expected 'Int32', got 'String'"><<-HEREDOC
            hello
            HEREDOC</error>
            )
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSizeofExpressionMatchesInt32Type() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(n : Int32)
            end
            foo(sizeof(Int32))
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSizeofExpressionMismatchString() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Int32'">sizeof(Int32)</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Array Literal Inference ====================

    fun testArrayLiteralOfTypeAnnotation() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(a : Array(Int32))
            end
            foo([] of Int32)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralHomogeneousNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(a : Array(Int32))
            end
            foo([1, 2, 3])
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralHeterogeneousNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(a : Array(Int32 | String))
            end
            foo([1, "hi"])
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(a : Array(Int32))
            end
            foo(<error descr="Type mismatch: expected 'Array(Int32)', got 'Array(String)'">["hello"]</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testArrayLiteralWhereStringExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Array(Int32)'">[1, 2]</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Hash Literal Inference ====================

    fun testHashLiteralOfTypeAnnotation() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(h : Hash(String, Int32))
            end
            foo({} of String => Int32)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testHashLiteralShorthandNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(h : Hash(Symbol, Int32))
            end
            foo({a: 1})
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testHashLiteralArrowNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(h : Hash(String, Int32))
            end
            foo({"a" => 1})
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testHashLiteralTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(h : Hash(String, Int32))
            end
            foo(<error descr="Type mismatch: expected 'Hash(String, Int32)', got 'Hash(String, String)'">{"a" => "b"}</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testHashLiteralWhereStringExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Hash(Symbol, Int32)'">{a: 1}</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Tuple Literal Inference ====================

    fun testTupleLiteralNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(t : Tuple(Int32, String))
            end
            foo({1, "hi"})
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testTupleLiteralTypeMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(t : Tuple(Int32, String))
            end
            foo(<error descr="Type mismatch: expected 'Tuple(Int32, String)', got 'Tuple(String, Int32)'">{"hi", 1}</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testTupleLiteralWhereStringExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo(<error descr="Type mismatch: expected 'String', got 'Tuple(Int32, Int32)'">{1, 2}</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Control-Flow Union Inference ====================

    fun testTernaryExpressionType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(n : Int32?)
            end
            foo(true ? 1 : nil)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Operator Result Type Inference ====================

    fun testIntegerAdditionType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(n : Int32)
            end
            foo(1 + 2)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testComparisonType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(b : Bool)
            end
            foo(1 == 2)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testStringConcatType() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            foo("a" + "b")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testOrDefaultYieldsOperandNotBool() {
        // `mode? || "test"` is String, not Bool: `||` yields an operand.
        // (pipeline.cr: `EmulatorJob.new(..., mode: run_mode)` mismatch.)
        myFixture.configureByText(
            "test.cr",
            """
            def foo(s : String)
            end
            x = nil
            foo(x || "test")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testToIFamilyYieldsNumeric() {
        // `ENV.fetch(...).to_i` is Int32, not the receiver String
        // (mock_drive.cr: `run(root, port, ...)` against `port : Int32`).
        myFixture.configureByText(
            "test.cr",
            """
            def foo(i : Int32)
            end
            foo("4567".to_i)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUnionArgumentFitsWhenAnyMemberFits() {
        // `sessions_start(query)` where query infers as
        // `String | Hash(String, String)` against `Hash(String, String)`:
        // fits when any union member fits.
        myFixture.configureByText(
            "test.cr",
            """
            def start(query : Hash(String, String))
            end
            q = {"a" => "b"}
            start(q)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testIndexAccessYieldsValueType() {
        // `normalize_phone(d["phone"]?)` against `raw : String?`:
        // `d["phone"]?` is `String | Nil` (Hash value + Nil), not the
        // receiver Hash itself (person_validator.cr shape).
        myFixture.configureByText(
            "test.cr",
            """
            def normalize_phone(raw : String?) : String
              raw.to_s
            end
            d = {"phone" => "123"}
            normalize_phone(d["phone"]?)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
