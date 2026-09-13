package io.github.unurgunite.crystal.project

import org.junit.Assert.*
import org.junit.Test

class CrystalDirectoryProjectGeneratorTest {

    @Test
    fun testProjectSettingsDefaults() {
        val settings = CrystalProjectSettings()
        assertEquals("app", settings.projectType)
        assertEquals("", settings.crystalPath)
    }

    @Test
    fun testGeneratorMetadata() {
        val generator = CrystalDirectoryProjectGenerator()
        assertEquals("Crystal", generator.name)
        assertEquals("Create a new Crystal project (application or library)", generator.description)
        assertNotNull(generator.logo)
        assertNotNull(generator.createPeer())
    }

    @Test
    fun testPeerReturnsDefaultSettingsBeforeUIInitialised() {
        val peer = CrystalProjectGeneratorPeer()
        val settings = peer.getSettings()
        assertEquals("app", settings.projectType)
        assertEquals("", settings.crystalPath)
    }

    @Test
    fun testResolveCrystalPathPrefersExplicitSetting() {
        assertEquals(
            "/custom/crystal",
            CrystalDirectoryProjectGenerator.resolveCrystalPath(
                CrystalProjectSettings(projectType = "app", crystalPath = "/custom/crystal")
            )
        )
    }

    @Test
    fun testResolveCrystalPathFallsBackToDetector() {
        val resolved = CrystalDirectoryProjectGenerator.resolveCrystalPath(
            CrystalProjectSettings(projectType = "app", crystalPath = "")
        )
        assertTrue("Fallback must be non-blank, got: '$resolved'", resolved.isNotBlank())
    }

    @Test
    fun testMissingGitignoreEntriesBothMissing() {
        val additions = CrystalDirectoryProjectGenerator.missingGitignoreEntries("*.cr\n")
        assertTrue(".idea/" in additions)
        assertTrue("*.iml" in additions)
    }

    @Test
    fun testMissingGitignoreEntriesPartial() {
        val additions = CrystalDirectoryProjectGenerator.missingGitignoreEntries(".idea/\n*.cr\n")
        assertFalse(".idea/" in additions.replace("*.iml", ""))
        assertTrue("*.iml" in additions)
    }

    @Test
    fun testMissingGitignoreEntriesNoneMissing() {
        assertTrue(
            CrystalDirectoryProjectGenerator.missingGitignoreEntries(".idea/\n*.iml\n").isBlank()
        )
    }
}
