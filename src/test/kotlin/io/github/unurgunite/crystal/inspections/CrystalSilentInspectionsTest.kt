package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Five inspections reported nothing on a 2851-file shard project, which is
 * not proof they work — an inspection that never fires looks exactly like a
 * clean project. Each one gets a minimal case that must fire here.
 */
class CrystalSilentInspectionsTest : BasePlatformTestCase() {
    fun testColonSpacingIsReported() {
        myFixture.enableInspections(CrystalColonSpacingInspection())
        myFixture.configureByText("test.cr", "def greet(name :String)\n  name\nend\n")
        assertReported()
    }

    fun testSingleQuoteStringIsReported() {
        myFixture.enableInspections(CrystalSingleQuoteStringInspection())
        myFixture.configureByText("test.cr", "message = 'plain'\nputs message\n")
        assertReported()
    }

    fun testEmptyCollectionIsReported() {
        myFixture.enableInspections(CrystalEmptyCollectionInspection())
        myFixture.configureByText("test.cr", "items = []\nputs items\n")
        assertReported()
    }

    fun testInstanceVarTypeIsReported() {
        myFixture.enableInspections(CrystalInstanceVarTypeInspection())
        myFixture.configureByText(
            "test.cr",
            """
            class Holder
              @value : Object

              def initialize(@value : Object)
              end
            end
            """.trimIndent(),
        )
        assertReported()
    }

    fun testLibFunParameterTypeIsReported() {
        myFixture.enableInspections(CrystalLibFunParameterTypeInspection())
        myFixture.configureByText(
            "test.cr",
            """
            lib LibC
              fun puts(message) : Int32
            end
            """.trimIndent(),
        )
        assertReported()
    }

    private fun assertReported() {
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertTrue("inspection produced no problem", descriptions.isNotEmpty())
    }
}
