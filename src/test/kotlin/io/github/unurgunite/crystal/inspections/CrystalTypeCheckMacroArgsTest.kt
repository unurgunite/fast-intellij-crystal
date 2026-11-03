package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Named arguments of helper defs whose keyword-only params follow a bare `*`. */
class CrystalTypeCheckMacroArgsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(
            CrystalArgumentCountInspection::class.java,
            CrystalTypeCheckInspection::class.java,
        )
    }

    fun testKeywordOnlyNamedArgAfterBareStarIsKnown() {
        myFixture.configureByText(
            "test.cr",
            """
            module ASPEC
              module Methods
                def self.assert_compiles(code : String, *, preamble : String = "", postamble : String = "")
                end
              end
            end
            ASPEC::Methods.assert_compiles "x", preamble: %(require "./spec_helper.cr")
            ASPEC::Methods.assert_compiles "x", postamble: "ATH.run"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testKeywordOnlyNamedArgOnPlainDef() {
        myFixture.configureByText(
            "test.cr",
            """
            def build(code : String, *, skip : Symbol? = nil, output : String? = nil)
            end
            build "x", skip: :macro
            build "x", output: "out", skip: nil
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** `select`-style helper with an enum-typed keyword-only param. */
    fun testKeywordOnlySymbolEnumParam() {
        myFixture.configureByText(
            "test.cr",
            """
            class Visitor
              @[Flags]
              enum Category
                Macro
              end

              def initialize(@rule : String, @source : String, *, skip : Category)
              end
            end
            Visitor.new "r", "s", skip: :macro
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** A record declared with a `do ... end` body still takes its parameters. */
    fun testRecordWithBodyAcceptsNamedArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            module Mts
              class WebFlow
                record Launch, id_short : String, ott : String do
                  def to_s(io : IO) : Nil
                    io << "Launch"
                  end
                end
              end
            end
            Mts::WebFlow::Launch.new(id_short: "a", ott: "b")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** Qualified receivers must not resolve to an equally-named foreign class. */
    fun testQualifiedConstructorUsesItsOwnParameters() {
        myFixture.configureByText(
            "test.cr",
            """
            module Crystal
              class NumberLiteral
                def initialize(@value : Int32)
                end
              end
            end
            module Other
              class NumberLiteral
                def initialize(@location : String)
                end
              end
            end
            Crystal::NumberLiteral.new(42)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
