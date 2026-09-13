package io.github.unurgunite.crystal.run

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalRunLineMarkerProviderTest : BasePlatformTestCase() {
    private val provider = CrystalRunLineMarkerProvider()

    private fun identifierAt(
        text: String,
        specFile: Boolean = true,
    ): com.intellij.psi.PsiElement {
        val name = if (specFile) "sample_spec.cr" else "main.cr"
        val file = myFixture.configureByText(name, text)
        val found =
            PsiTreeUtil
                .collectElements(file) {
                    it is LeafPsiElement && it.elementType == CrystalTypes.IDENTIFIER
                }.firstOrNull()
        assertNotNull("No identifier found", found)
        return found!!
    }

    fun testDescribeGetsMarker() {
        val element = identifierAt("describe \"math\" do\nend\n")
        assertEquals("describe", element.text)
        val info = provider.getInfo(element)
        assertNotNull("describe should get a gutter icon", info)
        assertEquals("Run spec suite", info!!.tooltipProvider?.apply(element))
    }

    fun testItGetsMarker() {
        val file =
            myFixture.configureByText(
                "sample_spec.cr",
                "describe \"math\" do\n  it \"adds\" do\n  end\nend\n",
            )
        val itIdent =
            PsiTreeUtil
                .collectElements(file) {
                    it is LeafPsiElement && it.elementType == CrystalTypes.IDENTIFIER && it.text == "it"
                }.firstOrNull()
        assertNotNull(itIdent)
        val info = provider.getInfo(itIdent!!)
        assertNotNull("it should get a gutter icon", info)
        assertEquals("Run spec", info!!.tooltipProvider?.apply(itIdent))
    }

    fun testNonSpecFileGetsNoMarker() {
        val element = identifierAt("describe \"math\" do\nend\n", specFile = false)
        assertNull("Non-spec files must not get markers", provider.getInfo(element))
    }

    fun testOrdinaryCallGetsNoMarker() {
        val element = identifierAt("puts \"hello\"\n")
        assertEquals("puts", element.text)
        assertNull("Ordinary calls must not get markers", provider.getInfo(element))
    }

    fun testNonLeafGetsNoMarker() {
        val file = myFixture.configureByText("sample_spec.cr", "describe \"math\" do\nend\n")
        val composite =
            PsiTreeUtil.findChildOfType(
                file,
                io.github.unurgunite.crystal.psi.CrystalBareCommandExpression::class.java,
            )
        if (composite != null) {
            assertNull(provider.getInfo(composite))
        }
    }
}
