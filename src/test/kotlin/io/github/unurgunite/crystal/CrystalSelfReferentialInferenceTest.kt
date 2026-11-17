package io.github.unurgunite.crystal

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.type.CrystalTypeInference

/**
 * A self-referential assignment (`m = m.copy_with id: 1`) asks for the type of
 * `m` while computing that same type. Resolving one such line re-resolves every
 * assignment to the name, so without a re-entry guard the cost is N^depth: a
 * handful of lines took seconds, and a real file with seventeen never finished
 * highlighting (the IDE thread stayed at 100% CPU indefinitely).
 */
class CrystalSelfReferentialInferenceTest : BasePlatformTestCase() {
    private fun chain(count: Int): String {
        val body = (1..count).joinToString("\n") { "m = m.copy_with id: $it" }
        return "m = TableMapping.new\n$body\nm<caret>\n"
    }

    private fun contextElement(): PsiElement = myFixture.file.findElementAt(myFixture.caretOffset) ?: error("no element at caret")

    /** The guard must not swallow the real initializer: the type is still there. */
    fun testSelfReferentialChainResolvesToInitializer() {
        myFixture.configureByText("test.cr", chain(10))

        assertEquals("TableMapping", CrystalTypeInference.inferType("m", contextElement(), project))
    }

    /**
     * Guards the exponential blowup itself: the same shape needs ~20ms with the
     * re-entry guard, but 27s at six lines and minutes at ten without it. The
     * budget is deliberately enormous so only a return to exponential fan-out
     * can trip it.
     */
    fun testSelfReferentialChainHighlightsWithoutBlowup() {
        myFixture.configureByText("test.cr", chain(10))

        val start = System.nanoTime()
        myFixture.doHighlighting()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("highlighting took ${elapsedMs}ms", elapsedMs < 60_000)
    }

    /** Two variables referring to each other must not ping-pong either. */
    fun testMutualReferenceCycleTerminates() {
        myFixture.configureByText(
            "test.cr",
            """
            a = b.foo
            b = a.bar
            a<caret>
            """.trimIndent(),
        )

        myFixture.doHighlighting()
    }

    /** An ordinary reassignment still contributes its type alongside the others. */
    fun testReassignmentStillAccumulatesCandidates() {
        myFixture.configureByText("test.cr", "x = 1\nx = \"s\"\nx<caret>\n")

        val type = CrystalTypeInference.inferType("x", contextElement(), project)
        assertEquals(setOf("Int32", "String"), type?.split(" | ")?.toSet())
    }
}
