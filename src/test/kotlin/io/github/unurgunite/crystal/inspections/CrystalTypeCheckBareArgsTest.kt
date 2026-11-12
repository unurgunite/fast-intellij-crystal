package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unparenthesized dot-call arguments hold their value expression directly in
 * a BARE_ARGUMENT wrapper. A ternary there must resolve to its branch type,
 * not to the condition (`sep ? ch : ""` was typed Bool).
 */
class CrystalTypeCheckBareArgsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testTernaryArgumentResolvesToBranchType() {
        myFixture.configureByText(
            "test.cr",
            """
            module ART
              class Token
                def initialize(kind : Symbol, value : String)
                end
              end
            end
            sep = true
            ch = "a"
            ART::Token.new :text, sep ? ch : ""
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
