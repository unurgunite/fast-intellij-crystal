package io.github.unurgunite.crystal.inlay

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.psi.CrystalAssignment

/**
 * Tests for Inlay Hints (P3): `CrystalTypeHint.compute` unit behavior plus
 * provider/factory wiring. Rendering itself is platform-owned; we assert the
 * computed offset + text the collector would hand to the sink.
 */
class CrystalInlayHintsTest : BasePlatformTestCase() {
    private fun hintsIn(code: String): List<CrystalTypeHint> {
        val file = myFixture.configureByText("test.cr", code)
        return PsiTreeUtil
            .collectElementsOfType(file, CrystalAssignment::class.java)
            .mapNotNull { CrystalTypeHint.compute(it) }
            .sortedBy { it.offset }
    }

    fun testLiteralAssignmentHint() {
        val hints = hintsIn("x = 1\n")
        assertEquals(1, hints.size)
        assertEquals(": Int32", ": ${hints[0].typeText}")
        assertEquals(1, hints[0].offset)
    }

    fun testStringAssignmentHint() {
        val hints = hintsIn("name = \"hello\"\n")
        assertEquals(listOf("String"), hints.map { it.typeText })
    }

    fun testAnnotatedAssignmentSkipped() {
        assertTrue(hintsIn("x : Int32 = 1\n").isEmpty())
    }

    fun testIvarAssignmentSkipped() {
        assertTrue(
            hintsIn(
                """
                class Foo
                  def bar
                    @x = 1
                  end
                end
                """.trimIndent(),
            ).isEmpty(),
        )
    }

    fun testNilAssignmentSkipped() {
        assertTrue(hintsIn("x = nil\n").isEmpty())
    }

    fun testUnionTypeHint() {
        val hints = hintsIn("x = true ? 1 : nil\n")
        assertEquals(1, hints.size)
        assertEquals("Int32 | Nil", hints[0].typeText)
    }

    fun testMultipleAssignments() {
        val hints =
            hintsIn(
                """
                a = 1
                b = "s"
                """.trimIndent(),
            )
        assertEquals(listOf("Int32", "String"), hints.map { it.typeText })
    }

    fun testChainedAssignmentOuterHint() {
        // `a = b = 1` parses as one assignment whose RHS is the `b = 1` chain
        // link: only the outer name gets a hint from this statement shape.
        val hints = hintsIn("a = b = 1\n")
        assertEquals(1, hints.size)
        assertEquals("Int32", hints[0].typeText)
    }

    fun testFactoryRegisteredForCrystalLanguage() {
        val factory = CrystalInlayHintsProviderFactory()
        assertTrue(factory.getSupportedLanguages().contains(CrystalLanguage))
        val infos = factory.getProvidersForLanguage(CrystalLanguage)
        assertEquals(1, infos.size)
        assertEquals(CrystalInlayHintsProvider.PROVIDER_ID, infos[0].providerId)
        assertTrue(infos[0].isEnabledByDefault)
        assertEquals(
            infos[0],
            factory.getProviderInfo(CrystalLanguage, CrystalInlayHintsProvider.PROVIDER_ID),
        )
    }

    fun testFactoryEmptyForOtherLanguages() {
        val factory = CrystalInlayHintsProviderFactory()
        assertTrue(
            factory.getProvidersForLanguage(com.intellij.lang.Language.ANY).isEmpty(),
        )
    }

    fun testCollectorDelegatesToCompute() {
        val file = myFixture.configureByText("test.cr", "x = 42\n")
        val assignment = PsiTreeUtil.collectElementsOfType(file, CrystalAssignment::class.java).first()
        val provider = CrystalInlayHintsProvider()
        val seen = ArrayList<String>()
        val sink =
            object : com.intellij.codeInsight.hints.declarative.InlayTreeSink {
                override fun addPresentation(
                    position: com.intellij.codeInsight.hints.declarative.InlayPosition,
                    payloads: List<com.intellij.codeInsight.hints.declarative.InlayPayload>?,
                    tooltip: String?,
                    hintFormat: com.intellij.codeInsight.hints.declarative.HintFormat,
                    builder: com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder.() -> Unit,
                ) {
                    seen.add(tooltip ?: "?")
                }

                override fun whenOptionEnabled(
                    optionId: String,
                    block: () -> Unit,
                ) {
                    block()
                }
            }
        provider.collectFromElement(assignment, sink)
        assertEquals(listOf("Int32"), seen)
    }
}
