package io.github.unurgunite.crystal.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRunStateCommandLineTest : BasePlatformTestCase() {
    private fun createConfig(command: CrystalCommand): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", command)
    }

    fun testRunCommandWithFile() {
        val config = createConfig(CrystalCommand.RUN)
        config.crystalPath = "/usr/bin/crystal"
        config.filePath = "/tmp/main.cr"
        val cmd = CrystalRunState.buildCommandLine(config)
        assertEquals("/usr/bin/crystal", cmd.exePath)
        assertEquals(listOf("run", "/tmp/main.cr"), cmd.parametersList.list)
    }

    fun testRunAppendsDoubleDashBeforeProgramArgs() {
        val config = createConfig(CrystalCommand.RUN)
        config.filePath = "/tmp/main.cr"
        config.arguments = "--verbose input.txt"
        val cmd = CrystalRunState.buildCommandLine(config)
        assertEquals(
            listOf("run", "/tmp/main.cr", "--", "--verbose", "input.txt"),
            cmd.parametersList.list,
        )
    }

    fun testBuildHasNoDoubleDash() {
        val config = createConfig(CrystalCommand.BUILD)
        config.filePath = "/tmp/main.cr"
        config.arguments = "--release"
        val cmd = CrystalRunState.buildCommandLine(config)
        assertEquals(listOf("build", "/tmp/main.cr", "--release"), cmd.parametersList.list)
    }

    fun testBlankFilePathAddsNoParameter() {
        val config = createConfig(CrystalCommand.RUN)
        config.filePath = ""
        val cmd = CrystalRunState.buildCommandLine(config)
        assertEquals(listOf("run"), cmd.parametersList.list)
    }

    fun testEnvVarsLandInEnvironment() {
        val config = createConfig(CrystalCommand.RUN)
        config.filePath = "/tmp/main.cr"
        config.environmentVariables = "FOO=bar\nBAZ=qux"
        val cmd = CrystalRunState.buildCommandLine(config)
        assertEquals("bar", cmd.environment["FOO"])
        assertEquals("qux", cmd.environment["BAZ"])
    }
}
