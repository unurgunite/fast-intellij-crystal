package io.github.unurgunite.crystal.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrystalSpecRoundTripTest {
    @Test
    fun testRoundTrip_singleDescribeMultipleTests() {
        // Simulate the user's first example: one describe with "works" and "works2"
        val specContent =
            """
            describe Asdf do
              it "works" do
                false.should eq(true)
              end

              it "works2" do
                true.should eq(true)
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            // Simulate Crystal verbose output
            val output =
                """
                |Asdf
                |  works  works
                |  works2  works2
                |Finished in 0.01s
                |2 examples, 0 failures
                """.trimMargin()

            val tree = CrystalTestEventsConverter.parseForTest(output, locations)

            assertEquals(1, tree.size)
            val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            assertEquals("Asdf", suite.name)
            assertEquals(2, suite.children.size)

            val test1 = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
            assertEquals("works", test1.name)
            assertEquals("Asdf works", test1.fullName)
            assertNotNull("Test 'works' should have a URL for navigation", test1.url)
            assertTrue("URL should contain protocol", test1.url!!.startsWith(CrystalTestLocator.PROTOCOL))

            val test2 = suite.children[1] as CrystalTestEventsConverter.TestNode.Test
            assertEquals("works2", test2.name)
            assertEquals("Asdf works2", test2.fullName)
            assertNotNull("Test 'works2' should have a URL for navigation", test2.url)
            assertTrue("URL should contain protocol", test2.url!!.startsWith(CrystalTestLocator.PROTOCOL))

            // Verify URLs point to different lines
            val url1 = test1.url!!.substringAfterLast(":")
            val url2 = test2.url!!.substringAfterLast(":")
            assertNotEquals("Tests should navigate to different lines", url1, url2)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testRoundTrip_multipleDescribeBlocks() {
        val specContent = multipleDescribeBlocksSpecContent()
        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()
            val output = multipleDescribeBlocksOutput()
            val tree = CrystalTestEventsConverter.parseForTest(output, locations)
            assertMultipleDescribeBlocksTree(tree)
        } finally {
            tempFile.delete()
        }
    }

    private fun multipleDescribeBlocksSpecContent(): String =
        """
        describe Asdf do
          it "works" do
            false.should eq(true)
          end

          it "sorks" do
            true.should eq(true)
          end
        end

        describe Cba do
          it "xorks" do
            false.should eq(true)
          end

          it "grks" do
            true.should eq(true)
          end
        end
        """.trimIndent()

    private fun multipleDescribeBlocksOutput(): String =
        """
        |Asdf
        |  works  works
        |  sorks  sorks
        |Cba
        |  xorks  xorks
        |  grks  grks
        |Finished in 0.01s
        |4 examples, 0 failures
        """.trimMargin()

    private fun assertMultipleDescribeBlocksTree(tree: List<CrystalTestEventsConverter.TestNode>) {
        assertEquals(2, tree.size)

        // First describe block
        val asdfSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("Asdf", asdfSuite.name)
        assertEquals(2, asdfSuite.children.size)

        val works = asdfSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("works", works.name)
        assertNotNull("Test 'works' should have a URL", works.url)

        val sorks = asdfSuite.children[1] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("sorks", sorks.name)
        assertNotNull("Test 'sorks' should have a URL", sorks.url)

        // Second describe block
        val cbaSuite = tree[1] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("Cba", cbaSuite.name)
        assertEquals(2, cbaSuite.children.size)

        val xorks = cbaSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("xorks", xorks.name)
        assertNotNull("Test 'xorks' should have a URL", xorks.url)

        val grks = cbaSuite.children[1] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("grks", grks.name)
        assertNotNull("Test 'grks' should have a URL", grks.url)
    }

    @Test
    fun testRoundTrip_identicalTestNamesInDifferentDescribes() {
        // Both describes have "it works" — both should get URLs
        val specContent =
            """
            describe "Foo" do
              it "works" do
                expect(true).to be_true
              end
            end

            describe "Bar" do
              it "works" do
                expect(false).to be_false
              end
            end
            """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            val output =
                """
                |Foo
                |  works  works
                |Bar
                |  works  works
                |Finished in 0.01s
                |2 examples, 0 failures
                """.trimMargin()

            val tree = CrystalTestEventsConverter.parseForTest(output, locations)

            assertEquals(2, tree.size)

            val fooSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            val fooWorks = fooSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
            assertEquals("Foo works", fooWorks.fullName)
            assertNotNull("Foo works should have a URL", fooWorks.url)

            val barSuite = tree[1] as CrystalTestEventsConverter.TestNode.Suite
            val barWorks = barSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
            assertEquals("Bar works", barWorks.fullName)
            assertNotNull("Bar works should have a URL", barWorks.url)

            // Verify they point to different lines
            val fooLine = fooWorks.url!!.substringAfterLast(":")
            val barLine = barWorks.url!!.substringAfterLast(":")
            assertNotEquals("Identical test names in different describes should navigate to different lines", fooLine, barLine)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testJUnitTiming_appliesPerTestDuration() {
        val output =
            """
            |Calculator
            |  adds correctly  adds correctly
            |  subtracts correctly  subtracts correctly
            |  multiplies correctly  multiplies correctly
            |Finished in 150.0 ms
            |3 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val junitXml = File.createTempFile("junit_timing", ".xml")
        try {
            junitXml.writeText(
                """
                <?xml version="1.0"?>
                <testsuite tests="3" time="0.150">
                  <testcase name="Calculator adds correctly" time="0.001"/>
                  <testcase name="Calculator subtracts correctly" time="0.048"/>
                  <testcase name="Calculator multiplies correctly" time="0.101"/>
                </testsuite>
                """.trimIndent(),
            )

            CrystalTestEventsConverter.applyJUnitTimingFromXml(junitXml, tree)

            val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            assertEquals(3, suite.children.size)

            val adds = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
            val subtracts = suite.children[1] as CrystalTestEventsConverter.TestNode.Test
            val multiplies = suite.children[2] as CrystalTestEventsConverter.TestNode.Test

            assertEquals(1L, adds.durationMs)
            assertEquals(48L, subtracts.durationMs)
            assertEquals(101L, multiplies.durationMs)
        } finally {
            junitXml.delete()
        }
    }

    @Test
    fun testJUnitTiming_handlesMissingTimeAttribute() {
        val output =
            """
            |Math
            |  test_a  test_a
            |  test_b  test_b
            |Finished in 50.0 ms
            |2 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val junitXml = File.createTempFile("junit_timing", ".xml")
        try {
            junitXml.writeText(
                """
                <?xml version="1.0"?>
                <testsuite tests="2" time="0.050">
                  <testcase name="Math test_a" time="0.020"/>
                  <testcase name="Math test_b"/>
                </testsuite>
                """.trimIndent(),
            )

            CrystalTestEventsConverter.applyJUnitTimingFromXml(junitXml, tree)

            val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            val testA = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
            val testB = suite.children[1] as CrystalTestEventsConverter.TestNode.Test

            assertEquals(20L, testA.durationMs)
            assertEquals(-1L, testB.durationMs) // unchanged default
        } finally {
            junitXml.delete()
        }
    }

    @Test
    fun testJUnitTiming_duplicateTestNames() {
        // Two tests with the same name in different describe blocks
        val output =
            """
            |Foo
            |  works  works
            |Bar
            |  works  works
            |Finished in 0.01s
            |2 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val junitXml = File.createTempFile("junit_timing", ".xml")
        try {
            junitXml.writeText(
                """
                <?xml version="1.0"?>
                <testsuite tests="2" time="0.030">
                  <testcase name="Foo works" time="0.010"/>
                  <testcase name="Bar works" time="0.020"/>
                </testsuite>
                """.trimIndent(),
            )

            CrystalTestEventsConverter.applyJUnitTimingFromXml(junitXml, tree)

            val fooSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            val barSuite = tree[1] as CrystalTestEventsConverter.TestNode.Suite

            val fooWorks = fooSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
            val barWorks = barSuite.children[0] as CrystalTestEventsConverter.TestNode.Test

            assertEquals(10L, fooWorks.durationMs)
            assertEquals(20L, barWorks.durationMs)
        } finally {
            junitXml.delete()
        }
    }

    @Test
    fun testJUnitTiming_duplicateTestNamesInSameDescribe() {
        // Two tests with the same name in the SAME describe block
        val output =
            """
            |Foo
            |  works  works
            |  works  works
            |Finished in 0.01s
            |2 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val junitXml = File.createTempFile("junit_timing", ".xml")
        try {
            junitXml.writeText(
                """
                <?xml version="1.0"?>
                <testsuite tests="2" time="0.030">
                  <testcase name="Foo works" time="0.005"/>
                  <testcase name="Foo works" time="0.025"/>
                </testsuite>
                """.trimIndent(),
            )

            CrystalTestEventsConverter.applyJUnitTimingFromXml(junitXml, tree)

            val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            assertEquals(2, suite.children.size)

            val first = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
            val second = suite.children[1] as CrystalTestEventsConverter.TestNode.Test

            assertEquals("First test should get first timing", 5L, first.durationMs)
            assertEquals("Second test should get second timing", 25L, second.durationMs)
        } finally {
            junitXml.delete()
        }
    }
}
