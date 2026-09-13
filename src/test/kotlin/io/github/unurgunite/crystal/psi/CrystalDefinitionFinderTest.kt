package io.github.unurgunite.crystal.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalDefinitionFinderTest : BasePlatformTestCase() {
    fun testFindsClassByName() {
        myFixture.addFileToProject(
            "types.cr",
            """
class FinderWidget
end
            """.trimIndent(),
        )
        val found = CrystalDefinitionFinder.findDefinitions("FinderWidget", project)
        assertEquals(1, found.size)
        assertTrue(found[0] is CrystalClassDefinition)
    }

    fun testFindsMethodByName() {
        myFixture.addFileToProject(
            "code.cr",
            """
class FinderWidget
  def finder_action
  end
end
            """.trimIndent(),
        )
        val found = CrystalDefinitionFinder.findDefinitions("finder_action", project)
        assertEquals(1, found.size)
        assertTrue(found[0] is CrystalMethodDefinition)
    }

    fun testFindsTypeAndMethodSharingName() {
        myFixture.addFileToProject(
            "both.cr",
            """
class SharedWidget
end

def SharedWidget
end
            """.trimIndent(),
        )
        // Class and method live in different indices — both must surface
        val found = CrystalDefinitionFinder.findDefinitions("SharedWidget", project)
        assertEquals(2, found.size)
    }

    fun testUnknownNameYieldsEmpty() {
        myFixture.addFileToProject(
            "code.cr",
            """
class FinderWidget
end
            """.trimIndent(),
        )
        assertTrue(CrystalDefinitionFinder.findDefinitions("NoSuchFinderThing", project).isEmpty())
    }
}
