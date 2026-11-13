package io.github.unurgunite.crystal

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver

/**
 * `row[0]` must yield the indexed ELEMENT (`Int64`), not the tuple it came
 * from. The index expression has no dedicated PSI node — it is an EXPRESSION
 * of receiver + LBRACKET + index + RBRACKET — so resolution fell through to
 * the first-child descent and handed back the receiver's type.
 */
class CrystalTupleIndexTypeTest : BasePlatformTestCase() {
    fun testLiteralIndexPicksTupleMember() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def run\n" +
                    "  row = {1_i64, \"x\", 2_i32}\n" +
                    "  first = row[0]\n" +
                    "  puts first\n" +
                    "end\n",
            )
        assertEquals("Int64", typeOf("row[0]", file))
    }

    fun testNegativeIndexCountsFromEnd() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def run\n  row = {1_i64, \"x\"}\n  last = row[-1]\n  puts last\nend\n",
            )
        assertEquals("String", typeOf("row[-1]", file))
    }

    fun testArrayIndexYieldsElementType() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def run\n  values = [1, 2, 3]\n  first = values[0]\n  puts first\nend\n",
            )
        assertEquals("Int32", typeOf("values[0]", file))
    }

    private fun typeOf(
        text: String,
        file: com.intellij.psi.PsiFile,
    ): String? {
        val expr =
            PsiTreeUtil
                .findChildrenOfType(file, CrystalExpression::class.java)
                .first { it.text == text }
        return CrystalExpressionTypeResolver.resolveType(expr)?.typeName
    }
}
