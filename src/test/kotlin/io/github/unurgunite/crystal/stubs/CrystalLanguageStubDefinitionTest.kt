package io.github.unurgunite.crystal.stubs

import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase

class CrystalLanguageStubDefinitionTest : TestCase() {

    private val definition = CrystalLanguageStubDefinition()

    /**
     * The file gate that keeps the platform from invoking stub building for
     * non-Crystal files (the `.groovy`/Database-plugin stall regression guard).
     */
    fun testCrystalFilesPassGate() {
        assertTrue(definition.shouldBuildStubFor(LightVirtualFile("main.cr", "x = 1")))
        assertTrue(definition.shouldBuildStubFor(LightVirtualFile("foo_spec.cr", "x = 1")))
    }

    fun testForeignFilesRefused() {
        assertFalse(definition.shouldBuildStubFor(LightVirtualFile("build.groovy", "")))
        assertFalse(definition.shouldBuildStubFor(LightVirtualFile("notes.txt", "")))
        assertFalse(definition.shouldBuildStubFor(LightVirtualFile("Info.plist", "")))
        assertFalse(definition.shouldBuildStubFor(LightVirtualFile("noextension", "")))
    }

    fun testStubVersionStable() {
        assertEquals(2, definition.stubVersion)
    }
}
