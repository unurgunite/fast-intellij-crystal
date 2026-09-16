package io.github.unurgunite.crystal.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalProvidersTest : BasePlatformTestCase() {
    private fun strings(items: List<com.intellij.codeInsight.lookup.LookupElement>): Set<String> = items.map { it.lookupString }.toSet()

    // NOTE: stdlib/project/self/annotation data is covered end-to-end via the
    // completion pipeline in CrystalCompletionTypeAnnotationTest
    // (testTypeAnnotationSuggestsStdlibTypes, testTypeAnnotationInsideClassIncludesSelf,
    // testTypeAnnotationTopLevelNoSelf, testTypeAnnotationIncludesProjectTypes,
    // testAnnotationCompletion). Only data/edge cases unreachable through the
    // pipeline live here.
    fun testClassBodyLookups() {
        val names = strings(CrystalClassBodyCompletionProvider.getClassBodyLookups())
        for (expected in listOf("getter", "setter", "property", "delegate", "include", "abstract", "macro", "alias")) {
            assertTrue("$expected in class-body lookups", expected in names)
        }
    }

    fun testOverrideLookups() {
        val items = CrystalOverrideMethodProvider.getOverrideLookups()
        assertEquals(9, items.size)
        val names = strings(items)
        for (expected in listOf("initialize", "to_s", "inspect", "hash", "==", "finalize")) {
            assertTrue("$expected in override lookups", expected in names)
        }
        val toString = items.first { it.lookupString == "to_s" }
        assertTrue(
            "to_s should carry its signature, got: ${toString.allLookupStrings}",
            "to_s(io : IO)" in toString.allLookupStrings,
        )
    }

    fun testEnclosingTypeLookups() {
        myFixture.addFileToProject(
            "nested.cr",
            """
class Foo
  class Sub
  end

  module Mod
  end
end
            """.trimIndent(),
        )
        val names = strings(CrystalTypeCompletionProvider.getEnclosingTypeLookups("Foo", project))
        assertTrue("Sub in $names", "Sub" in names)
        assertTrue("Mod in $names", "Mod" in names)
    }

    fun testEnclosingTypeLookupsUnknownEnclosing() {
        myFixture.addFileToProject(
            "nested.cr",
            """
class Foo
end
            """.trimIndent(),
        )
        assertTrue(
            CrystalTypeCompletionProvider.getEnclosingTypeLookups("Nope", project).isEmpty(),
        )
    }
}
