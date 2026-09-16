package io.github.unurgunite.crystal.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRunSettingsEditorTest : BasePlatformTestCase() {
    private fun createConfig(command: CrystalCommand): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", command)
    }

    fun testCreateEditor() {
        assertNotNull(CrystalRunSettingsEditor(project).component)
    }

    fun testResetAndApplyRoundTrip() {
        val editor = CrystalRunSettingsEditor(project)
        editor.component
        val config = createConfig(CrystalCommand.SPEC)
        config.filePath = "/tmp/spec/foo_spec.cr"
        config.arguments = "--verbose"
        config.workingDirectory = "/tmp"
        config.environmentVariables = "FOO=bar"
        config.crystalPath = "/usr/bin/crystal"
        config.specLine = 7

        editor.resetFrom(config)

        val target = createConfig(CrystalCommand.RUN)
        editor.applyTo(target)
        assertEquals(CrystalCommand.SPEC, target.command)
        assertEquals("/tmp/spec/foo_spec.cr", target.filePath)
        assertEquals("--verbose", target.arguments)
        assertEquals("/tmp", target.workingDirectory)
        assertEquals("FOO=bar", target.environmentVariables)
        assertEquals("/usr/bin/crystal", target.crystalPath)
    }

    fun testResetLoadsDefaults() {
        val editor = CrystalRunSettingsEditor(project)
        editor.component
        val blank = createConfig(CrystalCommand.RUN)
        editor.resetFrom(blank)
        val target = createConfig(CrystalCommand.BUILD)
        editor.applyTo(target)
        assertEquals(CrystalCommand.RUN, target.command)
        assertEquals("", target.filePath)
        assertEquals("crystal", target.crystalPath)
    }
}
