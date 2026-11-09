package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A top-level `def` has no owning type, so it can never be the callee of a
 * qualified receiver. Spec files commonly define a private top-level wrapper
 * (`private def assert_compiles(code, *, line)`) while the real helper lives on
 * a module (`ASPEC::Methods.assert_compiles(code, *, preamble, postamble)`);
 * the wrapper must not shadow the module method.
 */
class CrystalArgumentCountTopLevelHelperTest : BasePlatformTestCase() {
    fun testTopLevelDefDoesNotShadowQualifiedReceiver() {
        myFixture.enableInspections(CrystalArgumentCountInspection())
        myFixture.configureByText(
            "main.cr",
            """
            private def assert_compiles(code : String, *, line : Int32 = __LINE__) : Nil
              code
            end

            private def call_helper(code : String, line : Int32) : Nil
              ASPEC::Methods.assert_compiles code, line: line, preamble: "require \"x\"", postamble: "y"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testTopLevelDefStillChecksUnqualifiedCalls() {
        myFixture.enableInspections(CrystalArgumentCountInspection())
        myFixture.configureByText(
            "main2.cr",
            """
            def helper(code : String, other : Int32) : Nil
              code
            end

            <error descr="Missing required argument(s): 'other'">helper</error> "x"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
