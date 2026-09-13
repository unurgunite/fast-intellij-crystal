package io.github.unurgunite.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for CrystalCompletionContributor: free-text completion and suppression.
 */
class CrystalCompletionTest : BasePlatformTestCase() {
    // ==================== Free-text completion ====================

    fun testCompletesClassNames() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\nclass Aprikose\nend\n")
        myFixture.configureByText("main.cr", "x = Ap<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Apfel", names.contains("Apfel"))
        assertTrue("Should contain Aprikose", names.contains("Aprikose"))
    }

    fun testCompletesLocalVariables() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo
              meine_variable = 42
              mein_anderes = 99
              mein<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups!!.map { it.lookupString }
        assertTrue("Should contain meine_variable", names.contains("meine_variable"))
        assertTrue("Should contain mein_anderes", names.contains("mein_anderes"))
    }

    fun testCompletesParameters() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo(apfel_param : String, aprikose_param : Int32)
              ap<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain apfel_param", names.contains("apfel_param"))
        assertTrue("Should contain aprikose_param", names.contains("aprikose_param"))
    }

    fun testCompletesShorthandInstanceVarParameters() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo(@apfel_param : String, @aprikose_param : Int32)
              ap<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain apfel_param (without @)", names.contains("apfel_param"))
        assertTrue("Should contain aprikose_param (without @)", names.contains("aprikose_param"))
    }

    // ==================== Edge cases ====================

    fun testNoCompletionsInEmptyFile() {
        myFixture.configureByText("main.cr", "<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        // Should not crash — may return null (auto-inserted) or empty
        // Just verifying no exception is thrown
    }

    fun testStdlibTypesInFreeTextCompletion() {
        myFixture.configureByText(
            "test.cr",
            """
            x = <caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Array from stdlib", names.contains("Array"))
        assertTrue("Should contain String from stdlib", names.contains("String"))
    }

    fun testNoCompletionInsideStringLiteral() {
        myFixture.configureByText(
            "test.cr",
            """
            x = "hello <caret>"
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertTrue("Should NOT offer completions inside string", lookups == null || lookups.isEmpty())
    }

    // ==================== Suppression after numeric literals ====================

    fun testNoCompletionAfterIntegerLiteral() {
        myFixture.configureByText("main.cr", "a = 1<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertTrue("Should NOT offer completions after integer literal", lookups == null || lookups.isEmpty())
    }

    fun testNoCompletionAfterFloatLiteral() {
        myFixture.configureByText("main.cr", "a = 1.5<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertTrue("Should NOT offer completions after float literal", lookups == null || lookups.isEmpty())
    }

    fun testCompletionStillWorksAfterNewline() {
        myFixture.configureByText(
            "main.cr",
            """
            a = 1
            b<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer completions after newline", lookups)
    }
}
