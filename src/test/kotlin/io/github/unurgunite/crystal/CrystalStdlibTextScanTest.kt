package io.github.unurgunite.crystal

import io.github.unurgunite.crystal.psi.CrystalStdlibTextScan
import io.github.unurgunite.crystal.psi.SymbolLoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the stdlib text-symbol scan ([CrystalStdlibTextScan.scanText]).
 * Pure string in/out — no VFS, no IDE.
 *
 * Regression: visibility-prefixed definitions (`private macro`, `protected def`,
 * `abstract def`) were invisible to the symbol table, leaving calls like
 * `interpret_check_args` (a `private macro` in macros/methods.cr) unresolved.
 */
class CrystalStdlibTextScanTest {
    private fun scan(
        relPath: String = "a.cr",
        text: String,
    ): Map<String, SymbolLoc> {
        val symbols = HashMap<String, SymbolLoc>()
        CrystalStdlibTextScan.scanText(relPath, text, symbols, HashSet())
        return symbols
    }

    @Test
    fun `private macro is indexed`() {
        val symbols =
            scan(
                text =
                    """
                    private macro interpret_check_args(*, node = self, &block)
                      foo
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["interpret_check_args"])
    }

    @Test
    fun `protected def is indexed under enclosing type`() {
        val symbols =
            scan(
                text =
                    """
                    class Foo
                      protected def bar
                      end
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Foo#bar"])
    }

    @Test
    fun `abstract def is indexed without eating the next end`() {
        val symbols =
            scan(
                text =
                    """
                    class Foo
                      abstract def bar : String
                      def baz
                      end
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Foo#bar"])
        // The bodiless abstract def owns no `end`: the namespace frame must
        // survive it, so the following def keeps its qualified key.
        assertNotNull(symbols["Foo#baz"])
    }

    @Test
    fun `plain def still indexed`() {
        val symbols =
            scan(
                text =
                    """
                    def top_level_helper
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["top_level_helper"])
    }

    @Test
    fun `def named abstract_foo is a body with an end`() {
        // `def abstract_foo` is NOT an abstract def — its `end` belongs to it,
        // and the enclosing type frame must still be intact afterwards.
        val symbols =
            scan(
                text =
                    """
                    class Foo
                      def abstract_foo
                      end
                      def after
                      end
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Foo#abstract_foo"])
        assertNotNull(symbols["Foo#after"])
    }

    @Test
    fun `offset points at the method name`() {
        val text = "private macro interpret_check_args\nend\n"
        val symbols = scan(text = text)
        val loc = symbols["interpret_check_args"]
        assertNotNull(loc)
        assertEquals("a.cr", loc!!.relPath)
        assertEquals(text.indexOf("interpret_check_args"), loc.offset)
    }

    @Test
    fun `private def with self receiver is indexed`() {
        val symbols =
            scan(
                text =
                    """
                    class Foo
                      private def self.create
                      end
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Foo#create"])
        assertNull(symbols["Foo#self"])
    }
}
