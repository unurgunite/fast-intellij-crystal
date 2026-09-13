package io.github.unurgunite.crystal.sdk

import junit.framework.TestCase
import java.io.File

class CrystalSdkDetectorTest : TestCase() {

    fun testDetectReturnsNullOrExecutable() {
        val detected = CrystalSdkDetector.detect()
        if (detected != null) {
            assertTrue("Detected path must be executable: $detected", File(detected).canExecute())
        }
        // null is valid when Crystal is not installed — no assertion, must not throw
    }

    fun testValidateBogusPathReturnsNull() {
        assertNull(CrystalSdkDetector.validate("/nonexistent-dir-xyz/crystal"))
    }

    fun testValidateDirectoryReturnsNull() {
        val dir = java.nio.file.Files.createTempDirectory("crystal-sdk-test").toFile()
        try {
            assertNull(CrystalSdkDetector.validate(dir.absolutePath))
        } finally {
            dir.delete()
        }
    }

    fun testValidateFakeCrystalScript() {
        val script = File.createTempFile("fake-crystal", ".sh")
        script.writeText("#!/bin/sh\necho 'Crystal 9.9.9 (2026-01-01)'\n")
        script.setExecutable(true)
        try {
            val version = CrystalSdkDetector.validate(script.absolutePath)
            assertNotNull("Script reporting a Crystal version should validate", version)
            assertTrue(version!!.contains("Crystal"))
        } finally {
            script.delete()
        }
    }

    fun testValidateFailingScriptReturnsNull() {
        val script = File.createTempFile("failing-crystal", ".sh")
        script.writeText("#!/bin/sh\necho 'boom'\nexit 1\n")
        script.setExecutable(true)
        try {
            assertNull(CrystalSdkDetector.validate(script.absolutePath))
        } finally {
            script.delete()
        }
    }

    fun testValidateWrongOutputReturnsNull() {
        val script = File.createTempFile("wrong-crystal", ".sh")
        script.writeText("#!/bin/sh\necho 'not a compiler'\n")
        script.setExecutable(true)
        try {
            assertNull("Output without 'Crystal' must not validate", CrystalSdkDetector.validate(script.absolutePath))
        } finally {
            script.delete()
        }
    }

}
