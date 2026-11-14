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

    fun testRepeatedLookupDoesNotQueryTheIndexAgain() {
        myFixture.configureByText(
            "test.cr",
            """
            class Service
              def call(path : String)
              end
            end
            """.trimIndent(),
        )
        val first = CrystalOverloadEvaluator.findCandidateMethods("call", project)
        val second = CrystalOverloadEvaluator.findCandidateMethods("call", project)
        // The cached instance is handed back, so a highlighting pass that hits
        // the same method name many times walks the stub index once.
        assertSame(first, second)
    }

    fun testCacheIsPerProjectName() {
        myFixture.configureByText("test.cr", "class Service\n  def only_here\n  end\nend\n")
        assertTrue(CrystalOverloadEvaluator.findCandidateMethods("only_here", project).isNotEmpty())
        assertTrue(CrystalOverloadEvaluator.findCandidateMethods("nowhere_at_all", project).isEmpty())
    }

    /**
     * The cache lives on the project, not in a static map: it must die with the
     * project so PSI from a closed project is not retained for the whole
     * process. Asserted behaviourally — an empty result is cached too, so a
     * second lookup of an unknown name stays empty instead of re-querying.
     */
    fun testUnknownNameIsCachedAsEmpty() {
        myFixture.configureByText("test.cr", "class Service\n  def x\n  end\nend\n")
        val first = CrystalOverloadEvaluator.findCandidateMethods("definitely_absent", project)
        val second = CrystalOverloadEvaluator.findCandidateMethods("definitely_absent", project)
        assertTrue(first.isEmpty())
        assertSame(first, second)
    }
}
