package io.github.unurgunite.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver

/**
 * `A.new` without parentheses is an EXPRESSION wrapping a DOT_CALL_ACCESS, not
 * a method call, so class instantiation was typed as unknown while
 * `A.new(1)` was typed correctly.
 */
class CrystalNewWithoutParensTypeTest : BasePlatformTestCase() {
    fun testNewWithoutParensIsItsClass() {
        assertEquals("Widget", resolveOf("class Widget\nend\nw = Widget.new\n"))
    }

    fun testNewWithParensStaysItsClass() {
        assertEquals("Widget", resolveOf("class Widget\n  def initialize(size : Int32)\n  end\nend\nw = Widget.new(1)\n"))
    }

    fun testQualifiedNewWithoutParensKeepsItsNamespace() {
        assertEquals("UI::Widget", resolveOf("module UI\n  class Widget\n  end\nend\nw = UI::Widget.new\n"))
    }

    private fun resolveOf(code: String): String? {
        myFixture.configureByText("test.cr", code)
        val expr =
            com.intellij.psi.util.PsiTreeUtil
                .collectElementsOfType(myFixture.file, CrystalExpression::class.java)
                .first { it.text.trim().startsWith("Widget.new") || it.text.trim().startsWith("UI::Widget.new") }
        return CrystalExpressionTypeResolver.resolveType(expr)?.typeName
    }
}
