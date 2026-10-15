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

    fun testRecordLookupInvalidatesAfterEdit() {
        // The per-file record map is cached: replacing the record with a
        // class must hide it from lookup, not serve the stale cached def.
        val file = myFixture.configureByText("test.cr", "record Config, host : String\n")
        assertNotNull(
            io.github.unurgunite.crystal.type.CrystalRecordLookup
                .findRecordDefinition("Config", file),
        )
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.getDocument(file).setText("class Config\nend\n")
        }
        com.intellij.psi.PsiDocumentManager
            .getInstance(project)
            .commitAllDocuments()
        assertNull(
            io.github.unurgunite.crystal.type.CrystalRecordLookup
                .findRecordDefinition("Config", myFixture.file),
        )
    }

    // ==================== Macro `param` constructors (Mosquito-style) ====================

    fun testParamConstructorAcceptsNamedArgs() {
        // `param attempt_id : Int64` generates `initialize` at compile time —
        // `Job.new(attempt_id: ...)` must not report "Unknown named argument".
        myFixture.configureByText(
            "test.cr",
            """
            class CamTestJob < MosquitoJob
              param attempt_id : Int64
            end
            CamTestJob.new(attempt_id: 5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testParamConstructorUnknownArgStillReported() {
        // A genuinely unknown name against `param` declarations still reports.
        myFixture.configureByText(
            "test.cr",
            """
            class CamTestJob < MosquitoJob
              param attempt_id : Int64
            end
            CamTestJob.new(<error descr="Unknown named argument 'bogus'">bogus: 5</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testLiteralInitializeBeatsParamDeclarations() {
        // A literal `def initialize` wins over `param` entries: wrong arity
        // against initialize still reports (param fallback must not shadow it).
        myFixture.configureByText(
            "test.cr",
            """
            class Job
              param attempt_id : Int64
              def initialize(x : Int32)
              end
            end
            Job.new(1, <error descr="Too many arguments: expected at most 1, got 2">2</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testParamConstructorAcrossFilesWithChainedCall() {
        // Real-project shape: the class lives in another file (pipeline.cr)
        // while the call site chains (`CamTestJob.new(attempt_id: ...).enqueue`
        // in app.cr). File-local lookup missed the `param` entries entirely.
        myFixture.addFileToProject(
            "jobs.cr",
            """
            class CamTestJob < MosquitoJob
              param attempt_id : Int64
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "app.cr",
            "CamTestJob.new(attempt_id: 5).enqueue\n",
        )
        myFixture.checkHighlighting()
    }

    fun testEmptyInheritedInitializeYieldsToParam() {
        // Mosquito's QueuedJob ships `def initialize; end` (zero-params
        // fallback); the macro-generated `initialize(@attempt_id, ...)` is
        // invisible in source. Arity must go to `param`, not the fallback.
        myFixture.configureByText(
            "test.cr",
            """
            class QueuedJob
              def initialize; end
            end
            class CamTestJob < QueuedJob
              param attempt_id : Int64
            end
            CamTestJob.new(attempt_id: 5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNewMatchesAcrossOverloads() {
        // `Array(String).new(initial_capacity: ...)` must match the
        // `initialize(initial_capacity)` overload, not the empty one
        // (redis commands shape).
        myFixture.configureByText(
            "test.cr",
            """
            class Array(T)
              def initialize
              end
              def initialize(initial_capacity : Int)
              end
            end
            Array(String).new(initial_capacity: 5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testForeignSameNamedInitializeIgnored() {
        // Same-short-name collision (`Error` in two namespaces): a foreign
        // `initialize(ssl, return_code)` must not shadow the hierarchy's
        // own `(message = nil)` overload (redis errors shape).
        myFixture.configureByText(
            "test.cr",
            """
            class SslError
              def initialize(ssl : String, return_code : Int32)
              end
            end
            class Error < Exception
            end
            Error.new("boom")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBareSuperclassResolvesInNamespaceFirst() {
        // `class QueuedJob < Job` inside `module Mosquito` means
        // `Mosquito::Job` — a same-named stdlib `Job` (Benchmark::IPS::Job
        // with its own `initialize`) must not poison the hierarchy walk
        // and shadow `param` constructors (mosquito `.new` red).
        myFixture.addFileToProject(
            "framework.cr",
            """
            module Mosquito
              abstract class Job
              end
              abstract class QueuedJob < Job
                def initialize; end
              end
            end
            class Job
              def initialize(calculation : Int32)
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "app.cr",
            """
            class CamTestJob < Mosquito::QueuedJob
              param attempt_id : Int64
            end
            CamTestJob.new(attempt_id: 5).enqueue
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
