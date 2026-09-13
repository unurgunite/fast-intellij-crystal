package io.github.unurgunite.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume

class CrystalStdlibLibraryProviderTest : BasePlatformTestCase() {

    private val realFiles = mutableListOf<java.io.File>()

    override fun tearDown() {
        try {
            realFiles.forEach { it.delete() }
            realFiles.clear()
            CrystalStdlibLibraryProvider.clearCache()
            CrystalSettings.getInstance(project).loadState(CrystalSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private val provider = CrystalStdlibLibraryProvider()

    /**
     * Writes a marker into the REAL project base dir. Fixture files live under
     * temp:// which LocalFileSystem cannot see, but the provider deliberately
     * uses model-free LocalFileSystem checks — so the test does too.
     */
    private fun writeBaseFile(name: String, content: String) {
        val basePath = project.basePath
        assertNotNull("No base path in test project", basePath)
        val file = java.io.File(basePath!!, name)
        // The fixture base dir may have been cleaned between tests — recreate it.
        file.parentFile?.mkdirs()
        file.writeText(content)
        realFiles.add(file)
        com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(file.absolutePath)
    }

    fun testNonCrystalProjectYieldsNoLibrary() {
        // Empty temp project: no shard.yml, no .cr files → no library, no crash.
        // Must not touch the workspace model (infinite-save-loop regression guard).
        CrystalStdlibLibraryProvider.clearCache()
        assertTrue(provider.getAdditionalProjectLibraries(project).isEmpty())
    }

    fun testMissingCrystalBinaryYieldsNoLibrary() {
        CrystalStdlibLibraryProvider.clearCache()
        writeBaseFile("shard.yml", "name: demo")
        CrystalSettings.getInstance(project).loadState(
            CrystalSettings.State(crystalPath = "/nonexistent-dir-xyz/crystal")
        )
        assertTrue(
            "Unresolvable stdlib must yield no library, not a crash",
            provider.getAdditionalProjectLibraries(project).isEmpty()
        )
    }

    fun testCrystalProjectYieldsStableLibrary() {
        Assume.assumeTrue(
            "Requires installed Crystal binary",
            CrystalStdlibResolver.resolveStdlibPath(project) != null
        )
        writeBaseFile("shard.yml", "name: demo")
        CrystalStdlibLibraryProvider.clearCache()
        val first = provider.getAdditionalProjectLibraries(project)
        assertEquals(1, first.size)
        val roots = first.first().sourceRoots
        assertEquals(1, roots.size)
        assertTrue(roots.first().isDirectory)
        // Second call must return an equal library (platform treats inequality as "changed").
        val second = provider.getAdditionalProjectLibraries(project)
        assertEquals(first, second)
        assertEquals(first.first().hashCode(), second.first().hashCode())
    }
}
