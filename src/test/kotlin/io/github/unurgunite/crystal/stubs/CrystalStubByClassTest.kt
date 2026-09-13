package io.github.unurgunite.crystal.stubs

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalConstantAssignment
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement

class CrystalStubByClassTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun methodsOf(className: String): List<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.allScope(project)
        return StubIndex
            .getElements(
                CrystalMethodByClassIndex.KEY,
                className,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            ).toList()
    }

    fun testMethodByClassIndexForSimpleFile() {
        myFixture.addFileToProject(
            "test.cr",
            """
class StubWidget
  def widget_alpha
  end
  def widget_beta(x : Int32)
  end
end
            """.trimIndent(),
        )

        val names = methodsOf("StubWidget").mapNotNull { it.name }.toSet()
        assertEquals("StubWidget should expose exactly widget_alpha and widget_beta", setOf("widget_alpha", "widget_beta"), names)
    }

    fun testMethodByClassIndexIsScopedPerClass() {
        myFixture.addFileToProject(
            "two.cr",
            """
class StubWidget
  def widget_a_method
  end
end

class StubWidgetB
  def widget_b_method
  end
end
            """.trimIndent(),
        )

        assertEquals(
            setOf("widget_a_method"),
            methodsOf("StubWidget").mapNotNull { it.name }.toSet(),
        )
        assertEquals(
            setOf("widget_b_method"),
            methodsOf("StubWidgetB").mapNotNull { it.name }.toSet(),
        )
    }

    fun testMethodByClassIndexMissesUnknownClass() {
        myFixture.addFileToProject(
            "test.cr",
            """
class StubWidget
  def widget_alpha
  end
end
            """.trimIndent(),
        )

        assertTrue(
            "Unknown class must yield no methods",
            methodsOf("NoSuchClass").isEmpty(),
        )
    }

    fun testMethodIndexHasBareMethods() {
        myFixture.addFileToProject(
            "test.cr",
            """
def widget_top_level
end

class StubWidget
  def widget_alpha
  end
end
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val widgetAlpha =
            StubIndex.getElements(
                CrystalMethodIndex.KEY,
                "widget_alpha",
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        assertEquals(1, widgetAlpha.size)
        val topLevel =
            StubIndex.getElements(
                CrystalMethodIndex.KEY,
                "widget_top_level",
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        assertEquals(1, topLevel.size)
    }

    fun testClassIndexFindsClass() {
        myFixture.addFileToProject(
            "test.cr",
            """
class StubWidget
end
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val found =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                "StubWidget",
                project,
                scope,
                CrystalNamedElement::class.java,
            )
        assertEquals(1, found.size)
        assertEquals("StubWidget", found.first().name)
    }

    fun testClassByEnclosingIndexFindsNestedType() {
        myFixture.addFileToProject(
            "test.cr",
            """
class StubWidget
  class StubWidgetInner
  end
end
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val nested =
            StubIndex
                .getElements(
                    CrystalClassByEnclosingIndex.KEY,
                    "StubWidget",
                    project,
                    scope,
                    CrystalNamedElement::class.java,
                ).mapNotNull { it.name }
                .toSet()
        assertTrue("Sub should be listed as nested in StubWidget, got: $nested", "StubWidgetInner" in nested)
    }

    fun testMacroIndexFindsMacro() {
        myFixture.addFileToProject(
            "test.cr",
            """
macro stub_widget_macro_xyz
end
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val found =
            StubIndex.getElements(
                CrystalMacroIndex.KEY,
                "stub_widget_macro_xyz",
                project,
                scope,
                CrystalMacroDefinition::class.java,
            )
        assertEquals(1, found.size)
        assertEquals("stub_widget_macro_xyz", found.first().name)
    }

    fun testConstantIndexFindsConstant() {
        myFixture.addFileToProject(
            "test.cr",
            """
STUB_WIDGET_CONST_XYZ = 0o644
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        val found =
            StubIndex.getElements(
                CrystalConstantIndex.KEY,
                "STUB_WIDGET_CONST_XYZ",
                project,
                scope,
                CrystalConstantAssignment::class.java,
            )
        assertEquals(1, found.size)
    }

    fun testConstantIndexMissesUnknown() {
        myFixture.addFileToProject(
            "test.cr",
            """
STUB_WIDGET_OTHER = 1
            """.trimIndent(),
        )

        val scope = GlobalSearchScope.allScope(project)
        assertTrue(
            StubIndex
                .getElements(
                    CrystalConstantIndex.KEY,
                    "NO_SUCH_STUB_CONST_XYZ",
                    project,
                    scope,
                    CrystalConstantAssignment::class.java,
                ).isEmpty(),
        )
    }
}
