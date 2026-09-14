package io.github.unurgunite.crystal.run

/**
 * Shared command-line building helpers for run, spec and debug states.
 * Kept in one place so argument splitting and KEY=VALUE parsing behave
 * identically across `crystal run`, `crystal spec` and DAP launch arguments.
 */
object CrystalCommandLine {
    /**
     * Splits a free-form argument string on spaces, dropping blanks.
     * (Quoting is not supported — matches the settings editor contract.)
     */
    fun splitArgs(arguments: String): List<String> = arguments.split(" ").filter { it.isNotBlank() }

    /**
     * Parses KEY=VALUE lines into a map. Lines without `=` are ignored.
     * Keys and values are trimmed; `=` inside values is preserved.
     */
    fun parseEnvVars(environmentVariables: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (line in environmentVariables.split("\n")) {
            val parts = line.trim().split("=", limit = 2)
            if (parts.size == 2) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
    }
}
