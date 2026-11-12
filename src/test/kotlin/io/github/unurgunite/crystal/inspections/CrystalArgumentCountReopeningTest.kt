package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * `def Type.new(...)` reopenings (stdlib ships `def Nil.new(pull : JSON::PullParser)`,
 * `def Array.new(pull, &)`, `def Time::Location.new(pull)`, …) are top-level
 * definitions whose declaring type lives in the def header. They used to look
 * ownerless and were offered as candidates for every unrelated `X.new`.
 */
class CrystalArgumentCountReopeningTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentCountInspection())
        myFixture.addFileToProject(
            "lib/types.cr",
            """
            def Nil.new(pull : Int32) : self
              pull
            end

            def Array.new(pull : Int32, &)
              pull
            end

            def Time::Location.new(pull : Int32)
              pull
            end
            """.trimIndent(),
        )
    }

    fun testReopeningsAreNotCandidatesForUnrelatedReceiver() {
        myFixture.configureByText(
            "a.cr",
            """
            module Shop
              class RouteCollection
                def each
                end
              end
            end

            a = Shop::RouteCollection.new
            b = Shop::RouteCollection.new 1, 2
            puts "after"
            """.trimIndent(),
        )
        assertEmpty(myFixture.doHighlighting().filter { it.description != null })
    }

    fun testReopeningIsCandidateForItsOwnType() {
        myFixture.configureByText(
            "a.cr",
            """
            x = Nil.new
            y = Nil.new 1
            puts "after"
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting().filter { it.description != null }
        assertEquals(highlights.joinToString { it.description }, 1, highlights.size)
        assertEquals("Missing required argument(s): 'pull'", highlights.single().description)
    }

    fun testRealConstructorStillChecked() {
        myFixture.configureByText(
            "a.cr",
            """
            class Widget
              def initialize(name : String, size : Int32); end
            end

            a = Widget.new
            b = Widget.new "a", 1
            puts "after"
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting().filter { it.description != null }
        assertEquals(highlights.joinToString { it.description }, 1, highlights.size)
        assertEquals("Missing required argument(s): 'name', 'size'", highlights.single().description)
    }
}
