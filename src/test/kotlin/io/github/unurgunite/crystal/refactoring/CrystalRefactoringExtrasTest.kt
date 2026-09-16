package io.github.unurgunite.crystal.refactoring

import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalRenameVerifierTest : BasePlatformTestCase() {
    private val verifier = CrystalRenameVerifier()

    fun testWrongRefactoringIdIsIgnored() {
        // Must return without touching afterData (null-safe no-op)
        verifier.refactoringDone("refactoring.move", null)
        verifier.conflictsDetected("refactoring.move", RefactoringEventData())
        verifier.undoRefactoring("refactoring.move")
        verifier.refactoringStarted("refactoring.move", null)
    }

    fun testRenameWithNullDataIsIgnored() {
        verifier.refactoringDone("refactoring.rename", null)
    }

    fun testRenameInNonCrystalFileIsIgnored() {
        val file = myFixture.configureByText("notes.txt", "hello")
        val data = RefactoringEventData()
        data.putUserData(RefactoringEventData.PSI_ELEMENT_KEY, file)
        // .txt file → verifier must bail before spawning any process
        verifier.refactoringDone("refactoring.rename", data)
    }
}

class CrystalRefactoringSupportProviderTest : BasePlatformTestCase() {
    private val provider = CrystalRefactoringSupportProvider()

    fun testNamedElementsRenameable() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
class Foo
  def bar
  end
end
                """.trimIndent(),
            )
        val classDef =
            com.intellij.psi.util.PsiTreeUtil.findChildOfType(
                file,
                io.github.unurgunite.crystal.psi.CrystalClassDefinition::class.java,
            )!!
        assertNotNull(classDef.nameIdentifier)
        assertTrue(provider.isMemberInplaceRenameAvailable(classDef, classDef.nameIdentifier))
    }

    fun testRawIdentifierTokensRenameable() {
        val file = myFixture.configureByText("test.cr", "x = 1\n")
        val element = file.findElementAt(0)!!
        assertEquals(CrystalTypes.IDENTIFIER, element.node.elementType)
        assertTrue(provider.isMemberInplaceRenameAvailable(element, element))
    }

    fun testWhitespaceNotRenameable() {
        val file = myFixture.configureByText("test.cr", "x = 1\n")
        val element = file.findElementAt(1)!!
        assertFalse(provider.isMemberInplaceRenameAvailable(element, element))
    }
}
