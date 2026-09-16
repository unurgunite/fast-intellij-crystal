package io.github.unurgunite.crystal

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.references.CrystalReference

/**
 * Tests for block-parameter rename and resolve, plus the default
 * rename processor check for Crystal elements.
 */
class CrystalRenameBlockParameterTest : BasePlatformTestCase() {
    // ==================== Block Parameter Rename Tests ====================

    fun testRenameBlockParameterFromDefinition() {
        myFixture.configureByText(
            "test.cr",
            """
            [1, 2, 3].each do |<caret>ola|
              puts ola
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("element")
        myFixture.checkResult(
            """
            [1, 2, 3].each do |element|
              puts element
            end
            """.trimIndent(),
        )
    }

    fun testRenameBlockParameterFromUsage() {
        myFixture.configureByText(
            "test.cr",
            """
            [1, 2, 3].each do |ola|
              puts <caret>ola
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("element")
        myFixture.checkResult(
            """
            [1, 2, 3].each do |element|
              puts element
            end
            """.trimIndent(),
        )
    }

    fun testRenameBlockParameterMultipleParams() {
        myFixture.configureByText(
            "test.cr",
            """
            [1, 2].each_with_index do |<caret>elem, idx|
              puts elem
              puts idx
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("item")
        myFixture.checkResult(
            """
            [1, 2].each_with_index do |item, idx|
              puts item
              puts idx
            end
            """.trimIndent(),
        )
    }

    fun testRenameBlockParameterCurlyBrace() {
        myFixture.configureByText(
            "test.cr",
            """
            [1, 2, 3].map { |<caret>n| n * 2 }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("num")
        myFixture.checkResult(
            """
            [1, 2, 3].map { |num| num * 2 }
            """.trimIndent(),
        )
    }

    fun testRenameBlockParameterNestedBlocks() {
        myFixture.configureByText(
            "test.cr",
            """
            [1, 2].each do |outer|
              [3, 4].each do |<caret>inner|
                puts outer
                puts inner
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("x")
        myFixture.checkResult(
            """
            [1, 2].each do |outer|
              [3, 4].each do |x|
                puts outer
                puts x
              end
            end
            """.trimIndent(),
        )
    }

    fun testRenameBlockParameterNestedBlocksUsage() {
        myFixture.configureByText(
            "test.cr",
            """
            [1, 2].each do |outer|
              [3, 4].each do |inner|
                puts outer
                puts <caret>inner
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("x")
        myFixture.checkResult(
            """
            [1, 2].each do |outer|
              [3, 4].each do |x|
                puts outer
                puts x
              end
            end
            """.trimIndent(),
        )
    }

    fun testResolveBlockParameter() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                [1, 2, 3].each do |ola|
                  puts <caret>ola
                end
                """.trimIndent(),
            )
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef =
            element.parent as? CrystalVariableReference
                ?: error("Expected CrystalVariableReference, got ${element.parent?.javaClass?.simpleName}")
        val ref =
            varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
                ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve to block parameter", resolved)
        assertTrue(
            "Should resolve to CrystalParameter (PsiNameIdentifierOwner)",
            resolved is CrystalParameter,
        )
        assertTrue(
            "Resolved element should be PsiNameIdentifierOwner",
            resolved is PsiNameIdentifierOwner,
        )
    }

    fun testRenameUsesDefaultProcessorForAllElements() {
        // Without a custom renamePsiElementProcessor, all Crystal elements
        // use the DEFAULT processor. This is the current design: leaf tokens
        // are handled by TokenInplaceRenameHandler (token-based), and
        // composites with PsiNameIdentifierOwner are handled by
        // MemberInplaceRenameHandler via the DEFAULT processor.
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet
                  <caret>x = 1
                end
                """.trimIndent(),
            )
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val processor =
            com.intellij.refactoring.rename.RenamePsiElementProcessor
                .forElement(element)
        assertTrue(
            "Should use DEFAULT processor",
            processor === com.intellij.refactoring.rename.RenamePsiElementProcessor.DEFAULT,
        )
    }
}
