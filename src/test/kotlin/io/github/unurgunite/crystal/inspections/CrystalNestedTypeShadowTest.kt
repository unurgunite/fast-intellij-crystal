package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A type referenced by its short name inside its own namespace must resolve to
 * that namespace's definition, even when other types share the short name.
 *
 * Real-world shape: `Result.new(rule_id: ..., severity: ...)` written inside
 * `module Catalyst; module Rules; class X < Rule` where `Catalyst::Result` is
 * declared in `module Catalyst; abstract class Rule`. Other shards declare
 * their own unrelated `Result`, and picking one of those reported every named
 * argument as unknown.
 */
class CrystalNestedTypeShadowTest : BasePlatformTestCase() {
    private fun foreignResult() {
        myFixture.addFileToProject(
            "redis/streaming.cr",
            """
            module Redis
              class XReadGroupResponse
                struct Result
                  def initialize(@key : String, @messages : Array(String))
                  end
                end
              end
            end
            """.trimIndent(),
        )
    }

    private fun anotherForeignResult() {
        myFixture.addFileToProject(
            "ameba/liveness.cr",
            """
            module Ameba
              record Result, kind : Symbol
            end
            """.trimIndent(),
        )
    }

    private fun catalystBase() {
        myFixture.addFileToProject(
            "catalyst/rules/base.cr",
            """
            module Catalyst
              abstract class Rule
                struct Result
                  getter rule_id : String
                  getter severity : String

                  def initialize(
                    @rule_id : String,
                    @severity : String,
                    @message : String = "",
                    @file : String = "",
                  )
                  end
                end
              end
            end
            """.trimIndent(),
        )
    }

    private fun ruleCheck() {
        myFixture.configureByText(
            "rule.cr",
            """
            module Catalyst
              module Rules
                class ArrayIncludeToSet < Rule
                  def check
                    [
                      Result.new(
                        rule_id: "id",
                        severity: "warning",
                        message: "msg",
                        file: "f.cr",
                      ),
                    ]
                  end
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection())
        return
    }

    /** Named arguments matching the enclosing namespace's `Result` must be accepted. */
    fun testNamedArgumentsResolveToNamespaceResult() {
        foreignResult()
        anotherForeignResult()
        catalystBase()
        ruleCheck()
        val infos = myFixture.doHighlighting().filter { it.description != null }
        assertEmpty(infos.map { it.description + " at " + it.startOffset }.toString(), infos)
    }

    /** A named argument that exists on no `Result` must still be reported. */
    fun testUnknownNamedArgumentStillReported() {
        foreignResult()
        anotherForeignResult()
        catalystBase()
        myFixture.configureByText(
            "rule.cr",
            """
            module Catalyst
              module Rules
                class BadRule < Rule
                  def check
                    Result.new(rule_id: "id", severity: "warning", bogus: 1)
                  end
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection())
        val infos = myFixture.doHighlighting().filter { it.description?.contains("bogus") == true }
        assertSize(1, infos)
    }

    /** Too many positional arguments must still be reported on the real type. */
    fun testTooManyArgumentsStillReported() {
        foreignResult()
        anotherForeignResult()
        catalystBase()
        myFixture.configureByText(
            "rule.cr",
            """
            module Catalyst
              module Rules
                class ExtraRule < Rule
                  def check
                    Result.new("a", "b", "c", "d", "extra")
                  end
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection())
        val infos = myFixture.doHighlighting().filter { it.description?.contains("Too many") == true }
        // Against the namespace-resolved 4-param initializer only the last
        // argument is surplus (excess args highlight the final one only).
        assertSize(1, infos)
    }
}
