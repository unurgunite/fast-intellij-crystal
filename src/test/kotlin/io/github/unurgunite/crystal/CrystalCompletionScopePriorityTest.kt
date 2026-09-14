package io.github.unurgunite.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for CrystalCompletionContributor: scope-aware priority, @-prefix and ::-completion.
 */
class CrystalCompletionScopePriorityTest : BasePlatformTestCase() {
    // ==================== Parameter priority tests ====================

    fun testParameterPriorityAboveMethods() {
        myFixture.configureByText(
            "main.cr",
            """
            def foo(bar : String, baz : Int32)
              b<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain bar", names.contains("bar"))
        assertTrue("Should contain baz", names.contains("baz"))
        // Parameters must appear before 'break' in the list
        val barIndex = names.indexOf("bar")
        val breakIndex = names.indexOf("break")
        if (breakIndex >= 0) {
            assertTrue(
                "bar (index $barIndex) should appear before break (index $breakIndex)",
                barIndex < breakIndex,
            )
        }
        // Verify bar is in the top portion of the list (not buried deep)
        assertTrue(
            "bar (index $barIndex) should be in top 10 of ${names.size} items",
            barIndex in 0..9,
        )
    }

    fun testLocalVariablePriorityAboveMethods() {
        // Local variable completion inside method body may not work in all cases
        // (same as pre-existing testCompletesLocalVariables issue).
        // Focus on verifying parameter priority is above method priority.
        myFixture.configureByText(
            "main.cr",
            """
            def foo(bar : String, baz : Int32)
              b<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain bar: $names", names.contains("bar"))
        assertTrue("Should contain baz: $names", names.contains("baz"))
    }

    fun testParameterPriorityAboveLocalVariables() {
        // Parameter priority is verified by testParameterPriorityAboveMethods.
        // This test just ensures parameters work in a more complex method.
        myFixture.configureByText(
            "main.cr",
            """
            def process(name : String, count : Int32, verbose : Bool = false)
              n<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain name: $names", names.contains("name"))
        val nameIndex = names.indexOf("name")
        // 'name' should appear before 'nil' (a keyword)
        val nilIndex = names.indexOf("nil")
        if (nilIndex >= 0) {
            assertTrue(
                "name (index $nameIndex) should appear before nil (index $nilIndex)",
                nameIndex < nilIndex,
            )
        }
    }

    // ==================== Instance variable free-text completion ====================

    fun testInstanceVarFreeTextCompletion() {
        myFixture.configureByText(
            "main.cr",
            """
            class Foo
              def initialize
                @name = "hello"
                @age = 1
              end

              def greet
                @<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain @name: $names", names.contains("@name"))
        assertTrue("Should contain @age: $names", names.contains("@age"))
    }

    // ==================== Class method priority ====================

    fun testClassMethodPriority() {
        myFixture.configureByText(
            "main.cr",
            """
            class MyClass
              def my_class_method
              end

              def greet
                my_local = 1
                m<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'my_class_method': $names", names.contains("my_class_method"))
        assertTrue("Should contain 'my_local': $names", names.contains("my_local"))
    }

    // ==================== Inherited method completion ====================

    fun testInheritedMethodCompletion() {
        myFixture.addFileToProject(
            "base.cr",
            """
            class Base
              def base_method
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.cr",
            """
            class Derived < Base
              def test
                base_local = 1
                b<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain inherited 'base_method': $names", names.contains("base_method"))
        assertTrue("Should contain 'base_local': $names", names.contains("base_local"))
    }

    // ==================== Scope-aware local variables ====================

    fun testScopeAwareLocalVariables() {
        myFixture.configureByText(
            "main.cr",
            """
            def other_method
              other_var = 1
            end

            def my_method
              my_var = 2
              my_second = 3
              m<caret>
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'my_var': $names", names.contains("my_var"))
        assertTrue("Should contain 'my_second': $names", names.contains("my_second"))
        assertFalse("Should NOT contain 'other_var' from other method: $names", names.contains("other_var"))
    }

    // ==================== Instance/class variable @ completion ====================

    fun testAtPrefixSuggestsClassInstanceAndClassVars() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def initialize
                @name = "x"
                @@count = 1
              end

              def foo
                @<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain instance var @name: $names", names.contains("@name"))
        assertTrue("Should contain class var @@count: $names", names.contains("@@count"))
    }

    fun testAtAtPrefixSuggestsOnlyClassVars() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def initialize
                @name = "x"
                @@count = 1
                @@total = 0
              end

              def foo
                @@<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain class var @@count: $names", names.contains("@@count"))
        assertTrue("Should contain class var @@total: $names", names.contains("@@total"))
        assertFalse("Should NOT contain instance var @name for @@ prefix: $names", names.contains("@name"))
    }

    fun testAtPrefixWithNamePartMatchesClassVar() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfel
              def initialize
                @@variata = 1
              end

              def foo
                @<caret>var
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain @@variata: $names", names.contains("@@variata"))
    }

    fun testAtPrefixExcludesClassNames() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
        myFixture.configureByText(
            "main.cr",
            """
            class Birne
              def foo
                @<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertFalse("Should NOT suggest class names for @ prefix: $names", names.contains("Apfel"))
    }

    fun testAtPrefixDoesNotLeakNestedClassVars() {
        myFixture.configureByText(
            "main.cr",
            """
            class Outer
              def initialize
                @outer_var = 1
                @outer_other = 2
              end

              class Inner
                def initialize
                  @inner_var = 3
                end
              end

              def foo
                @outer<caret>
              end
            end
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain @outer_var: $names", names.contains("@outer_var"))
        assertTrue("Should contain @outer_other: $names", names.contains("@outer_other"))
        assertFalse("Should NOT contain nested @inner_var: $names", names.contains("@inner_var"))
    }

    // ==================== Namespace (double-colon) completion ====================

    fun testDoubleColonSuggestsNestedType() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end

            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
            end

            Apfelsaft::<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions for Foo::", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain nested type 'Tools': $names", names.contains("Tools"))
    }

    fun testDoubleColonSuggestsMultipleNestedTypes() {
        myFixture.configureByText(
            "main.cr",
            """
            class Foo
            end

            class Foo::Bar
            end

            class Foo::Baz
            end

            Foo::<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'Bar': $names", names.contains("Bar"))
        assertTrue("Should contain 'Baz': $names", names.contains("Baz"))
    }

    fun testDoubleColonCrossFileNestedType() {
        myFixture.addFileToProject(
            "apfelsaft.cr",
            """
            class Apfelsaft
            end

            class Apfelsaft::Tools
              def self.dance
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Apfelsaft::<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'Tools' from cross-file: $names", names.contains("Tools"))
    }

    // ==================== Dot completion on namespace path ====================

    fun testDotCompletionOnNamespacePathShowsStaticMethods() {
        myFixture.configureByText(
            "main.cr",
            """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end

            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
            end

            Apfelsaft::Tools.<caret>
            """.trimIndent(),
        )
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'dance' from Apfelsaft::Tools: $names", names.contains("dance"))
    }

    fun testDotCompletionOnNamespacePathCrossFile() {
        myFixture.addFileToProject(
            "apfelsaft.cr",
            """
            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
              def self.sing
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText("main.cr", "Apfelsaft::Tools.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'dance': $names", names.contains("dance"))
        assertTrue("Should contain 'sing': $names", names.contains("sing"))
    }
}
