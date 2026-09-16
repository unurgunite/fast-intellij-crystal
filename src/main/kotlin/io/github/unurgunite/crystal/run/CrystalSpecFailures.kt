package io.github.unurgunite.crystal.run

import io.github.unurgunite.crystal.run.CrystalTestEventsConverter.TestNode

/**
 * `Failures:` section parsing for spec output: `N) name` headers,
 * `Failure/Error:` messages and `# file:line` locations, flushed onto the
 * matching test nodes. Split out of `CrystalTestEventsConverter.Parser`
 * (which exceeded the function budget).
 */
internal class CrystalSpecFailures(
    private val roots: List<TestNode>,
) {
    private var currentFailureName: String? = null
    private var currentFailureMessage = StringBuilder()
    private var failureFileLocation: String? = null
    private var collectingFailureMessage = false

    /**
     * One line of the failures section. Returns true on the `Finished in`
     * terminator (caller finalizes and moves to SUMMARY).
     */
    fun parseFailureLine(line: String): Boolean {
        val trimmed = line.trimStart()

        if (trimmed.startsWith("Finished in")) {
            flushCurrentFailure()
            return true
        }

        val failureMatch = FAILURE_HEADER.find(line)
        if (failureMatch != null) {
            startFailure(failureMatch.groupValues[1].trim())
            return false
        }

        if (currentFailureName != null) {
            parseFailureDetail(line, trimmed)
        }
        return false
    }

    /** New `N) name` header: flushes the previous failure, resets the accumulator. */
    private fun startFailure(name: String) {
        flushCurrentFailure()
        currentFailureName = name
        currentFailureMessage.clear()
        failureFileLocation = null
        collectingFailureMessage = false
    }

    /** Message/location/content lines of the current failure. */
    private fun parseFailureDetail(
        line: String,
        trimmed: String,
    ) {
        if (trimmed.startsWith("Failure/Error:")) {
            collectingFailureMessage = true
            val msg = trimmed.removePrefix("Failure/Error:").trim()
            if (msg.isNotEmpty()) currentFailureMessage.appendLine(msg)
            return
        }

        val locationMatch = FAILURE_LOCATION.find(line)
        if (locationMatch != null) {
            failureFileLocation = locationMatch.groupValues[1]
            collectingFailureMessage = false
            return
        }

        if (collectingFailureMessage && trimmed.isNotEmpty()) {
            currentFailureMessage.appendLine(trimmed)
        }
    }

    fun flushCurrentFailure() {
        val name = currentFailureName ?: return
        currentFailureName = null

        CrystalSpecTree.findTestInTree(roots, name)?.let { test ->
            test.failed = true
            test.failureMessage = currentFailureMessage.toString().trim()
            test.failureDetails = if (failureFileLocation != null) "${CrystalTestLocator.PROTOCOL}://$failureFileLocation" else ""
        }
    }

    companion object {
        private val FAILURE_HEADER = Regex("""^\s*\d+\)\s+(.+)$""")
        private val FAILURE_LOCATION = Regex("""^\s*#\s+(.+:\d+)\s*$""")
    }
}
