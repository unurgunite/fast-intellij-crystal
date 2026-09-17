package io.github.unurgunite.crystal.generation

import com.intellij.lang.CodeInsightActions
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * Tests for Implement Members (P2): abstract discovery across the ancestor
 * chain plus stub generation. The handler is registered for the Crystal
 * language via `com.intellij.codeInsight.implementMethod`.
 */
class CrystalImplementMembersTest : BasePlatformTestCase() {
    private val handler = CrystalImplementMethodsHandler()

    /**
     * Definitions live in a project file (stub-indexed); the caret file holds
     * the usage. `configureByText` files are not reliably indexed, so putting
     * definitions there makes ancestor lookup flaky. Defs-file names are
     * unique per test (fixture `addFileToProject` persists across tests in
     * the class).
     */
    private var defsCounter = 0

    private fun setup(
        defs: String,
        usage: String,
    ) {
        defsCounter++
        if (defs.isNotBlank()) {
            myFixture.addFileToProject("gen_defs$defsCounter.cr", defs.trimIndent())
        }
        myFixture.configureByText("main.cr", usage.trimIndent())
    }

    private fun targetAtCaret(): com.intellij.psi.PsiElement {
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        return CrystalPsiUtils.getEnclosingType(element!!)!!
    }

    fun testHandlerRegisteredForCrystalLanguage() {
        val registered = CodeInsightActions.IMPLEMENT_METHOD.forLanguage(CrystalLanguage)
        assertTrue(
            "CrystalImplementMethodsHandler must be registered, got: $registered",
            registered is CrystalImplementMethodsHandler,
        )
    }

    fun testDiscoversAbstractFromSuperclass() {
        setup(
            """
            abstract class Shape
              abstract def area : Float64
            end
            class Circle < Shape
            end
            """,
            """
            class Circle < Shape
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertEquals(1, missing.size)
        assertEquals("area", missing[0].name)
        assertEquals(0, missing[0].arity)
        assertEquals(" : Float64", missing[0].returnTypeText)
    }

    fun testSkipsAlreadyImplemented() {
        setup(
            """
            abstract class Shape
              abstract def area : Float64
              abstract def name : String
            end
            """,
            """
            class Circle < Shape
              def area : Float64
                3.14
              end
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertEquals(listOf("name"), missing.map { it.name })
    }

    fun testOverloadsMatchedByArity() {
        setup(
            """
            abstract class Base
              abstract def foo(x : Int32) : Int32
              abstract def foo(x : Int32, y : Int32) : Int32
            end
            """,
            """
            class Child < Base
              def foo(x : Int32) : Int32
                x
              end
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertEquals(1, missing.size)
        assertEquals(2, missing[0].arity)
    }

    fun testNearestConcreteWins() {
        setup(
            """
            abstract class Grand
              abstract def run : Nil
            end
            abstract class Middle < Grand
              def run : Nil
              end
            end
            """,
            """
            class Leaf < Middle
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertTrue("Middle#run satisfies Grand#run, got: $missing", missing.isEmpty())
    }

    fun testDiscoversAbstractFromIncludedModule() {
        setup(
            """
            module Comparable2
              abstract def compare(other : Int32) : Int32
            end
            """,
            """
            class Box
              include Comparable2
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertEquals(listOf("compare"), missing.map { it.name })
        assertEquals("(other : Int32)", missing[0].paramsText)
    }

    fun testOperatorNamePreserved() {
        setup(
            """
            module Sortable
              abstract def <=>(other : Int32) : Int32
            end
            """,
            """
            class Item
              include Sortable
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertEquals(listOf("<=>"), missing.map { it.name })
    }

    fun testGeneratedFileHasNoParseErrors() {
        setup(
            """
            abstract class Shape
              abstract def area : Float64
              abstract def name : String
            end
            """,
            """
            class Circle < Shape
            <caret>end
            """,
        )
        val file = myFixture.file
        val target =
            PsiTreeUtil.findChildrenOfType(file, CrystalClassDefinition::class.java).first { it.name == "Circle" }
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(target, project)
        assertEquals(2, missing.size)
        handler.insertForTest(project, myFixture.editor.document, target, missing)
        myFixture.checkResult(
            """
            class Circle < Shape
              def area : Float64
                raise NotImplementedError.new("Circle#area not implemented")
              end
              def name : String
                raise NotImplementedError.new("Circle#name not implemented")
              end
            end
            """.trimIndent(),
        )
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        assertTrue("Generated stubs must parse cleanly, got: $errors", errors.isEmpty())
    }

    fun testNoAbstractsLeavesDocumentUnchanged() {
        myFixture.configureByText(
            "test.cr",
            """
            class Plain
              def foo
              end
              <caret>
            end
            """.trimIndent(),
        )
        val before = myFixture.editor.document.text
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertTrue(missing.isEmpty())
        handler.invoke(project, myFixture.editor, myFixture.file)
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testIsValidForRequiresEnclosingType() {
        setup("", "x = <caret>1\n")
        assertFalse(handler.isValidFor(myFixture.editor, myFixture.file))
        setup(
            "",
            """
            class Foo
              <caret>
            end
            """,
        )
        assertTrue(handler.isValidFor(myFixture.editor, myFixture.file))
    }

    fun testMacroGeneratedNameSkipped() {
        setup(
            """
            abstract class Base
              abstract def {{name}}
            end
            """,
            """
            class Child < Base
              <caret>
            end
            """,
        )
        val missing = CrystalImplementMembersDiscovery.collectUnimplemented(targetAtCaret(), project)
        assertTrue("Macro names cannot be implemented textually, got: $missing", missing.isEmpty())
    }
}
