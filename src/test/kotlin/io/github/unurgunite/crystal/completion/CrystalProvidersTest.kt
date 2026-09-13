package io.github.unurgunite.crystal.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalProvidersTest : BasePlatformTestCase() {

    private fun strings(items: List<com.intellij.codeInsight.lookup.LookupElement>): Set<String> =
        items.map { it.lookupString }.toSet()

    fun testStdlibTypeLookups() {
        val names = strings(CrystalTypeCompletionProvider.getStdlibTypeLookups())
        assertTrue(names.size > 20)
        for (expected in listOf("Int32", "String", "Bool", "Array", "Hash", "Nil", "Float64")) {
            assertTrue("$expected in stdlib lookups", expected in names)
        }
    }

    fun testAnnotationLookups() {
        val names = strings(CrystalAnnotationCompletionProvider.getAnnotationLookups())
        for (expected in listOf("Deprecated", "Flags", "Link", "JSON::Serializable", "YAML::Field")) {
            assertTrue("$expected in annotation lookups", expected in names)
        }
    }

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
            "to_s(io : IO)" in toString.allLookupStrings
        )
    }

    fun testTypeLookupsIncludeProjectTypesAndSelf() {
        val file = myFixture.addFileToProject("types.cr", """
class MyProjectType
  def m(x : Int32)
  end
end
        """.trimIndent())
        val position = file.findElementAt(10)!!
        val names = strings(CrystalTypeCompletionProvider.getTypeLookups(position, project))
        assertTrue("Int32 in type lookups", "Int32" in names)
        assertTrue("MyProjectType in type lookups", "MyProjectType" in names)
        assertTrue("'self' inside class body", "self" in names)
    }

    fun testTypeLookupsNoSelfAtTopLevel() {
        val file = myFixture.addFileToProject("top.cr", "x = 1\n")
        val position = file.findElementAt(0)!!
        val names = strings(CrystalTypeCompletionProvider.getTypeLookups(position, project))
        assertTrue("Int32 in type lookups", "Int32" in names)
        assertFalse("'self' must not appear at top level", "self" in names)
    }

    fun testEnclosingTypeLookups() {
        myFixture.addFileToProject("nested.cr", """
class Foo
  class Sub
  end

  module Mod
  end
end
        """.trimIndent())
        val names = strings(CrystalTypeCompletionProvider.getEnclosingTypeLookups("Foo", project))
        assertTrue("Sub in $names", "Sub" in names)
        assertTrue("Mod in $names", "Mod" in names)
    }

    fun testEnclosingTypeLookupsUnknownEnclosing() {
        myFixture.addFileToProject("nested.cr", """
class Foo
end
        """.trimIndent())
        assertTrue(
            CrystalTypeCompletionProvider.getEnclosingTypeLookups("Nope", project).isEmpty()
        )
    }
}
