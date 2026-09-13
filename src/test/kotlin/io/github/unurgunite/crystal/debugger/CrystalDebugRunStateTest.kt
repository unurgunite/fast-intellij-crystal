package io.github.unurgunite.crystal.debugger

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.run.CrystalCommand
import io.github.unurgunite.crystal.run.CrystalRunConfiguration
import io.github.unurgunite.crystal.run.CrystalRunConfigurationType
import io.github.unurgunite.crystal.run.CrystalSpecFactory

class CrystalDebugRunStateTest : BasePlatformTestCase() {

    private fun createConfig(): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", CrystalCommand.RUN)
    }

    private fun debugState(config: CrystalRunConfiguration): CrystalDebugRunState {
        val executor = com.intellij.execution.executors.DefaultDebugExecutor.getDebugExecutorInstance()
        val env = com.intellij.execution.runners.ExecutionEnvironmentBuilder
            .create(executor, config).build()
        return CrystalDebugRunState(env, config)
    }

    fun testIsApplicableOnlyForCrystalConfigs() {
        val state = debugState(createConfig())
        assertTrue(state.isApplicable("Debug", createConfig()))
        assertFalse(
            state.isApplicable(
                "Debug",
                object : com.intellij.execution.configurations.RunProfile {
                    override fun getState(
                        executor: com.intellij.execution.Executor,
                        environment: com.intellij.execution.runners.ExecutionEnvironment
                    ) = null
                    override fun getName() = "foreign"
                    override fun getIcon() = null
                }
            )
        )
    }

    fun testBuildDebugArgsShape() {
        val config = createConfig()
        config.crystalPath = "/usr/bin/crystal"
        config.filePath = "/tmp/main.cr"
        config.workingDirectory = "/tmp/work"
        val args = debugState(config).buildDebugArgs()
        assertEquals(
            listOf("/usr/bin/crystal", "build", "--debug", "/tmp/main.cr", "-o", args.last()),
            args
        )
        assertTrue("Binary should land under bin/, got: ${args.last()}", args.last().endsWith("bin/main"))
    }
}
