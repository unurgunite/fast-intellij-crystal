package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypeCheckInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    // ==================== Basic Type Mismatches ====================

    fun testIntLiteralWhereStringExpected() {
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

    fun testStringLiteralWhereIntExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(x : Int32)
            end
            add(<error descr="Type mismatch: expected 'Int32', got 'String'">"hello"</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBoolWhereStringExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet(<error descr="Type mismatch: expected 'String', got 'Bool'">true</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testLiteralMatchesAnnotation() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(x : Int32)
            end
            add(42)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testStringLiteralMatchesString() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String)
            end
            greet("world")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Nil and Nilable ====================

    fun testNilWhereNonNilExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(x : Int32)
            end
            add(<error descr="Type mismatch: expected 'Int32', got 'Nil'">nil</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNilWhereNilableExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def maybe(x : Int32?)
            end
            maybe(nil)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNilWhereUnionWithNilExpected() {
        myFixture.configureByText(
            "test.cr",
            """
            def maybe(x : Int32 | Nil)
            end
            maybe(nil)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Union Types ====================

    fun testUnionAcceptsMember() {
        myFixture.configureByText(
            "test.cr",
            """
            def flex(x : Int32 | String)
            end
            flex("hello")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUnionRejectsMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            def flex(x : Int32 | String)
            end
            flex(<error descr="Type mismatch: expected 'Int32 | String', got 'Bool'">true</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== No Annotation → No Check ====================

    fun testNoAnnotationNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def duck(x)
            end
            duck(123)
            duck("anything")
            duck(true)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Overloads ====================

    fun testOverloadCompatible() {
        myFixture.configureByText(
            "test.cr",
            """
            def process(x : Int32)
            end
            def process(x : String)
            end
            process("hello")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testAllOverloadsIncompatible() {
        myFixture.configureByText(
            "test.cr",
            """
            def process(x : Int32)
            end
            def process(x : String)
            end
            process(<error descr="Type mismatch: expected 'Int32' or 'String', got 'Bool'">true</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Numeric Autocasting ====================

    fun testUnsuffixedIntToInt64Autocast() {
        myFixture.configureByText(
            "test.cr",
            """
            def big(x : Int64)
            end
            big(123)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUnsuffixedIntToFloat64Autocast() {
        myFixture.configureByText(
            "test.cr",
            """
            def calc(x : Float64)
            end
            calc(42)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSuffixedIntMatchesExactType() {
        myFixture.configureByText(
            "test.cr",
            """
            def big(x : Int64)
            end
            big(123_i64)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSuffixedIntAutocastToLarger() {
        myFixture.configureByText(
            "test.cr",
            """
            def big(x : Int64)
            end
            big(123_i32)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testFloat32ToFloat64Autocast() {
        myFixture.configureByText(
            "test.cr",
            """
            def calc(x : Float64)
            end
            calc(1.5_f32)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Multiple Parameters ====================

    fun testMultipleParamsSecondMismatch() {
        myFixture.configureByText(
            "test.cr",
            """
            def pair(a : Int32, b : String)
            end
            pair(1, <error descr="Type mismatch: expected 'String', got 'Int32'">2</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Unknown Type → No Error ====================

    fun testUnknownTypeNoError() {
        myFixture.configureByText(
            "test.cr",
            """
            def process(x : MyCustomClass)
            end
            process(123)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Splat Parameters ====================

    fun testSplatParameterSkipped() {
        myFixture.configureByText(
            "test.cr",
            """
            def variadic(*args)
            end
            variadic(1, "hello", true)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Default Values ====================

    fun testFewerArgsThanParamsWithDefaults() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet(name : String, greeting : String = "Hello")
            end
            greet("World")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Named tuple type as call argument ====================

    fun testNamedTupleWithNullableTypesAsArgument() {
        // `DB#query_all(..., as: {Int64, String?})` — the tuple members are
        // types (`String?` keeps `?` attached), not value expressions.
        // checkHighlighting asserts zero unexpected errors.
        myFixture.configureByText(
            "test.cr",
            """
            rows = DB.db.query_all("SELECT id, name FROM t", as: {Int64, String, String?, String?})
            puts rows
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNamedTypeBraceValueAsArgument() {
        // `headers: HTTP::Headers{...}` — a type path + hash literal as a
        // named value (HTTP::Client call sites). checkHighlighting asserts
        // zero unexpected errors.
        myFixture.configureByText(
            "test.cr",
            """
            response = HTTP::Client.post("url", headers: HTTP::Headers{"a" => "b"}, body: body)
            puts response
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testToJsonHashFitsStringParam() {
        // `rc_post(path, hash.to_json)` against `body : String` — the
        // `.to_json` boundary: serialized at the call site, no mismatch.
        // A bare hash against String stays a mismatch (checked by the
        // literal tests in CrystalTypeCheckLiteralsTest).
        myFixture.configureByText(
            "test.cr",
            """
            def rc_post(path : String, body : String)
            end
            rc_post("/sync/copy", {"jobid" => 1}.to_json)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMethodReturnTypeKeepsGenerics() {
        // `data.to_hash` where `to_hash : Hash(String, String)` feeds
        // `validate(d : Hash(String, String))` (pipeline.cr shape): the
        // DOT-call return type must keep generic args — bare `Hash` also
        // fits, but the full form must resolve, not collapse or null out.
        myFixture.configureByText(
            "test.cr",
            """
            struct PersonData
              def to_hash : Hash(String, String)
                {"a" => "b"}
              end
            end
            def validate(d : Hash(String, String))
            end
            data = PersonData.new
            validate(data.to_hash)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReceiverClassMethodBeatsStdlibSameName() {
        // A same-named stdlib generic (`Hash#to_hash`-like) must not shadow
        // the receiver class's own method: `data.to_hash` with data: Holder
        // takes Holder#to_hash even when a generic `to_hash` exists elsewhere.
        myFixture.configureByText(
            "test.cr",
            """
            struct Holder
              def to_hash : Hash(String, String)
                {"a" => "b"}
              end
            end
            struct Other
              def to_hash : Hash(String, Int32)
                {"a" => 1}
              end
            end
            def validate(d : Hash(String, String))
            end
            data = Holder.new
            validate(data.to_hash)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
