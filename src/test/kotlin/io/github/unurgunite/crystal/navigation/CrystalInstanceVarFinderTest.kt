package io.github.unurgunite.crystal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInstanceVarFinderTest : BasePlatformTestCase() {
    private fun contextAtCaret(): com.intellij.psi.PsiElement {
        val file = myFixture.file
        val element = file.findElementAt(myFixture.editor.caretModel.offset)
        assertNotNull("No element at caret", element)
        return element!!
    }

    fun testAssignmentFallback() {
        myFixture.configureByText(
            "test.cr",
            """
class Foo
  def m
    @x = 1
    puts @x<caret>
  end
end
            """.trimIndent(),
        )

        val targets = CrystalInstanceVarFinder.findDefinitionTargets("@x", contextAtCaret())
        assertEquals(1, targets.size)
        assertTrue("Target should be the assignment", targets[0].text.startsWith("@x"))
    }

    fun testFindAllUsagesCountsReadsAndWrites() {
        myFixture.configureByText(
            "test.cr",
            """
class Foo
  def m
    @x = 1
    puts @x<caret>
  end
end
            """.trimIndent(),
        )

        val usages = CrystalInstanceVarFinder.findAllUsages("@x", contextAtCaret())
        assertEquals("Write + read = 2 usages", 2, usages.size)
    }

    fun testGetterMacroBeatsAssignment() {
        myFixture.configureByText(
            "test.cr",
            """
class Foo
  getter name

  def m
    @name = "x"
    puts @name<caret>
  end
end
            """.trimIndent(),
        )

        val targets = CrystalInstanceVarFinder.findDefinitionTargets("@name", contextAtCaret())
        assertFalse("Should resolve somewhere", targets.isEmpty())
        assertTrue(
            "Getter macro should win over assignment, got: ${targets[0].text}",
            targets[0].text.startsWith("getter"),
        )
    }

    fun testPropertyDeclarationBeatsAssignment() {
        myFixture.configureByText(
            "test.cr",
            """
class Foo
  @size : Int32

  def m
    @size = 1
    puts @size<caret>
  end
end
            """.trimIndent(),
        )

        val targets = CrystalInstanceVarFinder.findDefinitionTargets("@size", contextAtCaret())
        assertFalse("Should resolve somewhere", targets.isEmpty())
        assertTrue(
            "Property declaration should win, got: ${targets[0].text}",
            targets[0].text.contains("@size") && targets[0].text.contains("Int32"),
        )
    }

    fun testNestedClassIsolation() {
        myFixture.configureByText(
            "test.cr",
            """
class Outer
  def m
    @x = 1
  end

  class Inner
    def n
      @x = 2
      puts @x<caret>
    end
  end
end
            """.trimIndent(),
        )

        val targets = CrystalInstanceVarFinder.findDefinitionTargets("@x", contextAtCaret())
        assertEquals(1, targets.size)
        assertEquals("@x", targets[0].text)
        assertTrue(
            "Should resolve to Inner's assignment, got: ${targets[0].parent.text}",
            targets[0].parent.text.contains("= 2"),
        )
    }

    fun testNoEnclosingClassReturnsEmpty() {
        myFixture.configureByText("test.cr", "@x = <caret>1\n")
        val context = contextAtCaret()
        assertTrue(CrystalInstanceVarFinder.findDefinitionTargets("@x", context).isEmpty())
        assertTrue(CrystalInstanceVarFinder.findAllUsages("@x", context).isEmpty())
    }

    fun testClassVariable() {
        myFixture.configureByText(
            "test.cr",
            """
class Foo
  @@count = 0

  def self.n
    puts @@count<caret>
  end
end
            """.trimIndent(),
        )

        val targets = CrystalInstanceVarFinder.findDefinitionTargets("@@count", contextAtCaret())
        assertEquals(1, targets.size)
    }
}
