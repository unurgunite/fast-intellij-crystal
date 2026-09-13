package io.github.unurgunite.crystal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalParameterInfoIndexTest : BasePlatformTestCase() {
    private val handler = CrystalParameterInfoHandler()

    override fun getTestDataPath(): String = "src/test/testData"

    fun testParameterIndexFirstArg() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet(<caret>"John", 30)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testParameterIndexSecondArg() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet("John", <caret>30)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testParameterIndexAfterCommaEmpty() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet("John",<caret>)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testParameterIndexAfterCommaSpaceEmpty() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet("John", <caret>)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testParameterIndexThirdArg() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def foo(a, b, c)
                end
                foo(1, 2, <caret>3)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(2, index)
    }

    fun testParameterIndexNestedCall() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def foo(a, b)
                end
                def bar(x, y)
                end
                foo(bar(1, 2), <caret>3)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    // ==================== Bare Call Parameter Index Tests ====================

    fun testBareCallParameterIndexFirst() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet <caret>"John", 30
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testBareCallParameterIndexSecond() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet "John", <caret>30
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder!!, myFixture.caretOffset)
        assertEquals(1, index)
    }

    // ==================== Dot-Call Tests ====================

    fun testBareCallDotNoArgs() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                  def bar(x, y)
                  end
                end
                f = Foo.new
                f.bar <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for dot-call bare with no args", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("bar", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testBareCallDotTrailingComma() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                  def bar(x, y)
                  end
                end
                f = Foo.new
                f.bar 1, <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for dot-call bare with trailing comma", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("bar", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testBareCallClassMethodNoArgs() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                  def self.create(name)
                  end
                end
                Foo.create <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for class method bare call", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("create", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(0, index)
    }

    // ==================== Negative Tests ====================
}
