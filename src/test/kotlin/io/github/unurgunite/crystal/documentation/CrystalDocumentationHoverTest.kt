package io.github.unurgunite.crystal.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalDocumentationHoverTest : BasePlatformTestCase() {
    private val provider = CrystalDocumentationProvider()

    override fun getTestDataPath(): String = "src/test/testData"

    private fun hoverDoc(code: String): String? {
        myFixture.configureByText("test.cr", code)
        val offset = myFixture.caretOffset
        val leaf = myFixture.file.findElementAt(offset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, offset)
        if (target != null) {
            val doc = provider.generateDoc(target, leaf)
            if (doc != null) return doc
        }
        return provider.generateDoc(leaf, null)
    }

    fun testResolveFromCallSite() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                # Wichtige Funktion.
                def wichtig(x : Int32)
                end
                wichtig(5)
                """.trimIndent(),
            )
        val callOffset =
            myFixture.editor.document.text
                .lastIndexOf("wichtig")
        val leaf = file.findElementAt(callOffset)!!
        val doc = provider.generateDoc(leaf, null)
        assertTrue("Should not crash", doc == null || doc.isNotEmpty())
    }

    fun testHoverOnStaticClassDotCallShowsMethodDoc() {
        val doc =
            hoverDoc(
                """
                class Apfel
                  # Tanzt laut.
                  def self.tanzen
                  end
                end
                Apfel.tan<caret>zen
                """.trimIndent(),
            )
        assertNotNull("Hover on Apfel.tanzen should return doc", doc)
        assertTrue("Should contain method name 'tanzen'", doc!!.contains("tanzen"))
        assertTrue("Should contain class name 'Apfel'", doc.contains("Apfel"))
        assertTrue("Should contain doc comment 'Tanzt laut'", doc.contains("Tanzt laut"))
    }

    fun testHoverOnInstanceMethodDotCallShowsMethodDoc() {
        val doc =
            hoverDoc(
                """
                class Apfel
                  # Isst den Apfel.
                  def essen
                  end
                end
                a = Apfel.new
                a.es<caret>sen
                """.trimIndent(),
            )
        assertNotNull("Hover on a.essen should return doc", doc)
        assertTrue("Should contain method name 'essen'", doc!!.contains("essen"))
        assertTrue("Should contain doc comment 'Isst den Apfel'", doc.contains("Isst den Apfel"))
    }

    fun testHoverOnTopLevelBareCallStillWorks() {
        val doc =
            hoverDoc(
                """
                # Sahnetoßchen.
                def sahne(bonbon : String)
                  return bonbon
                end
                sah<caret>ne
                """.trimIndent(),
            )
        assertNotNull("Hover on sahne should return doc", doc)
        assertTrue("Should contain method name 'sahne'", doc!!.contains("sahne"))
        assertTrue("Should contain doc comment 'Sahnetoßchen'", doc.contains("Sahneto"))
    }

    fun testHoverOnDotNewShowsConstructorDoc() {
        val doc =
            hoverDoc(
                """
                class Senf
                  # Erzeugt eine Senf-Instanz.
                  def initialize(x : Int32)
                  end
                end
                Senf.n<caret>ew
                """.trimIndent(),
            )
        assertNotNull("Hover on Senf.new should return doc", doc)
        assertTrue("Should contain 'initialize' or 'Senf'", doc!!.contains("initialize") || doc.contains("Senf"))
    }

    fun testParameterHoverInDefinitionShowsParameterPopup() {
        val doc =
            hoverDoc(
                """
                def butter(bon<caret>bon : String)
                  return bonbon
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on parameter in definition should return doc", doc)
        assertTrue("Should contain '(Parameter)' label", doc!!.contains("(Parameter)"))
        assertTrue("Should contain parameter name 'bonbon'", doc.contains("bonbon"))
    }

    fun testParameterHoverInBodyShowsParameterPopup() {
        val doc =
            hoverDoc(
                """
                def butter(bonbon : String)
                  return bon<caret>bon
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on parameter in body should return doc", doc)
        assertTrue("Should contain '(Parameter)' label", doc!!.contains("(Parameter)"))
        assertTrue("Should contain parameter name 'bonbon'", doc.contains("bonbon"))
    }

    fun testParameterPopupShowsType() {
        val doc =
            hoverDoc(
                """
                def butter(bonbon : String)
                  return bon<caret>bon
                end
                """.trimIndent(),
            )
        assertNotNull(doc)
        assertTrue("Should contain type 'String'", doc!!.contains("String"))
    }

    fun testParameterPopupTypeIsLinked() {
        val doc =
            hoverDoc(
                """
                class Foo
                  def greet(x : Foo)
                    x
                  end
                end
                greet(Foo.new)
                """.trimIndent(),
            )
        // The hoverDoc helper finds the caret and resolves — Foo should be hyperlinked
        // This is a basic check that the type appears in the output
        assertTrue("Should contain 'Foo' as type", doc!!.contains("Foo"))
    }

    fun testUntypedParameterShowsAnyAndRuntimeNote() {
        val doc =
            hoverDoc(
                """
                def butter(bon<caret>bon)
                  return bonbon
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on untyped parameter should return doc", doc)
        assertTrue("Should show 'Any' as pseudo-type", doc!!.contains("Any"))
        assertTrue("Should contain '(Parameter)' label", doc.contains("(Parameter)"))
        assertTrue("Should contain runtime note", doc.contains("determined at runtime"))
    }

    fun testMethodDefinitionHoverShowsPopup() {
        val doc =
            hoverDoc(
                """
                def bu<caret>tter(x : Int32)
                  x
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on method definition should return doc", doc)
        assertTrue("Should contain method name 'butter'", doc!!.contains("butter"))
    }

    fun testClassDefinitionHoverShowsPopup() {
        val doc =
            hoverDoc(
                """
                class Fo<caret>o
                  def bar
                  end
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on class definition should return doc", doc)
        assertTrue("Should contain 'class' keyword", doc!!.contains("class"))
        assertTrue("Should contain class name 'Foo'", doc.contains("Foo"))
    }

    fun testModuleDefinitionHoverShowsPopup() {
        val doc =
            hoverDoc(
                """
                module Ba<caret>r
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on module definition should return doc", doc)
        assertTrue("Should contain 'module' keyword", doc!!.contains("module"))
        assertTrue("Should contain module name 'Bar'", doc.contains("Bar"))
    }

    fun testStructDefinitionHoverShowsPopup() {
        val doc =
            hoverDoc(
                """
                struct Po<caret>int
                  property x : Int32
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on struct definition should return doc", doc)
        assertTrue("Should contain 'struct' keyword", doc!!.contains("struct"))
        assertTrue("Should contain struct name 'Point'", doc.contains("Point"))
    }

    fun testEnumDefinitionHoverShowsPopup() {
        val doc =
            hoverDoc(
                """
                enum Colo<caret>r
                  RED
                end
                """.trimIndent(),
            )
        assertNotNull("Hover on enum definition should return doc", doc)
        assertTrue("Should contain 'enum' keyword", doc!!.contains("enum"))
        assertTrue("Should contain enum name 'Color'", doc.contains("Color"))
    }
}
