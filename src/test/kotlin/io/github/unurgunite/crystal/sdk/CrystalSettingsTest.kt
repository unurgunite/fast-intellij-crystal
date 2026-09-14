package io.github.unurgunite.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalSettingsTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            CrystalSettings.getInstance(project).loadState(CrystalSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testConfiguredPathWinsOverDetector() {
        CrystalSettings.getInstance(project).loadState(
            CrystalSettings.State(crystalPath = "/custom/crystal"),
        )
        assertEquals("/custom/crystal", CrystalSettings.getInstance(project).getEffectiveCrystalPath())
    }

    fun testBlankPathFallsBackToDetector() {
        CrystalSettings.getInstance(project).loadState(CrystalSettings.State(crystalPath = ""))
        val effective = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        assertTrue("Fallback must be non-blank, got: '$effective'", effective.isNotBlank())
    }

    fun testStateRoundTrip() {
        val settings = CrystalSettings.getInstance(project)
        settings.loadState(CrystalSettings.State(crystalPath = "/tmp/crystal"))
        assertEquals("/tmp/crystal", settings.state.crystalPath)
    }
}
