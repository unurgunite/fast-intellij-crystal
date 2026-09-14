package io.github.unurgunite.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume
import java.io.File

class CrystalStdlibResolverTest : BasePlatformTestCase() {
    override fun tearDown() {
        // Restore default settings so a bogus path cannot leak into other tests.
        try {
            CrystalSettings.getInstance(project).loadState(CrystalSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun useCrystalBinary(path: String) {
        CrystalSettings.getInstance(project).loadState(CrystalSettings.State(crystalPath = path))
    }

    private fun realCrystalAvailable(): Boolean {
        val detected = CrystalSdkDetector.detect() ?: return false
        if (!File(detected).canExecute() && !File(detected).isFile) {
            // PATH lookup result that is not a direct file — verify via --version
            return CrystalStdlibResolver.resolveCrystalVersion(project) != null
        }
        return true
    }

    fun testBogusBinaryReturnsNullPath() {
        useCrystalBinary("/nonexistent-dir-xyz/crystal")
        assertNull(
            "Bogus crystal binary must yield null stdlib path",
            CrystalStdlibResolver.resolveStdlibPath(project),
        )
    }

    fun testBogusBinaryReturnsNullVersion() {
        useCrystalBinary("/nonexistent-dir-xyz/crystal")
        assertNull(
            "Bogus crystal binary must yield null version",
            CrystalStdlibResolver.resolveCrystalVersion(project),
        )
    }

    fun testNonExecutableFileReturnsNull() {
        val tmp = File.createTempFile("not-crystal", ".bin")
        tmp.writeText("#!/bin/sh\nexit 1\n")
        tmp.setExecutable(false)
        try {
            useCrystalBinary(tmp.absolutePath)
            assertNull(CrystalStdlibResolver.resolveStdlibPath(project))
            assertNull(CrystalStdlibResolver.resolveCrystalVersion(project))
        } finally {
            tmp.delete()
        }
    }

    fun testDefaultSettingsFallBackToDetector() {
        val path = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        assertTrue("Effective path must fall back to a non-blank value", path.isNotBlank())
    }

    fun testResolveStdlibPath() {
        Assume.assumeTrue("Requires installed Crystal binary", realCrystalAvailable())
        val path = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Should resolve stdlib path when Crystal is installed", path)
        assertTrue("Stdlib path should be a directory", path!!.isDirectory)
        assertTrue("Stdlib path should contain .cr files", path.children.any { it.extension == "cr" })
    }

    fun testResolveCrystalVersion() {
        Assume.assumeTrue("Requires installed Crystal binary", realCrystalAvailable())
        val version = CrystalStdlibResolver.resolveCrystalVersion(project)
        assertNotNull("Should resolve Crystal version when Crystal is installed", version)
        assertTrue("Version should contain 'Crystal'", version!!.contains("Crystal"))
    }

    fun testIsCrystalProject() {
        Assume.assumeTrue("Requires installed Crystal binary", realCrystalAvailable())
        // Test with shard.yml
        myFixture.addFileToProject("shard.yml", "name: test")
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Should resolve stdlib for Crystal project", stdlibRoot)
    }
}
