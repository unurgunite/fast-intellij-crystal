package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A relative qualified parent (`Rule::Base` written inside `module Ameba`)
 * must resolve to `Ameba::Rule::Base`. Falling through to a bare-name lookup
 * grabbed an unrelated `Base` and pulled its initializer into every `.new`.
 */
class CrystalRelativeParentTest : BasePlatformTestCase() {
    fun testRelativeQualifiedParentStaysInItsNamespace() {
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
            "src/ameba/base.cr",
            """
            module Ameba
              class Rule
                class Base
                  def initialize(@rule : String, @source : String)
                  end
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        myFixture.configureByText(
            "spec.cr",
            """
            module Ameba
              class ScopeRule < Rule::Base
              end

              rule = "x"
              source = "y"
              ScopeRule.new(rule, source)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun testRelativeQualifiedParentStillReportsRealMismatch() {
        myFixture.addFileToProject(
            "src/ameba/base.cr",
            """
            module Ameba
              class Rule
                class Base
                  def initialize(@rule : String, @source : String)
                  end
                end
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        myFixture.configureByText(
            "spec.cr",
            """
            module Ameba
              class ScopeRule < Rule::Base
              end

              rule = "x"
              source = "y"
              ScopeRule.new(rule, source, <error descr="Too many arguments: expected at most 2, got 3">:extra</error>)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting(true, false, true)
    }
}
