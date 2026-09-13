package io.github.unurgunite.crystal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalParameterInfoHandlerTest : BasePlatformTestCase() {
    private val handler = CrystalParameterInfoHandler()

    override fun getTestDataPath(): String = "src/test/testData"

    fun testFindArgsParenthesized() {
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
        assertNotNull("Should find args holder in parenthesized call", argsHolder)
    }

    fun testFindArgsBareCall() {
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
        assertNotNull("Should find args holder in bare call", argsHolder)
    }

    fun testFindArgsAfterCommaNoSpace() {
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
        assertNotNull("Should find args holder after comma without space", argsHolder)
    }

    fun testFindArgsAfterCommaWithSpace() {
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
        assertNotNull("Should find args holder after comma with space", argsHolder)
    }

    fun testFindArgsAtOpeningParen() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet(<caret>)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder at opening paren", argsHolder)
    }

    // ==================== Method Name Resolution Tests ====================

    fun testMethodNameParenthesized() {
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
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
    }

    fun testMethodNameBareCall() {
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
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
    }

    // ==================== Parameter Index Tests ====================

    fun testBareCallNoArgsYet() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for bare call with no args yet", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(0, index)
    }

    fun testBareCallTrailingCommaNoSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet "John",<caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor after trailing comma", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testBareCallTrailingCommaWithSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name, age)
                end
                greet "John", <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor after trailing comma with space", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(1, index)
    }

    fun testBareCallMultipleArgs() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def foo(a, b, c)
                end
                foo 1, 2, <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find anchor for bare call with multiple args", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("foo", name)
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(argsHolder, myFixture.caretOffset)
        assertEquals(2, index)
    }

    fun testNoCallAfterOperator() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                x = 5 + <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        // Should be null or at least not resolve to a method
        if (argsHolder != null) {
            val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder)
            // If it finds something, it shouldn't be "x" or "5"
            assertNull("Should not find method name after operator", name)
        }
    }

    fun testNoCallAtEmptyLine() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def greet(name)
                end
                <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        // Should be null — empty line is not a call
        if (argsHolder != null) {
            val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder)
            assertNull("Should not find method name at empty line", name)
        }
    }

    fun testNoCallInsideBlockBody() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                [1].each do <caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        // Should not trigger parameter info inside block body
        if (argsHolder != null) {
            val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder)
            // "do" is a block opener — should not find "each" as method
            assertNull("Should not find method name inside block body", name)
        }
    }
}
