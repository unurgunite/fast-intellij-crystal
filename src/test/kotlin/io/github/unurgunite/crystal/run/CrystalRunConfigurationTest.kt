package io.github.unurgunite.crystal.run

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.debugger.CrystalDebugRunState
import org.jdom.Element

class CrystalRunConfigurationTest : BasePlatformTestCase() {

    private fun createConfig(command: CrystalCommand): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", command)
    }

    fun testFactoryDefaultCommands() {
        val type = CrystalRunConfigurationType()
        val byId = type.configurationFactories.associateBy { it.id }
        assertEquals(CrystalCommand.RUN, (byId["Crystal Run"]!!.createTemplateConfiguration(project) as CrystalRunConfiguration).command)
        assertEquals(CrystalCommand.BUILD, (byId["Crystal Build"]!!.createTemplateConfiguration(project) as CrystalRunConfiguration).command)
        assertEquals(CrystalCommand.SPEC, (byId["Crystal Spec"]!!.createTemplateConfiguration(project) as CrystalRunConfiguration).command)
    }

    fun testOptionDefaults() {
        val config = createConfig(CrystalCommand.RUN)
        assertEquals("", config.filePath)
        assertEquals("", config.arguments)
        assertEquals("", config.environmentVariables)
        assertEquals("crystal", config.crystalPath)
        assertEquals(0, config.specLine)
        assertEquals(project.basePath ?: "", config.workingDirectory)
    }

    fun testOptionSettersRoundTrip() {
        val config = createConfig(CrystalCommand.SPEC)
        config.filePath = "/tmp/spec/foo_spec.cr"
        config.arguments = "--verbose --tag focus"
        config.environmentVariables = "FOO=bar\nBAZ=qux"
        config.crystalPath = "/usr/local/bin/crystal"
        config.workingDirectory = "/tmp"
        config.specLine = 42
        assertEquals("/tmp/spec/foo_spec.cr", config.filePath)
        assertEquals("--verbose --tag focus", config.arguments)
        assertEquals("FOO=bar\nBAZ=qux", config.environmentVariables)
        assertEquals("/usr/local/bin/crystal", config.crystalPath)
        assertEquals("/tmp", config.workingDirectory)
        assertEquals(42, config.specLine)
    }

    fun testWriteReadExternalRoundTrip() {
        val config = createConfig(CrystalCommand.SPEC)
        config.filePath = "/tmp/spec/foo_spec.cr"
        val element = Element("configuration")
        config.writeExternal(element)
        assertEquals("SPEC", element.getAttributeValue("crystal-command"))

        val restored = createConfig(CrystalCommand.RUN)
        restored.readExternal(element)
        assertEquals(CrystalCommand.SPEC, restored.command)
    }

    fun testBogusCommandFallsBackToRun() {
        val element = Element("configuration")
        element.setAttribute("crystal-command", "NOT_A_COMMAND")
        val config = createConfig(CrystalCommand.SPEC)
        config.readExternal(element)
        assertEquals(CrystalCommand.RUN, config.command)
    }

    fun testGetStateRoutesByCommand() {
        val runExecutor = DefaultRunExecutor.getRunExecutorInstance()
        val runConfig = createConfig(CrystalCommand.RUN)
        val runEnv = ExecutionEnvironmentBuilder.create(runExecutor, runConfig).build()
        assertTrue(runConfig.getState(runExecutor, runEnv) is CrystalRunState)

        val specConfig = createConfig(CrystalCommand.SPEC)
        val specEnv = ExecutionEnvironmentBuilder.create(runExecutor, specConfig).build()
        assertTrue(specConfig.getState(runExecutor, specEnv) is CrystalTestRunState)
    }

    fun testGetStateRoutesDebugToDap() {
        val debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance()
        val config = createConfig(CrystalCommand.RUN)
        val env = ExecutionEnvironmentBuilder.create(debugExecutor, config).build()
        assertTrue(config.getState(debugExecutor, env) is CrystalDebugRunState)
    }
}
