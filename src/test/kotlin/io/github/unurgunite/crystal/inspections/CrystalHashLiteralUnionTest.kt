package io.github.unurgunite.crystal.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * `CrystalCollectionTypeResolver.resolveHashLiteral` renders a hash literal whose
 * values differ in type as `Hash(K, V | W)`. Joining the entry types without
 * de-duplication put a duplicate union in the key slot (`Hash(String | String,
 * ...)`), a malformed type that reported a valid literal as a mismatch.
 */
class CrystalHashLiteralUnionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testHashLiteralMixedValueTypesFitsUnionValueParameter() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(h : Hash(String, String | Int32))
            end
            foo({"a" => "b", "c" => 10})
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** The rendered `got` type keeps a single key type, not `String | String`. */
    fun testHashLiteralMixedValueTypesRendersKeyOnce() {
        val found =
            mismatchDescriptions(
                """
                def foo(h : Hash(Symbol, Int32))
                end
                foo({"a" => "b", "c" => 10})
                """,
            )
        assertTrue("got-type must be Hash(String, String | Int32): $found", found.any { it.contains("got 'Hash(String, String | Int32)'") })
    }

    private fun mismatchDescriptions(code: String): List<String> {
        myFixture.configureByText("probe.cr", code.trimIndent())
        return myFixture
            .doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR }
            .mapNotNull { it.description }
            .filter { it.contains("Type mismatch") }
    }
}
