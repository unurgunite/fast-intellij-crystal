package io.github.unurgunite.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for CrystalCompletionContributor: dot-completion, `new`, records and instance methods.
 */
class CrystalCompletionDotTest : BasePlatformTestCase() {
    // ==================== Dot completion on class (static methods) ====================

    fun testDotCompletionOnClassShowsStaticMethods() {
        myFixture.addFileToProject(
            "apfel.cr",
            """
            class Apfel
              def self.create
              end
              def eat
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Apfel.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain create", names.contains("create"))
        assertTrue("Should contain new", names.contains("new"))
        assertFalse("Should NOT contain instance method eat", names.contains("eat"))
    }

    fun testDotCompletionOnClassShowsNew() {
        myFixture.addFileToProject(
            "birne.cr",
            """
            class Birne
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Birne.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain new", names.contains("new"))
    }

    fun testNewShowsInitializeParameters() {
        myFixture.addFileToProject(
            "apfel.cr",
            """
            class Apfel
              def initialize(name : String, gewicht : Int32)
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Apfel.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newElement = lookups.first { it.lookupString == "new" }
        val presentation =
            com.intellij.codeInsight.lookup
                .LookupElementPresentation()
        newElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Should show parameters: $tailText", tailText.contains("name") && tailText.contains("gewicht"))
    }

    fun testNewWithParameterlessInitialize() {
        myFixture.addFileToProject(
            "birne.cr",
            """
            class Birne
              def initialize
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Birne.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newElement = lookups.first { it.lookupString == "new" }
        val presentation =
            com.intellij.codeInsight.lookup
                .LookupElementPresentation()
        newElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Parameterless initialize should show empty tail: '$tailText'", tailText.isEmpty() || tailText == "()")
    }

    fun testNewWithoutInitialize() {
        myFixture.addFileToProject(
            "kirsche.cr",
            """
            class Kirsche
              def essen
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Kirsche.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newElement = lookups.first { it.lookupString == "new" }
        val presentation =
            com.intellij.codeInsight.lookup
                .LookupElementPresentation()
        newElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("No initialize should show empty tail: '$tailText'", tailText.isEmpty() || tailText == "()")
    }

    // ==================== Dot completion on module (static methods only) ====================

    fun testDotCompletionOnModuleShowsStaticMethods() {
        myFixture.addFileToProject(
            "helper.cr",
            """
            module MathHelper
              def self.add(a : Int32, b : Int32) : Int32
              end
              def self.subtract(a : Int32, b : Int32) : Int32
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "MathHelper.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain add", names.contains("add"))
        assertTrue("Should contain subtract", names.contains("subtract"))
        assertFalse("Should NOT contain new", names.contains("new"))
    }

    fun testDotCompletionOnModuleDoesNotShowNew() {
        myFixture.addFileToProject(
            "utils.cr",
            """
            module Utils
              def self.helper
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Utils.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertFalse("Module should NOT offer new", names.contains("new"))
    }

    // ==================== Dot completion on variable (instance methods) ====================

    fun testDotCompletionOnVariableWithTypeInference() {
        // Class with our target methods
        myFixture.addFileToProject(
            "apfel.cr",
            """
            class Apfel
              def essen
              end
              def werfen
              end
            end
            """.trimIndent(),
        )
        // Other class with different methods — should NOT appear if inference works
        myFixture.addFileToProject(
            "birne.cr",
            """
            class Birne
              def schaelen
              end
              def waschen
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.cr",
            """
            a = Apfel.new
            a.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen", names.contains("essen"))
        assertTrue("Should contain werfen", names.contains("werfen"))
        assertFalse("Should NOT contain schaelen from Birne (inference should narrow)", names.contains("schaelen"))
        assertFalse("Should NOT contain waschen from Birne (inference should narrow)", names.contains("waschen"))
    }

    fun testDotCompletionWithBareArguments() {
        myFixture.addFileToProject(
            "apfel.cr",
            """
            class Apfel
              def essen
              end
            end
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "birne.cr",
            """
            class Birne
              def schaelen
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.cr",
            """
            a = Apfel.new "lol", 123
            a.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen (bare args should infer Apfel)", names.contains("essen"))
        assertFalse("Should NOT contain schaelen from Birne", names.contains("schaelen"))
    }

    fun testDotCompletionOnVariableWithoutInferenceFallsBack() {
        myFixture.configureByText(
            "main.cr",
            """
            def hello
            end
            x.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        // When type inference fails, no methods are suggested (no fallback)
        // This may return null (no completions) or empty array
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertFalse("Should NOT contain hello when type inference fails", names.contains("hello"))
    }

    // ==================== Parameter type-annotated inference ====================

    fun testDotCompletionOnParameterWithTypeAnnotation() {
        myFixture.addFileToProject(
            "apfel.cr",
            """
            class Apfel
              def schmecken
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.cr",
            """
            def foo(a : Apfel)
              a.<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain schmecken", names.contains("schmecken"))
    }

    // ==================== Instance variable (@var.) dot-completion ====================

    fun testDotCompletionOnInstanceVariable() {
        myFixture.addFileToProject(
            "apfel.cr",
            """
            class Apfel
              def essen
              end
              def werfen
              end
            end
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "birne.cr",
            """
            class Birne
              def schaelen
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.cr",
            """
            class Foo
              @apfel : Apfel
              def initialize
                @apfel = Apfel.new
              end
              def bar
                @apfel.<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen", names.contains("essen"))
        assertTrue("Should contain werfen", names.contains("werfen"))
        assertFalse("Should NOT contain schaelen from Birne", names.contains("schaelen"))
    }

    // ==================== Record macro completion ====================

    fun testRecordDotCompletionOffersNew() {
        myFixture.configureByText(
            "main.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'new' for record type", names.contains("new"))
    }

    fun testRecordNewTailTextShowsParameters() {
        myFixture.configureByText(
            "main.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        val newLookup = lookups?.find { it.lookupString == "new" }
        assertNotNull("Should have 'new' lookup", newLookup)
        val presentation =
            com.intellij.codeInsight.lookup
                .LookupElementPresentation()
        newLookup!!.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue(
            "Should show record parameters in tail text: $tailText",
            tailText.contains("host") && tailText.contains("port") && tailText.contains("ssl"),
        )
    }

    fun testRecordNewWithoutArgsHasNoError() {
        myFixture.configureByText(
            "main.cr",
            """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new(host: "localhost", port: 8080, ssl: true)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Debug: instance method completion ====================

    fun testInstanceMethodDotCompletion() {
        myFixture.addFileToProject(
            "apfelsaft.cr",
            """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end

              def essen(speed : String, anders : Int)
                puts "Schmeckt gut"
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.cr",
            """
            a = Apfelsaft.new("hi", 1)
            a.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen: $names", names.contains("essen"))
    }

    fun testInstanceMethodDotCompletionSameFile() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end

              def essen(speed : String, anders : Int)
                puts "Schmeckt gut"
              end
            end

            a = Apfelsaft.new("hi", 1)
            a.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen: $names", names.contains("essen"))
    }

    fun testClassNewCompletionShowsInitializeParams() {
        myFixture.addFileToProject(
            "apfelsaft2.cr",
            """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Apfelsaft.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newLookup = lookups.find { it.lookupString == "new" }
        assertNotNull("Should contain 'new'", newLookup)
        val presentation =
            com.intellij.codeInsight.lookup
                .LookupElementPresentation()
        newLookup!!.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue(
            "Should show initialize params in tail text: $tailText",
            tailText.contains("cool") && tailText.contains("other"),
        )
    }
}
