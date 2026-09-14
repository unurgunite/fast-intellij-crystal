package io.github.unurgunite.crystal.debugger

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.run.CrystalCommand
import io.github.unurgunite.crystal.run.CrystalRunConfiguration
import io.github.unurgunite.crystal.run.CrystalRunConfigurationType
import io.github.unurgunite.crystal.run.CrystalSpecFactory

class CrystalDebugProgramRunnerTest : BasePlatformTestCase() {
    private val runner = CrystalDebugProgramRunner()

    private fun crystalConfig(): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", CrystalCommand.RUN)
    }

    fun testRunnerId() {
        assertEquals("CrystalDebugRunner", runner.runnerId)
    }

    fun testCanRunDebugExecutorWithCrystalConfig() {
        assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, crystalConfig()))
    }

    fun testCannotRunPlainRunExecutor() {
        assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, crystalConfig()))
    }
}
