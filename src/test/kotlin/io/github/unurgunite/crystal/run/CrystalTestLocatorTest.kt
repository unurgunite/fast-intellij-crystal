package io.github.unurgunite.crystal.run

import com.intellij.execution.PsiLocation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTestLocatorTest : BasePlatformTestCase() {
    private val tempDirs = mutableListOf<java.io.File>()

    override fun tearDown() {
        try {
            tempDirs.forEach { it.deleteRecursively() }
            tempDirs.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun locate(
        path: String,
        protocol: String = CrystalTestLocator.PROTOCOL,
    ) = CrystalTestLocator.INSTANCE.getLocation(
        protocol,
        path,
        project,
        GlobalSearchScope.allScope(project),
    )

    /**
     * Writes a spec to a real temp file on the local filesystem.
     * (Fixture files live under temp:// which LocalFileSystem cannot see;
     * test-runner output always references real paths, so the test does too.)
     */
    private fun writeTempSpec(
        name: String,
        content: String,
    ): String {
        val dir =
            java.nio.file.Files
                .createTempDirectory("crystal-locator-test")
                .toFile()
        tempDirs.add(dir)
        val file = dir.resolve(name)
        file.writeText(content)
        com.intellij.openapi.vfs.LocalFileSystem
            .getInstance()
            .refreshAndFindFileByPath(file.absolutePath)
        return file.absolutePath
    }

    fun testWrongProtocolReturnsEmpty() {
        assertTrue(locate("whatever.cr:10", protocol = "unknown_proto").isEmpty())
    }

    fun testMissingColonReturnsEmpty() {
        assertTrue(locate("nocolonhere").isEmpty())
    }

    fun testMissingFileReturnsEmpty() {
        assertTrue(locate("/nonexistent-dir-xyz/spec/foo_spec.cr:3").isEmpty())
    }

    fun testValidLineNavigatesToElement() {
        val path =
            writeTempSpec(
                "sample_spec.cr",
                """
describe "sample" do
  it "works" do
  end
end
                """.trimIndent(),
            )
        val locations = locate("$path:2")
        assertEquals(1, locations.size)
        val psiLocation = locations[0] as? PsiLocation<*>
        assertNotNull("Should resolve to a PSI location", psiLocation)
        val vFile =
            com.intellij.openapi.vfs.LocalFileSystem
                .getInstance()
                .findFileByPath(path)!!
        val document =
            com.intellij.openapi.fileEditor.FileDocumentManager
                .getInstance()
                .getDocument(vFile)!!
        val lineStart = document.getLineStartOffset(1)
        assertEquals(
            "Should point at line 2",
            document.getLineNumber(psiLocation!!.psiElement.textRange.startOffset),
            1,
        )
        assertTrue(lineStart <= psiLocation.psiElement.textRange.startOffset)
    }

    fun testOutOfRangeLineFallsBackToFile() {
        val path = writeTempSpec("short_spec.cr", "describe \"x\" do\nend\n")
        // Must not throw — clamps to the file instead
        val locations = locate("$path:99999")
        assertEquals(1, locations.size)
    }

    fun testZeroLineReturnsFileLocation() {
        val path = writeTempSpec("zero_spec.cr", "describe \"x\" do\nend\n")
        val locations = locate("$path:0")
        assertEquals(1, locations.size)
    }

    fun testNonNumericLineReturnsFileLocation() {
        val path = writeTempSpec("nonnumeric_spec.cr", "describe \"x\" do\nend\n")
        val locations = locate("$path:abc")
        assertEquals(1, locations.size)
    }
}
