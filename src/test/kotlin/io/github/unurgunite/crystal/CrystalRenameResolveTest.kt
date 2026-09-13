package io.github.unurgunite.crystal

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalReference
import io.github.unurgunite.crystal.psi.CrystalVariableReference

/**
 * Tests for CrystalReference.resolve() promotion and rename of locals,
 * instance/class variables, plus the CrystalNamesValidator.
 *
 * resolve() promotes IDENTIFIER leaf results to their parent composite
 * when it implements PsiNameIdentifierOwner.
 */
class CrystalRenameResolveTest : BasePlatformTestCase() {
    // ==================== CrystalReference.resolve() promotion ====================

    fun testResolveParameterReturnsComposite() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(loud : Bool)
                  puts <caret>loud
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
        assertNotNull("Should resolve", resolved)
        // Should resolve to CrystalParameter (composite), not IDENTIFIER leaf
        assertTrue(
            "Should resolve to CrystalParameter (PsiNameIdentifierOwner)",
            resolved is CrystalParameter,
        )
        assertTrue(
            "Resolved element should be PsiNameIdentifierOwner",
            resolved is PsiNameIdentifierOwner,
        )
    }

    fun testResolveMethodReturnsMethodDefinition() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet
                end
                <caret>greet
                """.trimIndent(),
            )
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef =
            element.parent as? CrystalVariableReference
                ?: error("Expected CrystalVariableReference")
        val ref =
            varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
                ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve", resolved)
        assertTrue(
            "Should resolve to CrystalMethodDefinition",
            resolved is CrystalMethodDefinition,
        )
    }

    fun testResolveClassConstantReturnsClassDefinition() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                end
                x = <caret>Foo.new
                """.trimIndent(),
            )
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef =
            element.parent as? CrystalVariableReference
                ?: error("Expected CrystalVariableReference")
        val ref =
            varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
                ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve", resolved)
        assertTrue(
            "Should resolve to CrystalClassDefinition",
            resolved is CrystalClassDefinition,
        )
    }

    // ==================== resolveLocal() — variable assignment resolution ====================

    fun testResolveLocalFindsVariableAssignment() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet
                  x = 1
                  puts <caret>x
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
        assertNotNull("Should resolve to variable assignment", resolved)
        assertTrue(
            "Should resolve to CrystalAssignment (PsiNameIdentifierOwner)",
            resolved is CrystalAssignment,
        )
        assertTrue(
            "Resolved element should be PsiNameIdentifierOwner",
            resolved is PsiNameIdentifierOwner,
        )
    }

    fun testResolveLocalFindsAssignmentBeforeMultipleStatements() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet
                  name = "world"
                  puts "hello"
                  puts "foo"
                  puts <caret>name
                end
                """.trimIndent(),
            )
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef =
            element.parent as? CrystalVariableReference
                ?: error("Expected CrystalVariableReference")
        val ref =
            varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
                ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve to variable assignment", resolved)
        assertTrue(
            "Should resolve to CrystalAssignment",
            resolved is CrystalAssignment,
        )
    }

    fun testResolveLocalDoesNotCrossMethodBoundary() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def other
                  x = 99
                end
                def greet
                  puts <caret>x
                end
                """.trimIndent(),
            )
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef =
            element.parent as? CrystalVariableReference
                ?: error("Expected CrystalVariableReference")
        val ref =
            varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
                ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        // x is defined in other() — should NOT resolve to it from greet()
        assertNull("Should not resolve to variable in different method", resolved)
    }

    // ==================== E2E Rename Tests ====================

    fun testRenameInstanceVarParameter() {
        myFixture.configureByText(
            "test.cr",
            """
            class Senf
              def initialize(<caret>@testfein : Int32)
                @sahne = @testfein + 4
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("testfein2")
        myFixture.checkResult(
            """
            class Senf
              def initialize(@testfein2 : Int32)
                @sahne = @testfein2 + 4
              end
            end
            """.trimIndent(),
        )
    }

    fun testRenameInstanceVarWithAtPrefix() {
        // Renaming @example to @other — user types just "other", the @ prefix is added automatically
        myFixture.configureByText(
            "test.cr",
            """
            class Senf
              def initialize(<caret>@example : Int32)
                @sahne = @example + 4
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("other")
        myFixture.checkResult(
            """
            class Senf
              def initialize(@other : Int32)
                @sahne = @other + 4
              end
            end
            """.trimIndent(),
        )
    }

    fun testRenameInstanceVarStripsAtPrefix() {
        // User types @crane in dialog — internally "crane" is passed to setName/handleElementRename
        // The @ prefix is always re-applied from the original token type
        myFixture.configureByText(
            "test.cr",
            """
            class Senf
              def initialize(<caret>@sample : Int32)
                @sahne = @sample + 4
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("crane")
        myFixture.checkResult(
            """
            class Senf
              def initialize(@crane : Int32)
                @sahne = @crane + 4
              end
            end
            """.trimIndent(),
        )
    }

    fun testRenameInstanceVarKeepsPrefixEvenIfUserAddsIt() {
        // Verify that setName always applies the correct prefix from token type
        // (Cannot call setName directly — must use WriteCommandAction)
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Senf
                  def initialize(@testfein : Int32)
                  end
                end
                """.trimIndent(),
            )
        val element = file.findElementAt(file.text.indexOf("@testfein")) ?: error("No element")
        val param = element.parent as? com.intellij.psi.PsiNameIdentifierOwner ?: error("Not a named element")
        // getName() should return the full name including @ prefix
        assertEquals("@testfein", param.name)
    }

    fun testRenameInstanceVarUsage() {
        myFixture.configureByText(
            "test.cr",
            """
            class Senf
              def initialize(@testfein : Int32)
                @sahne = <caret>@testfein + 4
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("testfein2")
        myFixture.checkResult(
            """
            class Senf
              def initialize(@testfein2 : Int32)
                @sahne = @testfein2 + 4
              end
            end
            """.trimIndent(),
        )
    }

    fun testRenameLocalVariablePreservesAllOccurrences() {
        myFixture.configureByText(
            "test.cr",
            """
            def greet
              <caret>x = 1
              puts x
              puts x + 1
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("y")
        myFixture.checkResult(
            """
            def greet
              y = 1
              puts y
              puts y + 1
            end
            """.trimIndent(),
        )
    }

    // ==================== Explicit Prefix Tests ====================

    fun testRenameClassVarWithExplicitPrefix() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              <caret>@@example = 1
              def self.test
                puts @@example
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("@@other")
        myFixture.checkResult(
            """
            class Foo
              @@other = 1
              def self.test
                puts @@other
              end
            end
            """.trimIndent(),
        )
    }

    fun testRenameInstanceVarWithExplicitPrefix() {
        myFixture.configureByText(
            "test.cr",
            """
            class Bar
              def initialize(<caret>@ini : Int32)
                @other = @ini + 1
              end
            end
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("@other_ini")
        myFixture.checkResult(
            """
            class Bar
              def initialize(@other_ini : Int32)
                @other = @other_ini + 1
              end
            end
            """.trimIndent(),
        )
    }

    // ==================== CrystalNamesValidator ====================

    fun testNamesValidatorAcceptsAtPrefixedIdentifiers() {
        val validator =
            io.github.unurgunite.crystal.refactoring
                .CrystalNamesValidator()
        assertTrue("@name should be valid", validator.isIdentifier("@name", null))
        assertTrue("@my_var should be valid", validator.isIdentifier("@my_var", null))
        assertTrue("@other123 should be valid", validator.isIdentifier("@other123", null))
        assertTrue("@@class_var should be valid", validator.isIdentifier("@@class_var", null))
        assertFalse("@ should be invalid (no name after @)", validator.isIdentifier("@", null))
        assertFalse("@@ should be invalid (no name after @@)", validator.isIdentifier("@@", null))
        assertFalse("@123 should be invalid (starts with digit)", validator.isIdentifier("@123", null))
    }
}
