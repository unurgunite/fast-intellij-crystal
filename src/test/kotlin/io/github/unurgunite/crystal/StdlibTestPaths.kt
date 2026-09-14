package io.github.unurgunite.crystal

import java.io.File

/**
 * Resolves the Crystal stdlib `src/` directory for tests that walk the real
 * stdlib on disk (parse audits, reference-graph harness).
 *
 * Resolution order:
 * 1. `CRYSTAL_STDLIB_SRC` environment variable (explicit override, e.g. CI).
 * 2. `crystal env CRYSTAL_PATH` from `PATH` (tracks whatever toolchain is
 *    installed — hardcoded Cellar paths rot on every `brew upgrade`).
 * 3. Legacy hardcoded Homebrew path (last resort, keeps old checkouts working).
 */
object StdlibTestPaths {
    private const val LEGACY = "/opt/homebrew/Cellar/crystal/1.20.3/share/crystal/src"

    val STDLIB: String by lazy { resolve() }

    private fun resolve(): String {
        System.getenv("CRYSTAL_STDLIB_SRC")?.takeIf { File(it).isDirectory }?.let { return it }
        runCrystalEnv()?.let { return it }
        return LEGACY
    }

    private fun runCrystalEnv(): String? {
        return try {
            val process =
                ProcessBuilder("crystal", "env", "CRYSTAL_PATH")
                    .redirectErrorStream(true)
                    .start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (process.waitFor() != 0 || output.isBlank()) return null
            // CRYSTAL_PATH is colon-separated ("lib:/.../share/crystal"); the
            // absolute entry is the stdlib root, its src/ holds the .cr files.
            val entry = output.split(":").firstOrNull { it.startsWith("/") } ?: return null
            val src = File(File(entry), "src")
            (if (src.isDirectory) src else File(entry)).takeIf { it.isDirectory }?.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
