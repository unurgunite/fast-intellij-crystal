package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Paren-less dot calls (`Widget.new`, `x.empty?`) and nilable bare args. */
class CrystalArgumentCountParenlessTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection())
    }

    // A paren-less dot call (`Widget.new`) has no args holder, so it never
    // reached the arity checks: a constructor missing its required params was
    // silently accepted. Both directions must report.
    fun testParenlessConstructorArityIsChecked() {
        myFixture.configureByText(
            "t.cr",
            """
            class Widget
              def initialize(name : String, size : Int32)
              end
            end

            Widget.new("a", 1)
            Widget.<error descr="Missing required argument(s): 'name', 'size'">new</error>
            Widget.new("a", 1, <error descr="Too many arguments: expected at most 2, got 3">2</error>)
            Widget.new("a", 1)
            puts "after"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // Nilable type as a bare argument (`should eq String?`, `.get Bool?`):
    // the bare tower has no `?` tail, so the `?` used to be left dangling and
    // the call failed to parse at the argument boundary.
    fun testNilableTypeAsBareArgumentParses() {
        myFixture.configureByText(
            "t.cr",
            """
            def check(value, expected)
            end

            check 1, String?
            check 1, A::B?
            """.trimIndent(),
        )
        assertEquals(
            0,
            PsiTreeUtil
                .collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
                .size,
        )
    }
}
