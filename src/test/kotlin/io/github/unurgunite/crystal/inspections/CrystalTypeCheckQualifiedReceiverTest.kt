package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A qualified `.new` receiver must resolve against its full path.
 *
 * `findClassNameBeforeNewFromArgs` used to hand back only the last
 * NAMESPACE_ACCESS segment, so `Crystal::Location.new(nil, 2, 3)` resolved the
 * bare `Location` and landed on an unrelated same-named class
 * (`Time::Location`, whose `initialize(name : String, zones : Array(Zone))`),
 * reporting `expected 'Array(Zone)', got 'Int32'`.
 */
class CrystalTypeCheckQualifiedReceiverTest : BasePlatformTestCase() {
    fun testQualifiedConstructorDoesNotResolveUnrelatedSameNamedClass() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/time_location.cr",
            """
            class Time::Location
              def initialize(name : String, zones : Array(Int32), @a, @b)
              end
            end
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "lib/crystal_location.cr",
            """
            class Crystal::Location
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            subject = Crystal::Location.new(nil, 2, 3)
            puts subject
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testQualifiedConstructorStillTypeChecksItsOwnClass() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/other.cr",
            """
            class Other::Location
              def initialize(name : String)
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            class Crystal::Location
              def initialize(name : String)
              end
            end
            subject = Crystal::Location.new(<error descr="Type mismatch: expected 'String', got 'Int32'">1</error>)
            puts subject
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
