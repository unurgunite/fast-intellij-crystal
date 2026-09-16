package io.github.unurgunite.crystal.formatting

import junit.framework.TestCase
import org.junit.Assume

class CrystalFormattingServiceTest : TestCase() {
    private val service = CrystalFormattingService()

    fun testParseFormatErrorWithValidSyntaxError() {
        val stderr = "syntax error in 'STDIN:642:11': invalid regex: PCRE2 does not support \\F, \\L, \\l, \\N{name}, \\U, or \\u at 22"
        val result = service.parseFormatError(stderr, "playground.cr")

        assertTrue("Should mention file name", result.contains("playground.cr"))
        assertTrue("Should mention line", result.contains("line 642"))
        assertTrue("Should mention column", result.contains("column 11"))
        assertTrue("Should include description", result.contains("invalid regex"))
        assertTrue("Should include fix hint", result.contains("fix the syntax error"))
        assertFalse("Should not contain STDIN", result.contains("STDIN"))
    }

    fun testParseFormatErrorWithBlankStderr() {
        val result = service.parseFormatError("", "test.cr")
        assertTrue("Should mention Crystal compiler", result.contains("Crystal compiler"))
        assertTrue("Should mention installed", result.contains("installed"))
    }

    fun testParseFormatErrorWithUnparsableStderr() {
        val stderr = "Some unexpected error occurred"
        val result = service.parseFormatError(stderr, "test.cr")
        assertEquals("Should return raw stderr", stderr, result)
    }

    fun testParseFormatErrorReplacesStdinWithFileName() {
        val stderr = "syntax error in 'STDIN:10:5': unexpected token: end"
        val result = service.parseFormatError(stderr, "foo.cr")
        assertTrue("Should contain real file name", result.contains("foo.cr"))
        assertFalse("Should not contain STDIN", result.contains("STDIN"))
    }

    fun testFormatStdinRoundTripWithRealCrystal() {
        val crystal = findCrystal()
        Assume.assumeTrue("Requires installed Crystal binary", crystal != null)
        val formatted = service.formatStdin(crystal!!, "x     =     1\n", System.getProperty("java.io.tmpdir"))
        assertNotNull("Formatting should succeed, error was: ${service.lastFormatError}", formatted)
        assertEquals("x = 1\n", formatted)
    }

    fun testFormatStdinSyntaxErrorReturnsNull() {
        val crystal = findCrystal()
        Assume.assumeTrue("Requires installed Crystal binary", crystal != null)
        val formatted = service.formatStdin(crystal!!, "def broken(((\n", System.getProperty("java.io.tmpdir"))
        assertNull("Syntax errors must yield null", formatted)
        assertNotNull("Error details must be recorded", service.lastFormatError)
    }

    fun testFormatStdinBogusBinaryReturnsNull() {
        val formatted = service.formatStdin("/nonexistent-dir-xyz/crystal", "x = 1\n", System.getProperty("java.io.tmpdir"))
        assertNull(formatted)
        assertNotNull(service.lastFormatError)
    }

    private fun findCrystal(): String? {
        for (candidate in listOf("/opt/homebrew/bin/crystal", "/usr/bin/crystal", "/usr/local/bin/crystal")) {
            if (java.io.File(candidate).canExecute()) return candidate
        }
        return System
            .getenv("PATH")
            ?.split(":")
            ?.map { java.io.File(it, "crystal") }
            ?.firstOrNull { it.canExecute() }
            ?.absolutePath
    }
}
