package io.github.unurgunite.crystal.stubs

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement

class CrystalStubByClassTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun methodsOf(className: String): List<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.allScope(project)
        return StubIndex.getElements(
            CrystalMethodByClassIndex.KEY, className, project, scope,
            CrystalMethodDefinition::class.java
        ).toList()
    }

    fun testMethodByClassIndexForSimpleFile() {
        myFixture.addFileToProject("test.cr", """
class Foo
  def bar
  end
  def baz(x : Int32)
  end
end
        """.trimIndent())

        val names = methodsOf("Foo").mapNotNull { it.name }.toSet()
        assertEquals("Foo should expose exactly bar and baz", setOf("bar", "baz"), names)
    }

    fun testMethodByClassIndexIsScopedPerClass() {
        myFixture.addFileToProject("two.cr", """
class Foo
  def foo_method
  end
end

class Bar
  def bar_method
  end
end
        """.trimIndent())

        assertEquals(
            setOf("foo_method"),
            methodsOf("Foo").mapNotNull { it.name }.toSet()
        )
        assertEquals(
            setOf("bar_method"),
            methodsOf("Bar").mapNotNull { it.name }.toSet()
        )
    }

    fun testMethodByClassIndexMissesUnknownClass() {
        myFixture.addFileToProject("test.cr", """
class Foo
  def bar
  end
end
        """.trimIndent())

        assertTrue(
            "Unknown class must yield no methods",
            methodsOf("NoSuchClass").isEmpty()
        )
    }

    fun testMethodIndexHasBareMethods() {
        myFixture.addFileToProject("test.cr", """
def top_level
end

class Foo
  def bar
  end
end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val bar = StubIndex.getElements(
            CrystalMethodIndex.KEY, "bar", project, scope,
            CrystalMethodDefinition::class.java
        )
        assertEquals(1, bar.size)
        val topLevel = StubIndex.getElements(
            CrystalMethodIndex.KEY, "top_level", project, scope,
            CrystalMethodDefinition::class.java
        )
        assertEquals(1, topLevel.size)
    }

    fun testClassIndexFindsClass() {
        myFixture.addFileToProject("test.cr", """
class Foo
end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val found = StubIndex.getElements(
            CrystalClassIndex.KEY, "Foo", project, scope,
            CrystalNamedElement::class.java
        )
        assertEquals(1, found.size)
        assertEquals("Foo", found.first().name)
    }

    fun testClassByEnclosingIndexFindsNestedType() {
        myFixture.addFileToProject("test.cr", """
class Foo
  class Sub
  end
end
        """.trimIndent())

        val scope = GlobalSearchScope.allScope(project)
        val nested = StubIndex.getElements(
            CrystalClassByEnclosingIndex.KEY, "Foo", project, scope,
            CrystalNamedElement::class.java
        ).mapNotNull { it.name }.toSet()
        assertTrue("Sub should be listed as nested in Foo, got: $nested", "Sub" in nested)
    }
}
