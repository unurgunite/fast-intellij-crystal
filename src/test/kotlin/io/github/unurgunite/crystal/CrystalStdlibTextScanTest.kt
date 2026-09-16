package io.github.unurgunite.crystal

import io.github.unurgunite.crystal.psi.CrystalPsiUtils
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

    @Test
    fun `fun in lib is indexed qualified and bare`() {
        val symbols =
            scan(
                text =
                    """
                    lib C
                      fun strlen(s : Char*) : SizeT
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["C#strlen"])
        assertNotNull(symbols["strlen"])
    }

    @Test
    fun `fun alias indexes the alias name`() {
        val symbols =
            scan(
                text =
                    """
                    lib LLVM
                      fun build_icmp = LLVMBuildICmp(a : Int32) : Int32
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["LLVM#build_icmp"])
        assertNull(symbols["LLVM#LLVMBuildICmp"])
    }

    @Test
    fun `top-level fun is indexed bare`() {
        val symbols =
            scan(
                text = "fun __crystal_malloc(size : UInt32) : Void*\nend\n",
            )
        assertNotNull(symbols["__crystal_malloc"])
    }

    @Test
    fun `def beats fun for the bare key`() {
        // Walk order in one scanText call is fixed, but the cross-file VFS
        // order is not: both directions must end with the def winning, and
        // the qualified lib key must survive in both.
        val defText = "lib C\n  fun sleep(x : Int32) : Int32\nend\ndef sleep(x)\nend\n"
        val funFirst = scan(text = defText)
        // Offset points at the method name, past the `def ` prefix.
        assertEquals(defText.indexOf("def sleep") + 4, funFirst["sleep"]!!.offset)
        val revText = "def sleep(x)\nend\nlib C\n  fun sleep(x : Int32) : Int32\nend\n"
        val defFirst = scan(text = revText)
        // `fun` never overwrites: the earlier `def` offset survives.
        assertEquals(revText.indexOf("def sleep") + 4, defFirst["sleep"]!!.offset)
        assertNotNull(defFirst["C#sleep"])
        assertNotNull(funFirst["C#sleep"])
    }

    @Test
    fun `struct field is indexed qualified only`() {
        val symbols =
            scan(
                text =
                    """
                    struct Point
                      x : Int32
                      @y : String = "a"
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Point#x"])
        assertNotNull(symbols["Point#y"])
        assertNull(symbols["x"])
    }

    @Test
    fun `local annotation inside def is not a field`() {
        val symbols =
            scan(
                text =
                    """
                    struct Point
                      x : Int32
                      def foo
                        y : Int32 = 1
                        y
                      end
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Point#x"])
        assertNull(symbols["Point#y"])
        // `foo` itself is a real def and must stay indexed.
        assertNotNull(symbols["Point#foo"])
    }

    @Test
    fun `bare colon shape outside type is not a field`() {
        val symbols = scan(text = "x : Int32\n")
        assertNull(symbols["x"])
    }

    @Test
    fun `enum members generate predicates`() {
        val symbols =
            scan(
                text =
                    """
                    enum Color
                      Red
                      DarkBlue
                      IO
                      UInt128x
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Color#red?"])
        assertNotNull(symbols["Color#dark_blue?"])
        assertNotNull(symbols["Color#io?"])
        assertNotNull(symbols["Color#u_int128x?"])
        // Predicates are instance-side keys only — no bare or `::` pollution.
        assertNull(symbols["red?"])
        assertNull(symbols["Color::Red"])
    }

    @Test
    fun `enum member with explicit value and alias generate predicates`() {
        val symbols =
            scan(
                text =
                    """
                    @[Flags]
                    enum MyFlags
                      Default = LineNumbers
                      A, B
                      MAX = 3
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["MyFlags#default?"])
        assertNotNull(symbols["MyFlags#a?"])
        assertNotNull(symbols["MyFlags#b?"])
        assertNotNull(symbols["MyFlags#max?"])
        // ALL-CAPS members stay real constants too.
        assertNotNull(symbols["MyFlags::MAX"])
    }

    @Test
    fun `enum defs are methods not members`() {
        val symbols =
            scan(
                text =
                    """
                    enum Color
                      Red
                      def foo
                      end
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Color#red?"])
        assertNotNull(symbols["Color#foo"])
        assertNull(symbols["Color#def?"])
    }

    @Test
    fun `class constants get no predicates`() {
        val symbols =
            scan(
                text =
                    """
                    class Foo
                      MAX = 1
                      Red = 2
                    end
                    """.trimIndent(),
            )
        assertNotNull(symbols["Foo::MAX"])
        assertNull(symbols["Foo#max?"])
        assertNull(symbols["Foo#red?"])
    }

    @Test
    fun `crystal underscore matches compiler`() {
        assertEquals("dark_blue", CrystalPsiUtils.crystalUnderscore("DarkBlue"))
        assertEquals("io", CrystalPsiUtils.crystalUnderscore("IO"))
        assertEquals("u_int128x", CrystalPsiUtils.crystalUnderscore("UInt128x"))
        assertEquals("html_parser", CrystalPsiUtils.crystalUnderscore("HTMLParser"))
        assertEquals("io_error", CrystalPsiUtils.crystalUnderscore("IOError"))
    }
}
