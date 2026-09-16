package io.github.unurgunite.crystal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.references.CrystalDotCallReceiver
import io.github.unurgunite.crystal.psi.references.ReceiverInfo

/**
 * Direct tests for [CrystalDotCallReceiver] — the receiver half of DOT-call
 * resolution. Previously covered only indirectly through
 * `CrystalDotCallReferenceTest` (which asserts the resolved target, never the
 * receiver classification). These pin the classification itself: static vs
 * instance, qualified names, literal types, and the null edges.
 */
class CrystalDotCallReceiverTest : BasePlatformTestCase() {
    private fun receiverAtCaret(code: String): ReceiverInfo? {
        myFixture.configureByText("test.cr", code)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val dotCall = PsiTreeUtil.getParentOfType(leaf, CrystalDotCallAccess::class.java, false) ?: return null
        return CrystalDotCallReceiver.resolve(dotCall)
    }

    fun testConstantReceiverIsStatic() {
        val info = receiverAtCaret("Apfel.tan<caret>zen")
        assertNotNull(info)
        assertTrue(info!!.isStatic)
        assertEquals(listOf("Apfel"), info.classNames)
        assertEquals("Apfel", info.rawName)
    }

    fun testNamespaceReceiverBuildsQualifiedName() {
        val info = receiverAtCaret("Outer::Inner.r<caret>un")
        assertNotNull(info)
        assertTrue(info!!.isStatic)
        assertEquals(listOf("Inner"), info.classNames)
        assertEquals("Outer::Inner", info.qualifiedName)
    }

    fun testIdentifierReceiverInfersType() {
        val info =
            receiverAtCaret(
                """
                class Apfel
                  def essen
                  end
                end
                a = Apfel.new
                a.es<caret>sen
                """.trimIndent(),
            )
        assertNotNull(info)
        assertFalse(info!!.isStatic)
        assertEquals(listOf("Apfel"), info.classNames)
    }

    fun testIdentifierReceiverUnknownTypeYieldsEmpty() {
        val info =
            receiverAtCaret(
                """
                def consume(thing)
                  thing.es<caret>sen
                end
                """.trimIndent(),
            )
        assertNotNull(info)
        assertFalse(info!!.isStatic)
        assertTrue("Unknown receiver must yield no class names", info.classNames.isEmpty())
    }

    fun testSelfReceiverYieldsEnclosingType() {
        val info =
            receiverAtCaret(
                """
                class Apfel
                  def munch
                    self.es<caret>sen
                  end
                end
                """.trimIndent(),
            )
        assertNotNull(info)
        assertEquals(listOf("Apfel"), info!!.classNames)
    }

    fun testArrayLiteralReceiverYieldsArray() {
        val info = receiverAtCaret("[1, 2, 3].siz<caret>e")
        assertNotNull(info)
        assertEquals(listOf("Array"), info!!.classNames)
    }

    fun testStringLiteralReceiverYieldsString() {
        val info = receiverAtCaret("\"hello\".upcas<caret>e")
        assertNotNull(info)
        assertEquals(listOf("String"), info!!.classNames)
    }

    fun testIntegerLiteralReceiverYieldsInt() {
        val info = receiverAtCaret("x = 1.t<caret>o_s")
        assertNotNull(info)
        assertFalse(info!!.classNames.isEmpty())
    }
}
