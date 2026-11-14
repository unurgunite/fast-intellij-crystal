package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end regressions for call shapes that are *correct* Crystal and must
 * stay unreported. These come from reported type mismatches that are not
 * defects: a parameterised type passed where its bare class is expected, a
 * generic array literal, a tuple, and a collection that satisfies a module.
 */
class CrystalTypeCheckGenericsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    private fun mismatches(code: String): List<String> {
        myFixture.configureByText("probe.cr", code.trimIndent())
        return myFixture
            .doHighlighting()
            .filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
            .mapNotNull { it.description }
            .filter { it.contains("Type mismatch") }
    }

    fun testParameterisedTypePassedForBareClass() {
        val found =
            mismatches(
                """
                module App
                  class Metadata(T)
                  end

                  class Converter
                    def self.from(meta : App::Metadata)
                      meta
                    end
                  end
                end

                class UseIt
                  def go
                    meta = App::Metadata(Int32).new
                    App::Converter.from meta
                  end
                end
                """,
            )
        assertEquals("a specialised type satisfies its bare class: $found", emptyList<String>(), found)
    }

    fun testGenericArrayLiteralFitsGenericParameter() {
        val found =
            mismatches(
                """
                class Token
                end

                class Route
                  def self.build(tokens : Array(Token))
                    tokens
                  end
                end

                class Builder
                  def go
                    Route.build [Token.new, Token.new]
                  end
                end
                """,
            )
        assertEquals("array literal of the element type must fit: $found", emptyList<String>(), found)
    }

    fun testTypedEmptyArrayLiteralFits() {
        val found =
            mismatches(
                """
                class Holder
                  def self.build(items : Array(String))
                    items
                  end
                end

                class Caller
                  def run
                    Holder.build [] of String
                  end
                end
                """,
            )
        assertEquals("a typed empty literal is that array, not Array(Nil): $found", emptyList<String>(), found)
    }

    fun testTupleLiteralFitsTupleParameter() {
        val found =
            mismatches(
                """
                class Job
                  def self.run(args : {Int64, String})
                    args
                  end
                end

                class Runner
                  def go
                    Job.run({1_i64, "x"})
                  end
                end
                """,
            )
        assertEquals("tuple literal must not be truncated to '{': $found", emptyList<String>(), found)
    }

    fun testCollectionSatisfiesModuleParameter() {
        val found =
            mismatches(
                """
                module Coll
                  def self.count(items : Enumerable(String))
                    items.size
                  end
                end

                class Caller
                  def go
                    Coll.count Set{ "a", "b" }
                  end
                end
                """,
            )
        assertEquals("a Set satisfies Enumerable(String): $found", emptyList<String>(), found)
    }

    /** A real mismatch must still be reported, so the guards above stay honest. */
    fun testRealMismatchIsStillReported() {
        val found =
            mismatches(
                """
                class Holder
                  def self.build(items : Array(String))
                    items
                  end
                end

                class Caller
                  def run
                    Holder.build 42
                  end
                end
                """,
            )
        assertEquals("an Int32 for an Array(String) is still a mismatch", 1, found.size)
    }
}
