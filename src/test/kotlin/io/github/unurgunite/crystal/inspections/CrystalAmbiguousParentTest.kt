package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A bare parent name declared in several namespaces has no single definition.
 * Picking one of them silently gave the subclass a foreign parent, so its
 * `.new` was checked against an unrelated initializer.
 */
class CrystalAmbiguousParentTest : BasePlatformTestCase() {
    private fun addSameNamedBases() {
        myFixture.addFileToProject(
            "src/mts/versions.cr",
            """
            module Mts
              module Api
                struct Base
                  def initialize(@family : String, @generation : Int32, @host : String, @prefix : String)
                  end
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/lint/base.cr",
            """
            module Lint
              class Base
                def initialize(@rule : String, @source : String)
                end
              end
            end
            """.trimIndent(),
        )
    }

    fun testAmbiguousBareParentDoesNotInventArguments() {
        addSameNamedBases()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        myFixture.configureByText(
            "spec.cr",
            """
            class ScopeRule < Base
            end

            ScopeRule.new("a", "b")
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testUnambiguousParentStillResolves() {
        myFixture.addFileToProject(
            "src/only/base.cr",
            """
            module Only
              class Base
                def initialize(@rule : String, @source : String)
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        myFixture.configureByText(
            "spec.cr",
            """
            class ScopeRule < Only::Base
            end

            ScopeRule.new("a", "b", <error descr="Too many arguments: expected at most 2, got 3">:extra</error>)
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }
}
