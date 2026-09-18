package io.github.unurgunite.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.type.CrystalTypeInference

class CrystalTypeInferenceTest : BasePlatformTestCase() {
    fun testInferIntegerLiteral() {
        myFixture.configureByText("test.cr", "x = 1")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int32", type)
    }

    fun testInferSuffixedIntegerLiteral() {
        myFixture.configureByText("test.cr", "x = 1_i64")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int64", type)
    }

    fun testInferFloatLiteral() {
        myFixture.configureByText("test.cr", "x = 1.0")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Float64", type)
    }

    fun testInferSuffixedFloatLiteral() {
        myFixture.configureByText("test.cr", "x = 1_f32")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Float32", type)
    }

    fun testInferStringLiteral() {
        myFixture.configureByText("test.cr", "x = \"hello\"")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("String", type)
    }

    fun testInferCharLiteral() {
        myFixture.configureByText("test.cr", "x = 'a'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferCharLiteralWithEscapeSequences() {
        myFixture.configureByText("test.cr", "x = '\\n'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferCharLiteralWithHexEscape() {
        myFixture.configureByText("test.cr", "x = '\\x41'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferCharLiteralWithBackslashEscape() {
        myFixture.configureByText("test.cr", "x = '\\\\'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferSymbolLiteral() {
        myFixture.configureByText("test.cr", "x = :foo")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Symbol", type)
    }

    fun testInferTrueLiteral() {
        myFixture.configureByText("test.cr", "x = true")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Bool", type)
    }

    fun testInferFalseLiteral() {
        myFixture.configureByText("test.cr", "x = false")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Bool", type)
    }

    fun testInferNilLiteral() {
        myFixture.configureByText("test.cr", "x = nil")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Nil", type)
    }

    fun testInferArrayLiteralWithOfType() {
        myFixture.configureByText("test.cr", "x = [] of Int32")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Array(Int32)", type)
    }

    fun testInferArrayLiteralHomogeneous() {
        myFixture.configureByText("test.cr", "x = [1, 2, 3]")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Array(Int32)", type)
    }

    fun testInferInstanceVariableFromAssignment() {
        myFixture.configureByText("test.cr", "@x = \"hello\"")
        val type = CrystalTypeInference.inferType("@x", myFixture.file, project)
        assertEquals("String", type)
    }

    fun testInferInstanceVariableFromIntegerAssignment() {
        myFixture.configureByText("test.cr", "@count = 42")
        val type = CrystalTypeInference.inferType("@count", myFixture.file, project)
        assertEquals("Int32", type)
    }

    fun testInferHashLiteral() {
        myFixture.configureByText("test.cr", "x = {\"a\" => 1}")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Hash(String, Int32)", type)
    }

    fun testInferHashLiteralShorthand() {
        myFixture.configureByText("test.cr", "x = {a: 1}")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Hash(Symbol, Int32)", type)
    }

    fun testInferTupleLiteral() {
        myFixture.configureByText("test.cr", "x = {1, \"hi\"}")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Tuple(Int32, String)", type)
    }

    fun testInferTernarySameType() {
        myFixture.configureByText("test.cr", "x = true ? 1 : 2")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int32", type)
    }

    fun testInferTernaryDifferentTypes() {
        myFixture.configureByText("test.cr", "x = true ? 1 : nil")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int32 | Nil", type)
    }

    fun testInferTernaryWithVariableElement() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
a = true ? 1 : 2
puts a
                """.trimIndent(),
            )
        // Find the variable reference 'a' in 'puts a' (mimics hover context)
        val putsOffset = file.text.indexOf("puts")
        val aOffset = file.text.indexOf("a", putsOffset)
        val elementAtOffset = file.findElementAt(aOffset)
        val type = CrystalTypeInference.inferType("a", elementAtOffset!!, project)
        assertEquals("Int32", type)
    }

    fun testInferMethodReturnTypeFromReturn() {
        myFixture.configureByText(
            "test.cr",
            """
def sahne(bonbon : String)
  return bonbon
end
ret = sahne "gogo"
            """.trimIndent(),
        )
        val retOffset = myFixture.file.text.indexOf("ret =")
        val contextElement = myFixture.file.findElementAt(retOffset)
        val type = CrystalTypeInference.inferType("ret", contextElement!!, project)
        assertEquals("String", type)
    }

    fun testInferMethodReturnTypeFromImplicitReturn() {
        myFixture.configureByText(
            "test.cr",
            """
def bohne(age : Int32)
  7 + age
end
bet = bohne 22
            """.trimIndent(),
        )
        val betOffset = myFixture.file.text.indexOf("bet =")
        val contextElement = myFixture.file.findElementAt(betOffset)
        val type = CrystalTypeInference.inferType("bet", contextElement!!, project)
        assertEquals("Int32", type)
    }

    fun testSelfReferentialAssignmentTerminates() {
        // `x = x.to_s`: inferring x re-enters inference on x via the resolver
        // bridge (resolveType → inferTypeList → assignment → resolveType).
        // Must terminate (unknown → null), not StackOverflowError.
        myFixture.configureByText("test.cr", "x = x.to_s")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertNull(type)
    }

    fun testMutuallyReferentialAssignmentsTerminate() {
        // a ↔ b cycle across two assignments: same termination requirement.
        myFixture.configureByText(
            "test.cr",
            """
a = b.to_s
b = a.to_s
puts a
            """.trimIndent(),
        )
        val type = CrystalTypeInference.inferType("a", myFixture.file, project)
        assertNull(type)
    }

    fun testUnionParamYieldsAllMembers() {
        // `inferTypeList` must return every union member, not just the first —
        // DOT-call resolution iterates all of them (`x.essen` on `Apfel | Banane`).
        myFixture.configureByText(
            "test.cr",
            """
            def consume(x : Apfel | Banane)
              puts x
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val putsOffset = file.text.indexOf("puts")
        val xOffset = file.text.indexOf("x", putsOffset)
        val types = CrystalTypeInference.inferTypeList("x", file.findElementAt(xOffset)!!, project)
        assertEquals(listOf("Apfel", "Banane"), types)
    }

    fun testDeepReceiverChainTerminates() {
        // `a = b.foo; b = c.foo; ...` past MAX_INFERENCE_DEPTH: must terminate
        // with unknown (empty), not StackOverflowError.
        myFixture.configureByText(
            "test.cr",
            """
            a = b.to_s
            b = c.to_s
            c = d.to_s
            d = e.to_s
            e = f.to_s
            f = g.to_s
            g = 1
            puts a
            """.trimIndent(),
        )
        val file = myFixture.file
        val putsOffset = file.text.indexOf("puts")
        val aOffset = file.text.indexOf("a", putsOffset)
        val types = CrystalTypeInference.inferTypeList("a", file.findElementAt(aOffset)!!, project)
        assertTrue("Deep chain must terminate, got: $types", types.isEmpty() || types == listOf("String"))
    }

    fun testIvarFromShorthandParamHasType() {
        // Direct ivar query: `@name` typed by `initialize(@name : String)`.
        myFixture.configureByText(
            "test.cr",
            """
            class Path
              def initialize(@name : String)
              end
              def show
                puts @name
              end
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val useOffset = file.text.indexOf("@name", file.text.indexOf("puts"))
        assertEquals(
            "String",
            CrystalTypeInference.inferType("@name", file.findElementAt(useOffset)!!, project),
        )
    }

    fun testIvarFromPropertyDeclarationHasType() {
        // `@name : String` property declaration types the ivar from any method.
        myFixture.configureByText(
            "test.cr",
            """
            class Path
              @name : String
              def show
                puts @name
              end
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val useOffset = file.text.indexOf("@name", file.text.indexOf("puts"))
        assertEquals(
            "String",
            CrystalTypeInference.inferType("@name", file.findElementAt(useOffset)!!, project),
        )
    }

    fun testIvarFromAssignmentHasType() {
        // `@count = 42` in one method types `@count` reads without any declaration.
        myFixture.configureByText(
            "test.cr",
            """
            class Counter
              def reset
                @count = 42
              end
              def show
                puts @count
              end
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val useOffset = file.text.indexOf("@count", file.text.indexOf("puts"))
        assertEquals(
            "Int32",
            CrystalTypeInference.inferType("@count", file.findElementAt(useOffset)!!, project),
        )
    }

    fun testPlainParamDoesNotTypeIvar() {
        // `def foo(name : String)` declares NO ivar: `@name` stays unknown.
        // (Guards the declaration detector against the stripped param name.)
        myFixture.configureByText(
            "test.cr",
            """
            class Path
              def foo(name : String)
                puts @name
              end
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val useOffset = file.text.indexOf("@name")
        assertNull(
            "plain param must not type @name",
            CrystalTypeInference.inferType("@name", file.findElementAt(useOffset)!!, project),
        )
    }

    fun testLocalAssignedFromIvarIsUnknown() {
        // `path.cr` shape: `name = @name` where `@name` is a plain ivar (no
        // param carries the type), then `name.starts_with?(...)`. The RHS has
        // no cheap type, so the local stays unknown (null) — and resolving it
        // must not re-walk the file per recursion level (the "Resolving
        // reference" hang). Must terminate with unknown, not hang or throw.
        myFixture.configureByText(
            "test.cr",
            """
            class Path
              def expand
                name = @name
                name.starts_with?("~/")
              end
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val callOffset = file.text.indexOf("starts_with?")
        val type = CrystalTypeInference.inferType("name", file.findElementAt(callOffset)!!, project)
        assertNull("ivar-backed local must stay unknown, got: $type", type)
    }

    fun testLocalAssignedFromIvarParamTakesParamType() {
        // Companion: when `@name` IS a typed parameter, the shorthand
        // `@name : String` promotes to the local `name` (param names strip
        // `@`), so the receiver type is known exactly.
        myFixture.configureByText(
            "test.cr",
            """
            class Path
              def expand(@name : String)
                name = @name
                name.starts_with?("~/")
              end
            end
            """.trimIndent(),
        )
        val file = myFixture.file
        val callOffset = file.text.indexOf("starts_with?")
        assertEquals("String", CrystalTypeInference.inferType("name", file.findElementAt(callOffset)!!, project))
    }

    fun testAssignmentIndexInvalidatesAfterEdit() {
        // The per-file assignment index is cached: after editing the RHS the
        // new type must be visible, not the stale cached one.
        val file = myFixture.configureByText("test.cr", "x = 1\nputs x\n")
        val xOffset = file.text.indexOf("x", file.text.indexOf("puts"))
        assertEquals("Int32", CrystalTypeInference.inferType("x", file.findElementAt(xOffset)!!, project))
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.getDocument(file).setText("x = \"hello\"\nputs x\n")
        }
        com.intellij.psi.PsiDocumentManager
            .getInstance(project)
            .commitAllDocuments()
        val edited = myFixture.file
        val newOffset = edited.text.indexOf("x", edited.text.indexOf("puts"))
        assertEquals("String", CrystalTypeInference.inferType("x", edited.findElementAt(newOffset)!!, project))
    }
}
