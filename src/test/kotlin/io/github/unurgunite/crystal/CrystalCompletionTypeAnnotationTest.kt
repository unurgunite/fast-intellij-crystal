package io.github.unurgunite.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for CrystalCompletionContributor: type annotations, class body, annotations and def overrides.
 */
class CrystalCompletionTypeAnnotationTest : BasePlatformTestCase() {
    // ==================== Type annotation completion ====================

    fun testTypeAnnotationSuggestsStdlibTypes() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo(x : <caret>)
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
        assertTrue("Should contain Array", names.contains("Array"))
        assertTrue("Should contain Bool", names.contains("Bool"))
        assertTrue("Should contain Hash", names.contains("Hash"))
        assertTrue("Should contain Nil", names.contains("Nil"))
        assertTrue("Should contain Float64", names.contains("Float64"))
    }

    fun testTypeAnnotationFiltersByPrefix() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo(x : Str<caret>)
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Struct", names.contains("Struct"))
    }

    fun testReturnTypeAnnotation() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo : <caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeAnnotationInsideClassIncludesSelf() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def foo(other : <caret>)
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain self inside class", names.contains("self"))
    }

    fun testTypeAnnotationTopLevelNoSelf() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo(x : <caret>)
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertFalse("Should NOT contain self at top-level", names.contains("self"))
    }

    fun testTypeAnnotationIncludesProjectTypes() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\nclass Birne\nend\n")
        myFixture.configureByText(
            "main.cr",
            """
            def foo(x : <caret>)
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain project type Apfel", names.contains("Apfel"))
        assertTrue("Should contain project type Birne", names.contains("Birne"))
    }

    // ==================== Class body macro/keyword completion ====================

    fun testClassBodySuggestsGetter() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              get<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain getter", names.contains("getter"))
        assertTrue("Should contain getter?", names.contains("getter?"))
        assertTrue("Should contain getter!", names.contains("getter!"))
    }

    fun testClassBodySuggestsIncludeAndExtend() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              in<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain include", names.contains("include"))
    }

    fun testClassBodyNotInsideMethod() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def foo
                get<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val macroItems =
                lookups.filter { element ->
                    val presentation =
                        com.intellij.codeInsight.lookup
                            .LookupElementPresentation()
                    element.renderElement(presentation)
                    presentation.typeText == "define getter method"
                }
            assertTrue("Should NOT offer class macros inside method", macroItems.isEmpty())
        }
    }

    fun testClassBodyNotAtTopLevel() {
        myFixture.configureByText(
            "main.cr",
            """
            get<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val macroItems =
                lookups.filter { element ->
                    val presentation =
                        com.intellij.codeInsight.lookup
                            .LookupElementPresentation()
                    element.renderElement(presentation)
                    presentation.typeText == "define getter method"
                }
            assertTrue("Should NOT offer class macros at top-level", macroItems.isEmpty())
        }
    }

    fun testGetterInsertsSpace() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              get<caret>
            end
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        myFixture.lookup?.currentItem = myFixture.lookupElements?.first { it.lookupString == "getter" }
        myFixture.finishLookup('\n')

        val text = myFixture.editor.document.text
        assertTrue("Should have space after getter: '$text'", text.contains("getter "))
    }

    // ==================== Annotation completion ====================

    fun testAnnotationCompletion() {
        myFixture.configureByText(
            "main.cr",
            """
            @[<caret>]
            class Apfel
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Deprecated", names.contains("Deprecated"))
        assertTrue("Should contain JSON::Serializable", names.contains("JSON::Serializable"))
        assertTrue("Should contain Flags", names.contains("Flags"))
        assertTrue("Should contain Link", names.contains("Link"))
        assertTrue("Should contain YAML::Field", names.contains("YAML::Field"))
    }

    // ==================== Override method completion (def inside class) ====================

    fun testDefInsideClassSuggestsInitialize() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def ini<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain initialize", names.contains("initialize"))
    }

    fun testDefInsideClassSuggestsToS() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def to<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain to_s", names.contains("to_s"))
    }

    fun testDefInsideClassInitializeInsertsSuper() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def ini<caret>
            end
            """.trimIndent(),
        )
        myFixture.complete(CompletionType.BASIC)
        // Select "initialize" from the list
        myFixture.lookup?.currentItem = myFixture.lookupElements?.first { it.lookupString == "initialize" }
        myFixture.finishLookup('\n')

        val text = myFixture.editor.document.text
        assertTrue("Should contain 'super' in body: $text", text.contains("super"))
        assertTrue("Should contain 'end' closing: $text", text.contains("end"))
    }

    fun testDefOutsideClassNoOverrideSuggestions() {
        myFixture.configureByText(
            "main.cr",
            """
            def ini<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            // Should NOT contain override methods with "override" type text
            val overrideItems =
                lookups.filter { element ->
                    val presentation =
                        com.intellij.codeInsight.lookup
                            .LookupElementPresentation()
                    element.renderElement(presentation)
                    presentation.typeText == "override"
                }
            assertTrue("Should have no override suggestions outside class", overrideItems.isEmpty())
        }
    }

    fun testTypeCompletionAfterInstanceVarColon() {
        myFixture.configureByText(
            "main.cr",
            """
            class Foo
              @name : <caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after @name :", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionAfterClassVarColon() {
        myFixture.configureByText(
            "main.cr",
            """
            class Foo
              @@count : <caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after @@count :", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionAfterLocalVarColon() {
        myFixture.configureByText(
            "test.cr",
            """
            x : <caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after x :", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionAfterPipeInUnion() {
        myFixture.configureByText(
            "test.cr",
            """
            x : String | <caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after pipe in union", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32", names.contains("Int32"))
        assertTrue("Should contain Nil", names.contains("Nil"))
    }

    fun testTypeCompletionAfterMultiplePipes() {
        myFixture.configureByText(
            "test.cr",
            """
            x : String | Int32 | <caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after multiple pipes", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Nil", names.contains("Nil"))
    }

    fun testTypeCompletionInsideGenericParens() {
        myFixture.configureByText(
            "test.cr",
            """
            x : Array(<caret>)
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions inside generic parens", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionInsideGenericAfterComma() {
        myFixture.configureByText(
            "test.cr",
            """
            x : Hash(String, <caret>)
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after comma in generic", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32", names.contains("Int32"))
    }
}
