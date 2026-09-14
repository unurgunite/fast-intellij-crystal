package io.github.unurgunite.crystal

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTemplateContextTypeTest : BasePlatformTestCase() {
    private val contextType = CrystalTemplateContextType()

    fun testCrystalFileIsInContext() {
        val file = myFixture.configureByText("test.cr", "x = 1\n")
        val context = TemplateActionContext.expanding(file, 0)
        assertTrue(contextType.isInContext(context))
    }

    fun testForeignFileIsOutOfContext() {
        val file = myFixture.configureByText("notes.txt", "hello\n")
        val context = TemplateActionContext.expanding(file, 0)
        assertFalse(contextType.isInContext(context))
    }
}
