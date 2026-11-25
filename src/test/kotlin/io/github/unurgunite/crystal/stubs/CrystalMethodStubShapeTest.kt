package io.github.unurgunite.crystal.stubs

import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.type.CrystalMethodLookup

/**
 * A StubIndex hit must answer a method's header facts from the stub alone.
 * Reading them off the AST (`getTypePath()` and friends go through
 * `PsiTreeUtil.getChildOfType`) throws `UpToDateStubIndexMismatch` when the
 * index holds stubs built by an older parser, which is how
 * `CrystalTypeCheckInspection` used to crash on `spec/support/fake_api_server.cr`.
 */
class CrystalMethodStubShapeTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "shape.cr",
            """
class StubWidget
  def self.widget_static(x : Int32) : String
  end
end

def StubWidget.widget_reopen(x : Int32) : String
end

def widget_plain
end
            """.trimIndent(),
        )
    }

    fun testStubCarriesHeaderFacts() {
        val static = stubOf("widget_static")
        assertTrue("def self.x is a type-level method", static.isStatic)
        assertEquals("(x : Int32)", static.parameterSignature)
        assertEquals("String", static.returnType)
        assertNull("def self.x has no header type path", static.typePath)

        val reopened = stubOf("widget_reopen")
        assertEquals("StubWidget", reopened.typePath)
        assertFalse("def StubWidget.x is not a self method", reopened.isStatic)
    }

    fun testLookupsAnswerFromStub() {
        assertEquals("(x : Int32)", CrystalMethodLookup.getParameterSignature(methodOf("widget_static")))
        assertEquals("String", CrystalMethodLookup.getReturnType(methodOf("widget_static")))
        assertTrue(CrystalMethodLookup.isStaticMethod(methodOf("widget_static")))
        assertFalse(CrystalMethodLookup.isStaticMethod(methodOf("widget_reopen")))
    }

    fun testEnclosingClassNameComesFromTheHeaderTypePath() {
        // Enclosing definition of the class member.
        assertEquals("StubWidget", CrystalMethodLookup.getEnclosingClassName(methodOf("widget_static")))
        // Top-level reopening: the owner is the type in the header, not the file.
        assertEquals("StubWidget", CrystalMethodLookup.getEnclosingClassName(methodOf("widget_reopen")))
        // Genuinely top-level method.
        assertNull(CrystalMethodLookup.getEnclosingClassName(methodOf("widget_plain")))
    }

    private fun methodOf(name: String): CrystalMethodDefinition =
        StubIndex
            .getElements(
                CrystalMethodIndex.KEY,
                name,
                project,
                GlobalSearchScope.allScope(project),
                CrystalMethodDefinition::class.java,
            ).single()

    private fun stubOf(name: String): CrystalMethodDefinitionStub {
        val stub = (methodOf(name) as StubBasedPsiElement<*>).stub
        assertNotNull("$name must come from the stub index, not the AST", stub)
        return stub as CrystalMethodDefinitionStub
    }
}
