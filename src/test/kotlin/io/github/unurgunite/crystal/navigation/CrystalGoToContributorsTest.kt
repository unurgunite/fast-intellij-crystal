package io.github.unurgunite.crystal.navigation

import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters

class CrystalGoToContributorsTest : BasePlatformTestCase() {

    private val classContributor = CrystalGoToClassContributor()
    private val symbolContributor = CrystalGoToSymbolContributor()

    private fun collectNames(contributor: com.intellij.navigation.ChooseByNameContributorEx): Set<String> {
        val names = mutableSetOf<String>()
        contributor.processNames(
            Processor { names.add(it); true },
            GlobalSearchScope.allScope(project),
            null
        )
        return names
    }

    private fun collectElements(
        contributor: com.intellij.navigation.ChooseByNameContributorEx,
        name: String
    ): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()
        contributor.processElementsWithName(
            name,
            Processor { items.add(it); true },
            FindSymbolParameters(name, name, GlobalSearchScope.allScope(project))
        )
        return items
    }

    fun testClassContributorCollectsAllTypeKinds() {
        myFixture.addFileToProject("types.cr", """
class Foo
end

module Bar
end

struct Baz
end

enum Qux
  A
end
        """.trimIndent())

        val names = collectNames(classContributor)
        assertTrue("Foo in $names", "Foo" in names)
        assertTrue("Bar in $names", "Bar" in names)
        assertTrue("Baz in $names", "Baz" in names)
        assertTrue("Qux in $names", "Qux" in names)
    }

    fun testClassContributorResolvesElement() {
        myFixture.addFileToProject("types.cr", """
class Foo
end
        """.trimIndent())

        val items = collectElements(classContributor, "Foo")
        assertEquals(1, items.size)
        assertEquals("Foo", items[0].name)
    }

    fun testClassContributorMissesUnknown() {
        myFixture.addFileToProject("types.cr", """
class Foo
end
        """.trimIndent())

        assertTrue(collectElements(classContributor, "Nope").isEmpty())
    }

    fun testSymbolContributorIncludesMethodsAndMacros() {
        myFixture.addFileToProject("code.cr", """
class Foo
  def some_method
  end
end

macro helper_macro
end
        """.trimIndent())

        val names = collectNames(symbolContributor)
        assertTrue("Foo in $names", "Foo" in names)
        assertTrue("some_method in $names", "some_method" in names)
        assertTrue("helper_macro in $names", "helper_macro" in names)
    }

    fun testSymbolContributorResolvesMethod() {
        myFixture.addFileToProject("code.cr", """
class Foo
  def some_method
  end
end
        """.trimIndent())

        val items = collectElements(symbolContributor, "some_method")
        assertEquals(1, items.size)
        assertEquals("some_method", items[0].name)
    }
}
