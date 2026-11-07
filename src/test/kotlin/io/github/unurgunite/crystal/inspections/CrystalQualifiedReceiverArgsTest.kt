package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A qualified receiver (`A::B::Methods.call`) must still match candidates whose
 * only owner information is the SHORT enclosing class name from the stub chain,
 * otherwise the receiver's own definition is dropped and an unrelated
 * same-named definition is reported against.
 */
class CrystalQualifiedReceiverArgsTest : BasePlatformTestCase() {
    fun testQualifiedReceiverKeepsItsOwnMethod() {
        myFixture.addFileToProject(
            "methods.cr",
            """
            module Athena::Spec::Methods
              extend self
              def helper(message : String, *, preamble : String = "", line : Int32 = __LINE__) : Nil
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection())
        myFixture.configureByText(
            "t.cr",
            """
            private def helper(message : String, *, line : Int32 = __LINE__) : Nil
              Athena::Spec::Methods.helper message, line: line, preamble: "x"
            end
            """.trimIndent(),
        )
        assertEquals(0, myFixture.doHighlighting().count { it.description != null })
    }

    fun testQualifiedReceiverStillReportsUnknownArgument() {
        myFixture.addFileToProject(
            "methods.cr",
            """
            module Athena::Spec::Methods
              extend self
              def helper(message : String, *, preamble : String = "") : Nil
              end
            end
            """.trimIndent(),
        )
        myFixture.enableInspections(CrystalArgumentCountInspection())
        myFixture.configureByText(
            "t.cr",
            """
            Athena::Spec::Methods.helper "m", bogus: 1
            """.trimIndent(),
        )
        assertTrue(myFixture.doHighlighting().any { it.description?.contains("bogus") == true })
    }
}
