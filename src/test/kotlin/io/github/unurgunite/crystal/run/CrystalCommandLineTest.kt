package io.github.unurgunite.crystal.run

import junit.framework.TestCase

class CrystalCommandLineTest : TestCase() {

    fun testSplitArgsBasic() {
        assertEquals(listOf("--verbose", "--tag", "focus"), CrystalCommandLine.splitArgs("--verbose --tag focus"))
    }

    fun testSplitArgsDropsBlanks() {
        assertEquals(listOf("a", "b"), CrystalCommandLine.splitArgs("  a   b  "))
        assertTrue(CrystalCommandLine.splitArgs("").isEmpty())
        assertTrue(CrystalCommandLine.splitArgs("   ").isEmpty())
    }

    fun testParseEnvVarsBasic() {
        assertEquals(
            mapOf("FOO" to "bar", "BAZ" to "qux"),
            CrystalCommandLine.parseEnvVars("FOO=bar\nBAZ=qux")
        )
    }

    fun testParseEnvVarsTrimsAndKeepsEqualsInValue() {
        assertEquals(
            mapOf("KEY" to "a=b=c"),
            CrystalCommandLine.parseEnvVars("  KEY = a=b=c  ")
        )
    }

    fun testParseEnvVarsIgnoresGarbageLines() {
        val parsed = CrystalCommandLine.parseEnvVars("garbage line\nOK=1\n\n")
        assertEquals(mapOf("OK" to "1"), parsed)
    }

    fun testParseEnvVarsEmpty() {
        assertTrue(CrystalCommandLine.parseEnvVars("").isEmpty())
    }
}
