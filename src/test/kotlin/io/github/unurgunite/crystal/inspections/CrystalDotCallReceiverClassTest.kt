package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A DOT-call on a lowercase receiver must be checked against the methods of
 * the receiver's inferred class, not against every method of that name in the
 * project.
 *
 * The lowercase branch of `CrystalDotCallOverloads.resolve` passed the
 * receiver's TEXT (`value`) as if it were a class name and, finding no owner
 * match, returned every index hit for the method name. Each of these shapes
 * was reported as a type mismatch in the 1.2.2 export even though the call is
 * correct.
 */
class CrystalDotCallReceiverClassTest : BasePlatformTestCase() {
    fun testProcCallWithUnrelatedSameNamedMethod() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/importer.cr",
            """
            class Importer
              def call(path : Int32) : Int32
                path
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            def run(reader : Proc(String, String))
              reader.call("data.xml")
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testTypedArgumentOnInferredReceiver() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/timeout.cr",
            """
            class Timeout
              def wait(seconds : Int32) : Nil
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            def run
              timeout = Timeout.new
              timeout.wait(5)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRealMismatchOnInferredReceiverIsStillReported() {
        myFixture.enableInspections(CrystalTypeCheckInspection())
        myFixture.addFileToProject(
            "lib/timeout.cr",
            """
            class Timeout
              def wait(seconds : Int32) : Nil
              end
            end
            """.trimIndent(),
        )
        myFixture.configureByText(
            "test.cr",
            """
            def run
              timeout = Timeout.new
              timeout.wait(<error descr="Type mismatch: expected 'Int32', got 'String'">"soon"</error>)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
