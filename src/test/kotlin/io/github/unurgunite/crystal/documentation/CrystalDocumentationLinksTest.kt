package io.github.unurgunite.crystal.documentation

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition

class CrystalDocumentationLinksTest : BasePlatformTestCase() {
    private val provider = CrystalDocumentationProvider()

    override fun getTestDataPath(): String = "src/test/testData"

    private fun findMethod(
        fileText: String,
        methodName: String,
    ): CrystalMethodDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val methods = PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java)
        return methods.first { it.name == methodName }
    }

    private fun findClass(
        fileText: String,
        className: String,
    ): CrystalClassDefinition {
        val file = myFixture.configureByText("test.cr", fileText)
        val classes = PsiTreeUtil.findChildrenOfType(file, CrystalClassDefinition::class.java)
        return classes.first { it.name == className }
    }

    fun testParameterTypeIsHyperlinked() {
        val method =
            findMethod(
                """
                class Foo
                  def greet(name : Foo)
                  end
                end
                """.trimIndent(),
                "greet",
            )
        val doc = provider.generateDoc(method, null)
        assertNotNull(doc)
        // Foo (a project-defined class) should be hyperlinked
        assertTrue("Should contain 'Foo' in parameter", doc!!.contains("Foo"))
        assertTrue("Foo should be hyperlinked", doc.contains("psi_element://class:Foo"))
    }

    fun testSuperclassIsHyperlinkedInClassSignature() {
        val classDef =
            findClass(
                """
                class Animal
                end
                class Dog < Animal
                end
                """.trimIndent(),
                "Dog",
            )
        val doc = provider.generateDoc(classDef, null)
        assertNotNull(doc)
        assertTrue("Should contain 'class' keyword", doc!!.contains("class"))
        assertTrue("Should contain class name 'Dog'", doc!!.contains("Dog"))
        assertTrue("Should contain superclass 'Animal'", doc.contains("Animal"))
        assertTrue("Superclass should be hyperlinked", doc.contains("psi_element://class:Animal"))
    }

    fun testOwnClassNameIsNotHyperlinked() {
        val classDef =
            findClass(
                """
                class Foo
                end
                """.trimIndent(),
                "Foo",
            )
        val doc = provider.generateDoc(classDef, null)
        assertNotNull(doc)
        // The class's own name should NOT be hyperlinked (no self-link)
        assertFalse("Own class name should NOT be hyperlinked", doc!!.contains("psi_element://class:Foo"))
    }

    fun testGetDocumentationElementForLinkResolvesToClassViaIndex() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Tesa
                  def self.hika(name : String)
                  end
                end
                """.trimIndent(),
            )
        val project = myFixture.project
        val resolved =
            provider.getDocumentationElementForLink(
                com.intellij.psi.PsiManager
                    .getInstance(project),
                "class:Tesa",
                file,
            )
        assertNotNull("Should resolve Tesa link to class definition", resolved)
        assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition)
    }

    fun testGetDocumentationElementForLinkReturnsNullForUnknownName() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                end
                """.trimIndent(),
            )
        val project = myFixture.project
        val resolved =
            provider.getDocumentationElementForLink(
                com.intellij.psi.PsiManager
                    .getInstance(project),
                "class:DoesNotExist",
                file,
            )
        assertNull("Should return null for unknown class name", resolved)
    }

    fun testGetDocumentationElementForLinkIgnoresNonClassPrefix() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                end
                """.trimIndent(),
            )
        val project = myFixture.project
        val resolved =
            provider.getDocumentationElementForLink(
                com.intellij.psi.PsiManager
                    .getInstance(project),
                "method:foo",
                file,
            )
        assertNull("Should return null for non-class prefix", resolved)
    }
}
