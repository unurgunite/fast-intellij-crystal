package io.github.unurgunite.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalBlock

/**
 * Deeply nested `do`-block chains must parse completely.
 *
 * A builder-style file (`b.object do` / `b.field(...)`) nests seven levels in
 * ordinary code. grammar-kit charges one recursion level per rule call and the
 * parser definition used to cap that at 200, so such a file stopped parsing
 * mid-body with `unmatched input`. These cases pin the cap far enough away.
 */
class CrystalDeepBlockNestingTest : BasePlatformTestCase() {
    private fun assertNesting(
        levels: Int,
        leaf: String,
    ) {
        val builder = StringBuilder("def m(b)\n")
        repeat(levels) { level ->
            builder.append("  ".repeat(level + 1)).append("b.object do\n")
            builder.append("  ".repeat(level + 2)).append(leaf).append("\n")
        }
        for (level in levels downTo 1) builder.append("  ".repeat(level)).append("end\n")
        builder.append("end\n")

        val file = myFixture.configureByText("a.cr", builder.toString())
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertEquals("unexpected parse errors at nesting $levels: ${errors.map { it.errorDescription }}", 0, errors.size)
        assertEquals("every do-block must open", levels, PsiTreeUtil.collectElementsOfType(file, CrystalBlock::class.java).size)
    }

    fun testSevenLevelsWithPlainBody() {
        assertNesting(7, "1")
    }

    fun testSevenLevelsWithParenCallBody() {
        assertNesting(7, "b.field(\"k\", 1)")
    }

    fun testTenLevelsWithParenCallBody() {
        assertNesting(10, "b.field(\"k\", 1)")
    }
}
