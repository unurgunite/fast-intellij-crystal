package io.github.unurgunite.crystal

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver

/**
 * A call whose return type cannot be resolved must stay UNKNOWN, never become
 * its receiver's type. Reporting the receiver is what produced three separate
 * false-positive families in the 1.2.2 export:
 *
 * - `proc.call("x")` on `Proc(String, String)` typed as the Proc;
 * - `db.query_one?(..., as: {...})` typed as `DB::Database`;
 * - `ids.size` on a generic typed as `Enumerable(String)`.
 */
class CrystalCallReturnTypeTest : BasePlatformTestCase() {
    fun testProcCallYieldsTheBlockReturnType() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def run(reader : Proc(String, String))\n" +
                    "  data = reader.call(\"x\")\n" +
                    "  puts data\n" +
                    "end\n",
            )
        assertEquals("String", typeOf("reader.call(\"x\")", file))
    }

    fun testUnknownCallReturnTypeIsNotTheReceiverType() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Database
                  def query_one?(sql : String, args)
                  end
                end

                def run(db : Database)
                  row = db.query_one?("select 1", nil)
                  puts row
                end
                """.trimIndent(),
            )
        assertNotSame("Database", typeOf("db.query_one?(\"select 1\", nil)", file))
    }

    fun testSizeOnGenericIsInt32() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "def run(ids : Enumerable(String))\n  total = ids.size\n  puts total\nend\n",
            )
        assertEquals("Int32", typeOf("ids.size", file))
    }

    fun testAsNamedArgumentIsTheReturnType() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class ResultSet
                  def query_one?(sql : String, args, as types = "x")
                  end
                end

                def run(db : ResultSet)
                  row = db.query_one?("select 1", nil, as: {Int64, String})
                  puts row
                end
                """.trimIndent(),
            )
        assertEquals("Tuple(Int64, String)", typeOf("db.query_one?(\"select 1\", nil, as: {Int64, String})", file))
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
