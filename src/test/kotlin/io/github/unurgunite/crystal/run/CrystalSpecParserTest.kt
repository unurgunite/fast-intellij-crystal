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

class CrystalSpecParserTest {
    @Test
    fun testParse_simpleSuite() {
        val output =
            """
            |Calculator
            |  adds  adds
            |  subtracts  subtracts
            |Finished in 0.01s
            |2 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(1, tree.size)
        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("Calculator", suite.name)
        assertEquals(2, suite.children.size)
        val test1 = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("adds", test1.name)
        assertEquals("Calculator adds", test1.fullName)
        assertFalse(test1.failed)
    }

    @Test
    fun testParse_nestedSuites() {
        val output =
            """
            |User
            |  when admin
            |    can delete  can delete
            |  when guest
            |    cannot delete  cannot delete
            |Finished in 0.01s
            |2 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(1, tree.size)
        val userSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("User", userSuite.name)
        assertEquals(2, userSuite.children.size)

        val adminSuite = userSuite.children[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("when admin", adminSuite.name)
        assertEquals(1, adminSuite.children.size)
        assertEquals("can delete", (adminSuite.children[0] as CrystalTestEventsConverter.TestNode.Test).name)

        val guestSuite = userSuite.children[1] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("when guest", guestSuite.name)
        assertEquals(1, guestSuite.children.size)
    }

    @Test
    fun testParse_multipleTopLevelSuites() {
        val output =
            """
            |Math
            |  adds  adds
            |String
            |  concat  concat
            |Finished in 0.01s
            |2 examples, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(2, tree.size)
        assertEquals("Math", tree[0].name)
        assertEquals("String", tree[1].name)

        val mathTests = (tree[0] as CrystalTestEventsConverter.TestNode.Suite).children
        assertEquals(1, mathTests.size)
        assertEquals("adds", (mathTests[0] as CrystalTestEventsConverter.TestNode.Test).name)

        val stringTests = (tree[1] as CrystalTestEventsConverter.TestNode.Suite).children
        assertEquals(1, stringTests.size)
        assertEquals("concat", (stringTests[0] as CrystalTestEventsConverter.TestNode.Test).name)
    }

    @Test
    fun testParse_failureMarksTestAsFailed() {
        val output =
            """
            |Math basics
            |  adds  adds
            |  fails  fails
            |Failures:
            |  1) Math basics fails
            |     Failure/Error: assert false
            |     # spec/math_spec.cr:10
            |Finished in 0.01s
            |2 examples, 1 failure
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(1, tree.size)
        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals(2, suite.children.size)

        val passingTest = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("adds", passingTest.name)
        assertFalse(passingTest.failed)

        val failingTest = suite.children[1] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("fails", failingTest.name)
        assertTrue(failingTest.failed)
        assertEquals("assert false", failingTest.failureMessage)
        assertEquals("${CrystalTestLocator.PROTOCOL}://spec/math_spec.cr:10", failingTest.failureDetails)
    }

    @Test
    fun testParse_multipleFailures() {
        val output =
            """
            |Math basics
            |  adds  adds
            |  fails1  fails1
            |  fails2  fails2
            |Failures:
            |  1) Math basics fails1
            |     Failure/Error: assert 1 == 2
            |  2) Math basics fails2
            |     Failure/Error: assert nil
            |Finished in 0.01s
            |3 examples, 2 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals(3, suite.children.size)

        assertFalse((suite.children[0] as CrystalTestEventsConverter.TestNode.Test).failed)
        assertTrue((suite.children[1] as CrystalTestEventsConverter.TestNode.Test).failed)
        assertTrue((suite.children[2] as CrystalTestEventsConverter.TestNode.Test).failed)
        assertEquals("assert 1 == 2", (suite.children[1] as CrystalTestEventsConverter.TestNode.Test).failureMessage)
        assertEquals("assert nil", (suite.children[2] as CrystalTestEventsConverter.TestNode.Test).failureMessage)
    }

    @Test
    fun testParse_failureInNestedSuite() {
        val output =
            """
            |User
            |  when admin
            |    can delete  can delete
            |    fails here  fails here
            |  when guest
            |    cannot delete  cannot delete
            |Failures:
            |  1) User when admin fails here
            |     Failure/Error: boom
            |Finished in 0.01s
            |3 examples, 1 failure
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val userSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        val adminSuite = userSuite.children[0] as CrystalTestEventsConverter.TestNode.Suite

        val failingTest = adminSuite.children[1] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("fails here", failingTest.name)
        assertTrue(failingTest.failed)
        assertEquals("boom", failingTest.failureMessage)

        val guestSuite = userSuite.children[1] as CrystalTestEventsConverter.TestNode.Suite
        val guestTest = guestSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertFalse(guestTest.failed)
    }

    @Test
    fun testParse_allPassing() {
        val output =
            """
            |Math
            |  adds  adds
            |Finished in 0.01s
            |1 example, 0 failures
            """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        val test = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertFalse(test.failed)
        assertEquals("", test.failureMessage)
    }

    @Test
    fun testParse_emptyOutput() {
        val tree = CrystalTestEventsConverter.parseForTest("")
        assertEquals(0, tree.size)
    }
}
