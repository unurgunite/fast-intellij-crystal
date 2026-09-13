package io.github.unurgunite.crystal.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRunConfigurationProducerTest : BasePlatformTestCase() {

    private val producer = CrystalRunConfigurationProducer()

    private fun configsFromCaret(code: String, path: String): List<CrystalRunConfiguration> {
        val file = myFixture.configureByText(path, code)
        val element = file.findElementAt(myFixture.caretOffset)
        assertNotNull("No element at caret", element)
        val context = ConfigurationContext(element!!)
        return (context.createConfigurationsFromContext() ?: emptyList())
            .mapNotNull { it.configurationSettings?.configuration as? CrystalRunConfiguration }
    }

    private fun singleConfigFromCaret(code: String, path: String): CrystalRunConfiguration {
        val configs = configsFromCaret(code, path)
        assertEquals("Expected exactly one config for $path, got $configs", 1, configs.size)
        return configs[0]
    }

    fun testPlainFileProducesRun() {
        val config = singleConfigFromCaret("x = <caret>1\n", "main.cr")
        assertEquals(CrystalCommand.RUN, config.command)
        assertEquals("run: main.cr", config.name)
        assertTrue(config.filePath.endsWith("main.cr"))
        assertEquals(0, config.specLine)
    }

    fun testNonCrystalFileProducesNothing() {
        val configs = configsFromCaret("hello <caret>world", "notes.txt")
        assertTrue("No Crystal config expected for .txt, got $configs", configs.isEmpty())
    }

    fun testSpecFileTopLevelRunsWholeFile() {
        // Caret outside any it/describe block → whole file (specLine 0).
        // (Caret ON the describe line would target line 1 — that is covered below.)
        val config = singleConfigFromCaret("# top<caret>\ndescribe \"math\" do\nend\n", "math_spec.cr")
        assertEquals(CrystalCommand.SPEC, config.command)
        assertEquals(0, config.specLine)
        assertEquals("spec: math_spec.cr", config.name)
    }

    fun testCaretInsideItRunsSingleLine() {
        val config = singleConfigFromCaret(
            "describe \"math\" do\n  it \"adds<caret> numbers\" do\n  end\nend\n",
            "math_spec.cr"
        )
        assertEquals(CrystalCommand.SPEC, config.command)
        assertEquals(2, config.specLine)
        assertEquals("spec: adds numbers", config.name)
    }

    fun testIsConfigurationFromContextRoundTrip() {
        val config = singleConfigFromCaret("x = <caret>1\n", "main.cr")
        val file = myFixture.file
        val element = file.findElementAt(myFixture.caretOffset)!!
        val context = ConfigurationContext(element)
        assertTrue(producer.isConfigurationFromContext(config, context))
    }

    fun testIsConfigurationFromContextRejectsForeignPath() {
        val config = singleConfigFromCaret("x = <caret>1\n", "main.cr")
        config.filePath = "/elsewhere/other.cr"
        val file = myFixture.file
        val element = file.findElementAt(myFixture.caretOffset)!!
        val context = ConfigurationContext(element)
        assertFalse(producer.isConfigurationFromContext(config, context))
    }

    fun testDirectoryContextRunsAllSpecs() {
        val dirFile = myFixture.tempDirFixture.getFile("spec")
            ?: myFixture.tempDirFixture.findOrCreateDir("spec")
        val dir = com.intellij.psi.PsiManager.getInstance(project).findDirectory(dirFile)
        assertNotNull("Should resolve temp spec dir", dir)
        val context = ConfigurationContext(dir!!)
        val configs = (context.createConfigurationsFromContext() ?: emptyList())
            .mapNotNull { it.configurationSettings?.configuration as? CrystalRunConfiguration }
        assertEquals(1, configs.size)
        assertEquals(CrystalCommand.SPEC, configs[0].command)
        assertEquals(0, configs[0].specLine)
        assertEquals("spec: spec", configs[0].name)
    }
}
