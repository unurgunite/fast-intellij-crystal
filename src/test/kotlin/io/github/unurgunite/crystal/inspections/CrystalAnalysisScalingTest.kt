package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Guards the fix for per-file analysis cost growing with project size:
 * [io.github.unurgunite.crystal.type.CrystalCallReturnTypeResolver.lookupReturnType]
 * must not re-walk the project-wide method index for every call site.
 */
class CrystalAnalysisScalingTest : BasePlatformTestCase() {
    /**
     * Same file, same content, measured before and after adding unrelated
     * files that define the same method names. Before the cache this grew
     * superlinearly (12x from 1 to 1001 files for a single name) and a
     * 74-line redirect.cr took 96s per pass; the ratio here stays near 1.
     */
    fun testHighlightingCostDoesNotGrowWithProjectSize() {
        val names = listOf("presence", "emptyq", "merge", "parse", "normalizeb", "to_s", "scheme", "query_params", "port", "host")

        fun analyze(): Long {
            myFixture.doHighlighting() // warm stubs/indexes for the current project state
            val start = System.nanoTime()
            repeat(3) { myFixture.doHighlighting() }
            return (System.nanoTime() - start) / 3
        }

        val caller =
            buildString {
                append("class Redirect\n")
                names.forEach { append("  def $it\n    x\n  end\n") }
                append("  def go(uri : URI)\n")
                names.forEach { append("    $it\n") }
                append("  end\nend\n")
            }
        myFixture.configureByText("caller.cr", caller)
        val small = analyze()

        (0 until 500).forEach { i ->
            myFixture.addFileToProject(
                "pad$i.cr",
                buildString {
                    append("class Pad$i\n")
                    names.forEach { append("  def $it(a : Int32) : String\n    \"x\"\n  end\n") }
                    append("end\n")
                },
            )
        }
        val big = analyze()

        val ratio = big.toDouble() / small.coerceAtLeast(1)
        println("scaling: small=${small / 1000}us big=${big / 1000}us ratio=$ratio")
        // Loose bound: catches unbounded regrowth (was ~4x-12x) without
        // failing on warmup noise, since this suite also runs on CI.
        assertTrue("per-file analysis grew with project size: ${small / 1000}us -> ${big / 1000}us", ratio < 2.5)
    }

    /** A repeated lookup for the same name must not re-scan the index. */
    fun testRepeatedLookupIsMemoized() {
        myFixture.addFileToProject("defs.cr", "class Defs\n  def compute_thing(x : Int32) : String\n    \"x\"\n  end\nend\n")
        myFixture.configureByText("use.cr", "class Use\n  def go\n    compute_thing 1\n  end\nend\n")

        val project = project
        val resolver = io.github.unurgunite.crystal.type.CrystalCallReturnTypeResolver
        assertEquals("String", resolver.lookupReturnType("compute_thing", project))
        assertEquals("String", resolver.lookupReturnType("compute_thing", project))
        // Misses are memoized too.
        assertNull(resolver.lookupReturnType("never_defined_anywhere", project))
        assertNull(resolver.lookupReturnType("never_defined_anywhere", project))
    }

    /** The memo must not survive an edit that changes the return annotation. */
    fun testCacheInvalidatesOnEdit() {
        val file = myFixture.addFileToProject("defs.cr", "class Defs\n  def compute_thing : String\n    \"x\"\n  end\nend\n")
        myFixture.configureByText("use.cr", "class Use\n  def go\n    compute_thing\n  end\nend\n")

        val project = project
        val resolver = io.github.unurgunite.crystal.type.CrystalCallReturnTypeResolver
        assertEquals("String", resolver.lookupReturnType("compute_thing", project))

        myFixture.openFileInEditor(file.virtualFile)
        val doc =
            com.intellij.openapi.fileEditor.FileDocumentManager
                .getInstance()
                .getDocument(file.virtualFile)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            doc.setText(doc.text.replace(": String", ": Int32"))
        }
        com.intellij.psi.PsiDocumentManager
            .getInstance(project)
            .commitAllDocuments()

        assertEquals("Int32", resolver.lookupReturnType("compute_thing", project))
    }
}
