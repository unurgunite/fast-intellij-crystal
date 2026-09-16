package io.github.unurgunite.crystal.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.references.SymbolLoc
import io.github.unurgunite.crystal.psi.stdlib.CrystalStdlibFileResolve
import io.github.unurgunite.crystal.sdk.CrystalStdlibResolver

/**
 * Direct tests for [CrystalStdlibFileResolve] — the precise home-file half of
 * stdlib resolution (`ClassName` → `class_name.cr`). Previously covered only
 * indirectly through stdlib goto tests. These pin the mapping contract:
 * nested names map to paths, missing files return null (never a wrong-class
 * match), and materialize promotes leaves to named owners.
 */
class CrystalStdlibFileResolveTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        // Stdlib files live outside the test sandbox roots; allow read access
        // like CrystalDotCallReferenceTest does for stdlib navigation.
        CrystalStdlibResolver.resolveStdlibPath(project)?.path?.let {
            com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
                .allowRootAccess(testRootDisposable, it)
        }
    }

    private fun stdlibRoot(): com.intellij.openapi.vfs.VirtualFile {
        val root =
            CrystalStdlibResolver.resolveStdlibPath(project)?.let {
                com.intellij.openapi.vfs.LocalFileSystem
                    .getInstance()
                    .findFileByPath(it.path)
            }
        assertNotNull("Stdlib must be resolvable in test env", root)
        return root!!
    }

    fun testClassResolvesToHomeFile() {
        val loc = CrystalStdlibFileResolve.resolveMemberInFile(project, stdlibRoot(), "String", null)
        assertNotNull("String should resolve to its home file", loc)
        assertEquals("string.cr", loc!!.relPath)
    }

    fun testNestedClassMapsToNestedPath() {
        // Nested class maps to a nested path: File::Info -> file/info.cr.
        // (resolveByName splits on the LAST `::` as class + member, so the
        // nested-path mapping is exercised via resolveMemberInFile directly.)
        val loc = CrystalStdlibFileResolve.resolveMemberInFile(project, stdlibRoot(), "File::Info", null)
        assertNotNull("File::Info should resolve via file/info.cr", loc)
        assertEquals("file/info.cr", loc!!.relPath)
    }

    fun testMissingClassReturnsNull() {
        val loc = CrystalStdlibFileResolve.resolveMemberInFile(project, stdlibRoot(), "NoSuchClassXyz", null)
        assertNull("Missing class must return null, never a wrong-class match", loc)
    }

    fun testMemberResolvesInsideHomeFile() {
        val loc = CrystalStdlibFileResolve.resolveMemberInFile(project, stdlibRoot(), "String", "upcase")
        assertNotNull("String#upcase should resolve inside string.cr", loc)
        assertEquals("string.cr", loc!!.relPath)
    }

    fun testMissingMemberReturnsNull() {
        val loc =
            CrystalStdlibFileResolve.resolveMemberInFile(project, stdlibRoot(), "String", "no_such_method_xyz")
        assertNull("Missing member must return null", loc)
    }

    fun testMaterializePromotesLeafToOwner() {
        val loc = CrystalStdlibFileResolve.resolveMemberInFile(project, stdlibRoot(), "String", null)
        assertNotNull(loc)
        val el = CrystalStdlibFileResolve.materialize(project, stdlibRoot(), loc!!)
        assertNotNull("Materialize must return a live element", el)
    }

    fun testMaterializeMissingFileReturnsNull() {
        val root = stdlibRoot()
        val el = CrystalStdlibFileResolve.materialize(project, root, SymbolLoc("no/such_xyz.cr", 0))
        assertNull("Materialize of a missing file must return null", el)
    }
}
