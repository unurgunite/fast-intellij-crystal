package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A call on a typed receiver must not fall back to a project-wide lookup of
 * the method name.
 *
 * `resolveScopedOrFallback` guarded the fallback with
 * `receiverClass == null && receiver != null`, which only skipped it for an
 * *unknown* receiver. When the receiver class was known but had no such
 * member (a `record` getter, for example), the project-wide first hit still
 * won — here `Info#id : String` hijacking `existing.id` on a `record`
 * carrying `id : Int64`, reported as `expected Int64, got String`.
 */
class CrystalTypeCheckReceiverFallbackTest : BasePlatformTestCase() {
    fun testTypedReceiverDoesNotTakeUnrelatedSameNamedMethod() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/repo.cr",
            """
            module PersonRepository
              record Identity, id : Int64, person_key : String

              def self.find_by_key(key : String) : Identity?
                Identity.new(1_i64, key)
              end

              def self.empty?(identity : Identity) : Bool
                false
              end
            end

            class Info
              def id : String
                "x"
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "spec.cr",
            """
            def use(key : String, id : Int64)
            end

            def run(key : String)
              existing = PersonRepository.find_by_key(key)
              return unless existing
              raise "no" if PersonRepository.empty?(existing)
              use(key, existing.id)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReceiverScopedMethodStillResolves() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/person.cr",
            """
            class Person
              def to_label : String
                "person"
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            def want(label : String)
            end

            person = Person.new
            want(person.to_label)
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReceiverlessFragmentStillUsesProjectFallback() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/helper.cr",
            """
            def build_name : String
              "name"
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            def want(name : String)
            end

            def run(name : String)
              want(build_name)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
