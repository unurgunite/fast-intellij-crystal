package io.github.unurgunite.crystal.run

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTestRunStateCommandLineTest : BasePlatformTestCase() {
    private fun createConfig(): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", CrystalCommand.SPEC)
    }

    fun testWholeFileCommand() {
        val config = createConfig()
        config.crystalPath = "/usr/bin/crystal"
        config.filePath = "/tmp/spec/math_spec.cr"
        val cmd = CrystalTestRunState.buildCommandLine(config, null)
        assertEquals("/usr/bin/crystal", cmd.exePath)
        assertEquals(
            listOf("spec", "/tmp/spec/math_spec.cr", "-v", "--no-color"),
            cmd.parametersList.list,
        )
    }

    fun testSingleLineCommand() {
        val config = createConfig()
        config.filePath = "/tmp/spec/math_spec.cr"
        config.specLine = 42
        val cmd = CrystalTestRunState.buildCommandLine(config, null)
        assertTrue(cmd.parametersList.list.contains("/tmp/spec/math_spec.cr:42"))
    }

    fun testDirectoryCommand() {
        val dir =
            kotlin.io.path
                .createTempDirectory("specdir")
                .toFile()
        try {
            val config = createConfig()
            config.filePath = dir.absolutePath
            val cmd = CrystalTestRunState.buildCommandLine(config, null)
            assertTrue(cmd.parametersList.list.contains(dir.absolutePath))
            assertFalse(cmd.parametersList.list.any { it.contains(":") && it.endsWith(":0") })
        } finally {
            dir.deleteRecursively()
        }
    }

    fun testJunitOutputAppended() {
        val config = createConfig()
        config.filePath = "/tmp/spec/math_spec.cr"
        val junit = java.io.File.createTempFile("junit", ".xml")
        try {
            val cmd = CrystalTestRunState.buildCommandLine(config, junit)
            val params = cmd.parametersList.list
            val idx = params.indexOf("--junit_output")
            assertTrue(idx >= 0)
            assertEquals(junit.absolutePath, params[idx + 1])
        } finally {
            junit.delete()
        }
    }

    fun testArgsAndEnv() {
        val config = createConfig()
        config.filePath = "/tmp/spec/math_spec.cr"
        config.arguments = "--tag focus"
        config.environmentVariables = "FOO=bar"
        val cmd = CrystalTestRunState.buildCommandLine(config, null)
        val params = cmd.parametersList.list
        assertTrue(params.contains("--tag"))
        assertTrue(params.contains("focus"))
        assertEquals("bar", cmd.environment["FOO"])
    }
}
