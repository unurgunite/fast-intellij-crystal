package io.github.unurgunite.crystal.run

import junit.framework.TestCase

class CrystalRunConfigurationOptionsTest : TestCase() {
    fun testDefaults() {
        val options = CrystalRunConfigurationOptions()
        assertEquals("", options.filePath)
        assertEquals("", options.arguments)
        assertEquals("", options.workingDirectory)
        assertEquals("", options.environmentVariables)
        assertEquals("crystal", options.crystalPath)
    }

    fun testSettersRoundTrip() {
        val options = CrystalRunConfigurationOptions()
        options.filePath = "/tmp/main.cr"
        options.arguments = "--verbose"
        options.workingDirectory = "/tmp"
        options.environmentVariables = "A=1"
        options.crystalPath = "/usr/bin/crystal"
        assertEquals("/tmp/main.cr", options.filePath)
        assertEquals("--verbose", options.arguments)
        assertEquals("/tmp", options.workingDirectory)
        assertEquals("A=1", options.environmentVariables)
        assertEquals("/usr/bin/crystal", options.crystalPath)
    }

    fun testCommandValues() {
        assertEquals("run", CrystalCommand.RUN.command)
        assertEquals("build", CrystalCommand.BUILD.command)
        assertEquals("spec", CrystalCommand.SPEC.command)
    }
}
