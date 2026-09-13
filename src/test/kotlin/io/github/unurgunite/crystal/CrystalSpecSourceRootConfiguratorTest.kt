package io.github.unurgunite.crystal

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.java.JavaSourceRootType

class CrystalSpecSourceRootConfiguratorTest : BasePlatformTestCase() {
    private val configurator = CrystalSpecSourceRootConfigurator()
    private val realFiles = mutableListOf<java.io.File>()

    override fun tearDown() {
        try {
            realFiles.forEach { it.deleteRecursively() }
            realFiles.clear()
            // Drain async VFS/index fallout synchronously: without this, the
            // create/delete events for the real marker files land during a later
            // test class's highlighting ("PSI changes are not allowed during
            // highlighting" — bisected cross-test pollution).
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .refresh(false)
            com.intellij.testFramework.PlatformTestUtil
                .dispatchAllEventsInIdeEventQueue()
        } finally {
            super.tearDown()
        }
    }

    private fun writeBaseDir(vararg names: String): java.io.File {
        val basePath = project.basePath
        assertNotNull("No base path in test project", basePath)
        val base = java.io.File(basePath!!)
        base.mkdirs()
        val lfs =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
        for (name in names) {
            val child = java.io.File(base, name)
            if (name.endsWith("/")) child.mkdirs() else child.writeText("name: probe")
            realFiles.add(child)
            // Refresh each created path directly: refreshing only the parent dir
            // does not reliably surface new children to findFileByPath.
            lfs.refreshAndFindFileByPath(child.absolutePath)
        }
        return base
    }

    private fun testSourceRoots(): List<String> =
        ModuleManager.getInstance(project).modules.flatMap { module ->
            ModuleRootManager.getInstance(module).contentEntries.flatMap { entry ->
                entry.getSourceFolders(JavaSourceRootType.TEST_SOURCE).mapNotNull { it.file?.path }
            }
        }

    fun testNonCrystalProjectUntouched() {
        // No shard.yml on disk → early return, no roots added, no crash
        val before = testSourceRoots()
        runBlocking { configurator.execute(project) }
        assertEquals(before, testSourceRoots())
    }

    fun testMissingSpecDirUntouched() {
        writeBaseDir("shard.yml")
        val before = testSourceRoots()
        runBlocking { configurator.execute(project) }
        assertEquals(before, testSourceRoots())
    }

    fun testOwnershipPredicate() {
        assertTrue(
            CrystalSpecSourceRootConfigurator.isUnderContentRoot(
                "/proj/spec",
                "/proj",
            ),
        )
        assertFalse(
            CrystalSpecSourceRootConfigurator.isUnderContentRoot(
                "/other/spec",
                "/proj",
            ),
        )
        assertFalse(
            CrystalSpecSourceRootConfigurator.isUnderContentRoot(
                "/proj-other/spec",
                "/proj",
            ),
        )
    }
}
