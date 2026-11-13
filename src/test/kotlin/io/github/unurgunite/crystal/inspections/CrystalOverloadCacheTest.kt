package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The candidate cache must never outlive a PSI change: a fixed signature that
 * keeps being reported after the user edits it is the worst failure mode of a
 * cache in an inspection.
 */
class CrystalOverloadCacheTest : BasePlatformTestCase() {
    fun testCandidatesAreNotStaleAfterEdit() {
        myFixture.configureByText(
            "test.cr",
            """
            class Service
              def call(path : String)
              end
            end
            """.trimIndent(),
        )
        assertEquals(1, CrystalOverloadEvaluator.findCandidateMethods("call", project).size)
        // A second `call` appears after the first lookup populated the cache.
        // A cache that only sees names, not PSI stamp, would still say 1.
        myFixture.addFileToProject("other.cr", "class Other\n  def call(path : String, size : Int32)\n  end\nend\n")
        assertEquals(2, CrystalOverloadEvaluator.findCandidateMethods("call", project).size)
    }

    fun testCacheIsPerProjectName() {
        myFixture.configureByText("test.cr", "class Service\n  def only_here\n  end\nend\n")
        assertTrue(CrystalOverloadEvaluator.findCandidateMethods("only_here", project).isNotEmpty())
        assertTrue(CrystalOverloadEvaluator.findCandidateMethods("nowhere_at_all", project).isEmpty())
    }
}
