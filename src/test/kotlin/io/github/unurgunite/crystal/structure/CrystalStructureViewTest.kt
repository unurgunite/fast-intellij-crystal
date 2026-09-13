package io.github.unurgunite.crystal.structure

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalStructureViewTest : BasePlatformTestCase() {

    private fun rootChildren(code: String): List<String> {
        val file = myFixture.configureByText("test.cr", code)
        val root = CrystalStructureViewElement(file)
        return root.children.map { it.presentation.presentableText ?: "?" }
    }

    fun testTopLevelDefinitionsListed() {
        val names = rootChildren("""
class Foo
end

module Bar
end

def top_method
end

CONSTANT = 1
        """.trimIndent())
        assertTrue("Foo in $names", "Foo" in names)
        assertTrue("Bar in $names", "Bar" in names)
        // Methods render with signature: name(params)
        assertTrue("top_method() in $names", "top_method()" in names)
        assertTrue("CONSTANT in $names", "CONSTANT" in names)
    }

    fun testClassMembersNested() {
        val file = myFixture.configureByText("test.cr", """
class Foo
  def bar
  end

  def baz
  end
end
        """.trimIndent())
        val root = CrystalStructureViewElement(file)
        assertEquals(1, root.children.size)
        assertEquals("Foo", root.children[0].presentation.presentableText)
        val members = root.children[0].children.map { it.presentation.presentableText }
        assertEquals(setOf("bar()", "baz()"), members.toSet())
    }

    fun testEnumConstantsListed() {
        val file = myFixture.configureByText("test.cr", """
enum Color
  Red
  Green
end
        """.trimIndent())
        val root = CrystalStructureViewElement(file)
        assertEquals(1, root.children.size)
        val members = root.children[0].children.map { it.presentation.presentableText }
        assertTrue("Red in $members", "Red" in members)
        assertTrue("Green in $members", "Green" in members)
    }

    fun testEmptyFileHasNoChildren() {
        val file = myFixture.configureByText("test.cr", "x = 1\n")
        assertTrue(CrystalStructureViewElement(file).children.isEmpty())
    }
}
