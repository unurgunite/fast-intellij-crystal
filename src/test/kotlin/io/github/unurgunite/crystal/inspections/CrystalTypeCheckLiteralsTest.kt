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

    fun testConversionCallThroughVariableKeepsType() {
        // `port = "4567".to_i` then `run(port)` against `port : Int32`:
        // textual literal inference must not steal the call's type by its
        // leading quote (mock_drive.cr shape).
        myFixture.configureByText(
            "test.cr",
            """
            def run(root : String, port : Int32)
            end
            root = "x"
            port = "4567".to_i
            run(root, port)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testChainedCallTypesByTail() {
        // `port = ENV.fetch("P", "4567").to_i`: the chain tail (`.to_i` →
        // Int32) decides the type, not the head (`.fetch` → String).
        // Same family as mock_drive.cr's `ENV.fetch(...).to_i`.
        myFixture.configureByText(
            "test.cr",
            """
            def run(port : Int32)
            end
            port = ENV.fetch("P", "4567").to_i
            run(port)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMergeResultFitsConcreteHash() {
        // `d = valid_data.merge({...})` then `validate(d)` against
        // `Hash(String, String)`: stdlib merge yields `Hash(K | L, V | W)` —
        // all-placeholder slots fit concrete params (security_spec.cr shape).
        myFixture.configureByText(
            "test.cr",
            """
            def validate(d : Hash(String, String))
            end
            def valid_data : Hash(String, String)
              {"a" => "b"}
            end
            d = valid_data.merge({"phone" => "1"})
            validate(d)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testStdlibMergeShapeFitsConcreteHash() {
        // Full stdlib shape: `merge(other : Hash(L, W)) : Hash(K | L, V | W)`
        // on a generic Hash — the return annotation must survive union
        // splitting whole (naive split("|") truncated it to `Hash(K`).
        myFixture.configureByText(
            "test.cr",
            """
            class Hash(K, V)
              def merge(other : Hash(L, W)) : Hash(K | L, V | W)
              end
            end
            def validate(d : Hash(String, String))
            end
            def valid_data : Hash(String, String)
              {"a" => "b"}
            end
            d = valid_data.merge({"phone" => "1"})
            validate(d)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testClassMethodPrefersOwnClassOverload() {
        // `URI.parse(connection_string)` against `String | URI`: the
        // class-scoped `URI.parse : URI` wins over a same-named overload
        // from another class (db/src/db.cr shape).
        myFixture.configureByText(
            "test.cr",
            """
            class URI
              def self.parse(raw : String) : URI
              end
            end
            class Other
              def self.parse(raw : String) : Array(String)
              end
            end
            def build(uri : URI | String)
            end
            build(URI.parse("postgres://localhost"))
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testIndexHolderAfterDotCallIsNotAnArgument() {
        // `client.login["ok"].should be_true`: the `["ok"]` holder is index
        // access on the call result, never an argument of `login`
        // (clients_spec.cr shape).
        myFixture.configureByText(
            "test.cr",
            """
            client.login["ok"].should be_true
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testGlobalGenericNewKeepsArgs() {
        // `live = ::Set(String).new` then `prune(live)` against
        // `::Set(String)`: leading `::` tolerated, generic args kept
        // (redis cluster.cr shape).
        myFixture.configureByText(
            "test.cr",
            """
            def prune(live : ::Set(String))
            end
            live = ::Set(String).new
            prune(live)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDefinitionMacrosSkipArity() {
        // `define sum, count, ...` declares methods, never calls them —
        // arity against a same-named 2-param method is noise.
        // (`delegate` with operator symbols is a separate parser gap,
        // covered by the C1 batch.)
        myFixture.configureByText(
            "test.cr",
            """
            define sum, count_distinct, min, max, avg
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDotOperatorCallInInterpolation() {
        // `#{(seconds).*(100).trunc./(100)}s`: dot-form operators with paren
        // args inside string interpolation (mosquito executor.cr). The `/`
        // after DOT is division, never a regex start.
        myFixture.configureByText(
            "test.cr",
            """
            def time_with_units(seconds : Float64) : String
              if seconds > 0.1
                "#{(seconds).*(100).trunc./(100)}s"
              else
                "#{(seconds * 1_000).trunc}ms"
              end
            end
            puts time_with_units(1.5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNilableClassSuffixInParam() {
        // `def read(type : UInt8?.class) : UInt8?`: `.class` after a nilable
        // type in a parameter annotation (sqlite3 result_set.cr).
        myFixture.configureByText(
            "test.cr",
            """
            def read(type : UInt8?.class) : UInt8?
            end
            puts read(UInt8)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNamedMultiInputProcParam() {
        // `&block : Subscription, self ->`: named block with several proc
        // inputs and no return type (redis connection.cr).
        myFixture.configureByText(
            "test.cr",
            """
            def foo(*channels : String, &block : Subscription, self ->)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNilableClassLiteralAsArgument() {
        // `option(name, Bool?)` binding to `T.class`: nilable class literal
        // as a call argument (athena-console input.cr).
        myFixture.configureByText(
            "test.cr",
            """
            def option(name : String, type : T.class) : T forall T
            end
            option("a", Bool?)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testOperatorSymbolsAsArguments() {
        // `delegate :insert, :<<, :[], to: @columns`: bare operator symbols
        // as arguments (athena-console table.cr).
        myFixture.configureByText(
            "test.cr",
            """
            delegate :insert, :<<, :[], to: @columns
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
