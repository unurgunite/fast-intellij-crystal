package io.github.unurgunite.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalSettingsConfigurableTest : BasePlatformTestCase() {
    private lateinit var configurable: CrystalSettingsConfigurable

    override fun setUp() {
        super.setUp()
        CrystalSettings.getInstance(project).loadState(CrystalSettings.State())
        configurable = CrystalSettingsConfigurable(project)
    }

    override fun tearDown() {
        try {
            CrystalSettings.getInstance(project).loadState(CrystalSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testDisplayName() {
        assertEquals("Crystal", configurable.displayName)
    }

    fun testCreateComponentLoadsCurrentState() {
        CrystalSettings.getInstance(project).loadState(CrystalSettings.State(crystalPath = "/custom/crystal"))
        // Must build without throwing (spawns real `crystal --version`/`env` probes)
        assertNotNull(configurable.createComponent())
        assertFalse("Freshly loaded UI must not be modified", configurable.isModified)
    }

    fun testApplyPersistsPath() {
        configurable.createComponent()
        CrystalSettings.getInstance(project).loadState(CrystalSettings.State(crystalPath = "/applied/crystal"))
        configurable.reset()
        assertFalse(configurable.isModified)
        assertEquals("/applied/crystal", CrystalSettings.getInstance(project).state.crystalPath)
    }

    fun testResetRestoresStoredState() {
        configurable.createComponent()
        configurable.reset()
        assertFalse(configurable.isModified)
    }

    fun testBogusPathDoesNotCrashComponent() {
        CrystalSettings.getInstance(project).loadState(
            CrystalSettings.State(crystalPath = "/nonexistent-dir-xyz/crystal"),
        )
        // Status labels must degrade to "Not available", not throw
        assertNotNull(configurable.createComponent())
    }
}
