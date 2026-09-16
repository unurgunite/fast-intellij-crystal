package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalArgumentCountSplatTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
    }

    // ==================== Splat Absorbs Extra Args ====================
    fun testSplatAcceptsAnyCount() {
        myFixture.configureByText(
            "test.cr",
            """
            def variadic(*args)
            end
            variadic(1, 2, 3, 4, 5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSplatWithRequiredBefore() {
        myFixture.configureByText(
            "test.cr",
            """
            def log(level : String, *messages)
            end
            log("info", "msg1", "msg2", "msg3")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatAcceptsAnyNamedArg() {
        myFixture.configureByText(
            "test.cr",
            """
            def flexible(**kwargs)
            end
            flexible(foo: 1, bar: 2, baz: 3)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Block Parameter Not Counted ====================
    fun testBlockParamNotCounted() {
        myFixture.configureByText(
            "test.cr",
            """
            def each(arr : String, &block)
            end
            each("hello")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Splat Expansion ====================
    fun testSplatExpansionCorrectCount() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            args = {1, 2, 3}
            add(*args)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    private fun debugPsiTree(
        element: com.intellij.psi.PsiElement,
        depth: Int,
    ): String {
        val sb = StringBuilder()
        sb.append("  ".repeat(depth))
        sb.append(element.node.elementType.toString())
        if (element.children.isEmpty()) sb.append(" '${element.text.take(30)}'")
        sb.append("\n")
        for (child in element.children) {
            sb.append(debugPsiTree(child, depth + 1))
        }
        return sb.toString()
    }

    fun testSplatExpansionTooFew() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            args = {1, 2}
            <error descr="Missing required argument(s): 'c'">add</error>(*args)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionCorrect() {
        myFixture.configureByText(
            "test.cr",
            """
            def setup(host : String, port : Int32)
            end
            options = {host: "localhost", port: 8080}
            setup(**options)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionMissingKey() {
        myFixture.configureByText(
            "test.cr",
            """
            def setup(host : String, port : Int32)
            end
            options = {host: "localhost"}
            <error descr="Missing required argument(s): 'port'">setup</error>(**options)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSplatUnresolvableNoWarning() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            add(*get_args())
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSplatExpansionTooMany() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32, c : Int32) : Int32
              a + b + c
            end
            args = {1, 2, 3, 4}
            <error descr="Too many arguments: expected at most 3, got 4">add</error>(*args)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatExpansionUnknownKey() {
        myFixture.configureByText(
            "test.cr",
            """
            def setup(host : String, port : Int32)
            end
            options = {host: "localhost", ort: 8080}
            <error descr="Unknown named argument(s): 'ort'">setup</error>(**options)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBlockPassNotCountedAsArgument() {
        myFixture.configureByText(
            "test.cr",
            """
            def get(path : String, &block : ->)
            end
            def forward(path : String, &block : ->)
              get(path, &block)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBareCallWithSplatArgument() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            args = {1, 2}
            add(*args)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMultiplicationInMethodCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            add(2 * 3, 4 * 5)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMultiplicationInStructNewCall() {
        myFixture.configureByText(
            "test.cr",
            """
            struct Vector2D
              def initialize(@x : Float64, @y : Float64)
              end
            end
            Vector2D.new(1.0 * 2.0, 3.0 * 4.0)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testVariableMultiplicationInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            x = 2
            y = 3
            add(x, y)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Binary Operators with Literals in Arguments ====================
    fun testPlusWithStringLiteralInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : String, b : String)
            end
            name = "file"
            add(name + ".ext", name + ".bak")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testPlusWithPercentLiteralInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def write_to_second_line(file : String, line : String)
            end
            cmd_path_no_ext = "test"
            write_to_second_line(cmd_path_no_ext + ".ps1", %Q{not working})
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testPlusWithIntegerLiteralInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            x = 10
            add(x + 5, x + 3)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMinusWithLiteralInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def sub(a : Int32, b : Int32) : Int32
              a - b
            end
            x = 10
            sub(x - 5, x - 3)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBinaryOpWithGroupedExpressionInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def add(a : Int32, b : Int32) : Int32
              a + b
            end
            x = 10
            add(x + (1), x + (2))
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBinaryOpWithArrayLiteralInCallArgs() {
        myFixture.configureByText(
            "test.cr",
            """
            def process(a : String, b : String)
            end
            arr = ["hello"]
            process(arr[0] + "x", arr[0] + "y")
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
