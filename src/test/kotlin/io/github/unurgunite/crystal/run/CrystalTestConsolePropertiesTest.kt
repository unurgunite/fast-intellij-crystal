package io.github.unurgunite.crystal.run

import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTestConsolePropertiesTest : BasePlatformTestCase() {

    private fun createConfig(): CrystalRunConfiguration {
        val type = CrystalRunConfigurationType()
        val factory = type.configurationFactories.first { it is CrystalSpecFactory }
        return CrystalRunConfiguration(project, factory, "test", CrystalCommand.SPEC)
    }

    private fun properties(): CrystalTestConsoleProperties {
        val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
        return CrystalTestConsoleProperties(createConfig(), executor)
    }

    fun testLocatorIsCrystalLocator() {
        val locator: SMTestLocator = properties().testLocator
        assertSame(CrystalTestLocator.INSTANCE, locator)
    }

    fun testConsoleFlags() {
        val props = properties()
        assertFalse(
            com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties.HIDE_PASSED_TESTS.value(props)
        )
        assertTrue(
            com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties.OPEN_FAILURE_LINE.value(props)
        )
        assertTrue(
            com.intellij.execution.testframework.TestConsoleProperties.SCROLL_TO_SOURCE.value(props)
        )
        assertTrue(
            com.intellij.execution.testframework.TestConsoleProperties.SELECT_FIRST_DEFECT.value(props)
        )
    }

    fun testEventsConverterCarriesLocationsAndJunitFile() {
        val locations = mapOf(
            "spec/a_spec.cr" to listOf(
                CrystalSpecFileIndexer.TestLocation("spec/a_spec.cr", 2)
            )
        )
        val junit = java.io.File.createTempFile("junit", ".xml")
        try {
            val executor = com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance()
            val props = CrystalTestConsoleProperties(createConfig(), executor, locations, junit)
            val converter = props.createTestEventsConverter("CrystalSpec", props)
            assertTrue(converter is CrystalTestEventsConverter)
        } finally {
            junit.delete()
        }
    }
}
