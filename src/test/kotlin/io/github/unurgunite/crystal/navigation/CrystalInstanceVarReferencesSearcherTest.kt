package io.github.unurgunite.crystal.navigation

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess

class CrystalInstanceVarReferencesSearcherTest : BasePlatformTestCase() {
    private val searcher = CrystalInstanceVarReferencesSearcher()

    private fun accesses(
        code: String,
        varName: String,
    ): List<com.intellij.psi.PsiElement> {
        val file = myFixture.configureByText("test.cr", code)
        return PsiTreeUtil
            .collectElements(file) {
                (it is CrystalInstanceVarAccess || it is CrystalClassVarAccess) && it.text == varName
            }.toList()
    }

    /**
     * Queries ONLY our searcher (not the platform's default word search),
     * so counts precisely reflect this class's behavior.
     */
    private fun searcherRefs(target: com.intellij.psi.PsiElement): List<com.intellij.psi.PsiReference> {
        val refs = mutableListOf<com.intellij.psi.PsiReference>()
        val params =
            ReferencesSearch.SearchParameters(
                target,
                com.intellij.psi.search.GlobalSearchScope
                    .fileScope(myFixture.file),
                false,
            )
        searcher.processQuery(
            params,
            com.intellij.util.Processor {
                refs.add(it)
                true
            },
        )
        return refs
    }

    fun testFindUsagesFindsReadsAndWrites() {
        val found =
            accesses(
                """
class Foo
  def m
    @x = 1
    puts @x
    @x = @x + 1
  end
end
                """.trimIndent(),
                "@x",
            )
        assertEquals(4, found.size)
        val usages = searcherRefs(found[0])
        // All accesses except the target itself
        assertEquals(3, usages.size)
        val usageTexts = usages.map { it.element?.text }.toSet()
        assertTrue("@x" in usageTexts)
    }

    fun testFindUsagesIgnoresNestedClass() {
        val found =
            accesses(
                """
class Outer
  def m
    @x = 1
    puts @x
  end

  class Inner
    def n
      @x = 2
    end
  end
end
                """.trimIndent(),
                "@x",
            )
        assertEquals(3, found.size)
        val usages = searcherRefs(found[0])
        assertEquals("Inner's @x must not leak into Outer's usages", 1, usages.size)
    }

    fun testFindUsagesOnPlainIdentifierIsEmpty() {
        val file = myFixture.configureByText("test.cr", "y = <caret>1\n")
        val element = file.findElementAt(myFixture.caretOffset)!!
        // Non-var target: searcher bails, word index finds nothing new in isolation
        val usages = ReferencesSearch.search(element).findAll()
        assertTrue(usages.isEmpty())
    }

    fun testFindUsagesClassVariable() {
        val found =
            accesses(
                """
class Foo
  @@count = 0

  def self.n
    @@count += 1
    puts @@count
  end
end
                """.trimIndent(),
                "@@count",
            )
        assertEquals(3, found.size)
        val usages = searcherRefs(found[0])
        assertEquals(2, usages.size)
    }
}
