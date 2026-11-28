package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A ternary whose branches cannot both be resolved used to fall through to
 * first-child descent and take the condition's type (`flag(p) ? LIMIT : TABLE[0]`
 * typed Bool). See `CrystalControlFlowTypeResolver.resolveCompoundExpression`.
 */
class CrystalTernaryTypeTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testTernaryWithUnresolvableBranchIsNotTypedAsCondition() {
        val found =
            mismatchDescriptions(
                """
                LIMIT = 10
                TABLE = {0 => "x"}

                def flag(p : String) : Bool
                  true
                end

                def take(s : String)
                end

                take(flag(p) ? LIMIT : TABLE[0])
                """,
            )
        assertEquals(emptyList<String>(), found)
    }

    fun testTernaryBranchMismatchIsStillReported() {
        val found =
            mismatchDescriptions(
                """
                def flag(p : String) : Bool
                  true
                end

                def take(n : Int32)
                end

                take(flag(p) ? "a" : "b")
                """,
            )
        assertEquals(1, found.size)
    }

    private fun mismatchDescriptions(code: String): List<String> {
        myFixture.configureByText("test.cr", code.trimIndent())
        return myFixture
            .doHighlighting()
            .filter { it.description?.contains("Type mismatch") == true }
            .map { it.description!! }
    }
}
