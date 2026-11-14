package io.github.unurgunite.crystal.type

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The alias memo must stay correct when two files declare the same alias name. */
class CrystalAliasLookupCacheTest : BasePlatformTestCase() {
    fun testFileOwnedAliasWinsOverShardAlias() {
        myFixture.addFileToProject("shard.cr", "alias HK = Shop::HTTP\n")
        myFixture.configureByText("local.cr", "alias HK = Local::HTTP\n\nrecord Thing\nend\n")
        val file = myFixture.file

        // Both orders must give the file-local answer for the local file.
        assertEquals("Local::HTTP", CrystalAliasLookup.resolveTarget("HK", project, file))
        assertEquals("Local::HTTP", CrystalAliasLookup.resolveTarget("HK", project, file))
        // A file that declares nothing still sees the shard alias.
        val other = myFixture.addFileToProject("other.cr", "record Other\nend\n")
        assertEquals("Shop::HTTP", CrystalAliasLookup.resolveTarget("HK", project, other))
    }

    fun testExpansionIsMemoizedButNotStale() {
        myFixture.addFileToProject("shard.cr", "alias HK = Shop::HTTP\n")
        val file = myFixture.addFileToProject("use.cr", "record Use\nend\n")

        assertEquals("Shop::HTTP::Controller", CrystalAliasLookup.expand("HK::Controller", project, file))
        assertEquals("Shop::HTTP::Controller", CrystalAliasLookup.expand("HK::Controller", project, file))

        myFixture.addFileToProject("shard2.cr", "alias ZZ = Elsewhere\n")
        assertEquals("Elsewhere::X", CrystalAliasLookup.expand("ZZ::X", project, file))
    }
}
