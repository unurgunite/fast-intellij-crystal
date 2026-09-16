package io.github.unurgunite.crystal.run

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Crystal spec verbose output (-v --no-color) into SMTRunner test events.
 *
 * Two-pass architecture:
 * Pass 1: Parse verbose output into an in-memory tree (suites + tests + failures)
 * Pass 2: Parse JUnit XML (from --junit_output) for per-test timing, then emit events
 *
 * This ensures suites know about child failures before they're closed,
 * and each test has accurate execution time from Crystal's own measurement.
 */
class CrystalTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties,
    private val testLocations: Map<String, List<CrystalSpecFileIndexer.TestLocation>> = emptyMap(),
    private val junitOutputFile: File? = null,
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    private val log = Logger.getInstance(CrystalTestEventsConverter::class.java)

    private val parser =
        Parser(testLocations, onParsingComplete = {
            parsingComplete = true
        })

    @Volatile
    private var parsingComplete = false

    @Volatile
    private var eventsEmitted = false

    override fun processServiceMessages(
        text: String,
        outputType: Key<*>,
        visitor: ServiceMessageVisitor,
    ): Boolean {
        if (outputType != ProcessOutputTypes.STDOUT) {
            return false
        }
        parser.feedText(text)
        return true
    }

    override fun flushBufferOnProcessTermination(exitCode: Int) {
        super.flushBufferOnProcessTermination(exitCode)
        parser.finish()
        if (!eventsEmitted && parsingComplete) {
            applyJUnitTiming()
            emitEvents()
            eventsEmitted = true
        }
    }

    override fun dispose() {
        cleanupJUnitFile()
        super.dispose()
    }

    // ==================== JUnit XML Timing ====================

    private fun applyJUnitTiming() {
        val xmlFile = junitOutputFile ?: return
        if (!xmlFile.exists() || xmlFile.length() == 0L) return

        try {
            parseJUnitXml(xmlFile)
        } catch (e: javax.xml.parsers.ParserConfigurationException) {
            log.warn("Failed to parse JUnit XML: ${xmlFile.path}", e)
        } catch (e: org.xml.sax.SAXException) {
            log.warn("Failed to parse JUnit XML: ${xmlFile.path}", e)
        } catch (e: java.io.IOException) {
            log.warn("Failed to parse JUnit XML: ${xmlFile.path}", e)
        }
    }

    private fun parseJUnitXml(xmlFile: File) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val testcases = doc.getElementsByTagName("testcase")

        // Build a queue of test nodes per fullName to handle duplicate names
        val queues = CrystalSpecTree.testQueues(parser.rootChildren)
        CrystalSpecTree.applyTiming(testcases, queues)
    }

    private fun cleanupJUnitFile() {
        val file = junitOutputFile ?: return
        if (!file.delete() && file.exists()) {
            log.warn("Failed to clean up JUnit output file: ${file.path}")
        }
    }

    // ==================== Pass 2: Emitting ====================

    private fun emitEvents() {
        for (node in parser.rootChildren) {
            emitNode(node)
        }
    }

    private fun emitNode(node: TestNode) {
        when (node) {
            is TestNode.Suite -> emitSuite(node)
            is TestNode.Test -> emitTest(node)
        }
    }

    private fun emitSuite(suite: TestNode.Suite) {
        getProcessor().onSuiteStarted(TestSuiteStartedEvent(suite.name, null))
        for (child in suite.children) {
            emitNode(child)
        }
        getProcessor().onSuiteFinished(TestSuiteFinishedEvent(suite.name))
    }

    private fun emitTest(test: TestNode.Test) {
        getProcessor().onTestStarted(TestStartedEvent(test.name, test.url))
        if (test.failed) {
            getProcessor().onTestFailure(
                TestFailedEvent(test.name, test.failureMessage, test.failureDetails, false, null, null),
            )
        }
        getProcessor().onTestFinished(TestFinishedEvent(test.name, test.durationMs))
    }

    // ==================== Tree Node Types ====================

    sealed class TestNode {
        abstract val name: String

        data class Suite(
            override val name: String,
            val children: MutableList<TestNode> = mutableListOf(),
        ) : TestNode()

        data class Test(
            override val name: String,
            val fullName: String,
            val url: String?,
            var failed: Boolean = false,
            var failureMessage: String = "",
            var failureDetails: String = "",
            var durationMs: Long = -1,
        ) : TestNode()
    }

    // ==================== Parsing (self-contained, testable) ====================

    class Parser(
        private val testLocations: Map<String, List<CrystalSpecFileIndexer.TestLocation>> = emptyMap(),
        private val onParsingComplete: (() -> Unit)? = null,
    ) {
        private enum class ParseState { RUNNING, FAILURES, SUMMARY }

        private var parseState = ParseState.RUNNING
        internal val rootChildren = mutableListOf<TestNode>()
        private val suiteStack = mutableListOf<TestNode.Suite>()
        private var pendingSuiteName: String? = null
        private var currentTestName: String? = null
        private var currentTestFullName: String? = null
        private var hasSeenTests = false
        private val failures = CrystalSpecFailures(rootChildren)
        private val lineBuffer = StringBuilder()

        // Mutable queues for consuming test locations in order (handles duplicate test names)
        private val locationQueues = mutableMapOf<String, MutableList<CrystalSpecFileIndexer.TestLocation>>()

        fun feedText(text: String) {
            lineBuffer.append(text)
            while (true) {
                val newlineIdx = lineBuffer.indexOf('\n')
                if (newlineIdx < 0) break
                val line = lineBuffer.substring(0, newlineIdx).trimEnd('\r')
                lineBuffer.delete(0, newlineIdx + 1)
                parseLine(line)
            }
        }

        fun finish() {
            if (lineBuffer.isNotEmpty()) {
                parseLine(lineBuffer.toString().trimEnd('\r', '\n'))
                lineBuffer.clear()
            }
            finalizeParsing()
        }

        private fun parseLine(line: String) {
            when (parseState) {
                ParseState.RUNNING -> {
                    parseRunningLine(line)
                }

                ParseState.FAILURES -> {
                    if (failures.parseFailureLine(line)) {
                        finalizeParsing()
                        parseState = ParseState.SUMMARY
                        onParsingComplete?.invoke()
                    }
                }

                ParseState.SUMMARY -> {}
            }
        }

        private fun parseRunningLine(line: String) {
            if (line.trimStart() == "Failures:") {
                parseState = ParseState.FAILURES
                return
            }

            if (line.trimStart().startsWith("Finished in")) {
                clearCurrentTest()
                finalizeParsing()
                parseState = ParseState.SUMMARY
                onParsingComplete?.invoke()
                return
            }

            if (line.isBlank()) return

            val stripped = line.trimStart()
            val indent = line.length - stripped.length

            if (isDuplicatedName(stripped)) {
                addTestLine(stripped)
            } else {
                addSuiteLine(stripped, indent)
            }
        }

        /** A duplicated suite+test line: confirms the pending suite, starts the test node. */
        private fun addTestLine(stripped: String) {
            confirmPendingSuite()
            val testName = stripped.substring(0, stripped.length / 2).trimEnd()
            clearCurrentTest()

            val fullName = (suiteStack.map { it.name } + testName).joinToString(" ")
            val location = consumeNextLocation(fullName)
            val url = if (location != null) "${CrystalTestLocator.PROTOCOL}://${location.file}:${location.line}" else null

            currentTestName = testName
            currentTestFullName = fullName
            hasSeenTests = true

            currentContainer().add(TestNode.Test(testName, fullName, url))
        }

        /** A suite line: adjusts the stack to the indent level, pushes the suite. */
        private fun addSuiteLine(
            stripped: String,
            indent: Int,
        ) {
            val level = indent / 2

            if (!hasSeenTests && indent == 0) {
                pendingSuiteName = stripped
                return
            }

            confirmPendingSuite()

            while (suiteStack.size > level) {
                suiteStack.removeAt(suiteStack.size - 1)
            }

            val container = currentContainer()
            val suiteNode = TestNode.Suite(stripped)
            suiteStack.add(suiteNode)
            container.add(suiteNode)
        }

        /** Children of the innermost open suite, or the roots when no suite is open. */
        private fun currentContainer(): MutableList<TestNode> =
            if (suiteStack.isNotEmpty()) {
                suiteStack.last().children
            } else {
                rootChildren
            }

        /** Clears the in-progress test (finished or superseded). */
        private fun clearCurrentTest() {
            currentTestName = null
            currentTestFullName = null
        }

        private fun confirmPendingSuite() {
            val pending = pendingSuiteName ?: return
            pendingSuiteName = null

            while (suiteStack.size > 0) {
                suiteStack.removeAt(suiteStack.size - 1)
            }

            val suiteNode = TestNode.Suite(pending)
            suiteStack.add(suiteNode)
            rootChildren.add(suiteNode)
        }

        private fun consumeNextLocation(fullName: String): CrystalSpecFileIndexer.TestLocation? =
            CrystalSpecLocations.consumeNextLocation(locationQueues, testLocations, fullName)

        private fun finalizeParsing() {
            if (pendingSuiteName != null) {
                confirmPendingSuite()
            }
        }
    }

    companion object {
        // A repeated suite/test name is only treated as a rerun-duplicate when it is
        // long enough to be a real name rather than a coincidence ("ab  ab").
        private const val MIN_DUPLICATED_NAME_LENGTH = 3

        fun isDuplicatedName(stripped: String): Boolean {
            if (stripped.length < MIN_DUPLICATED_NAME_LENGTH) return false
            val splitRegex = Regex("""^(.+?)\s{2,}\1$""")
            return splitRegex.matches(stripped)
        }

        /**
         * Parse Crystal verbose spec output into a tree structure for testing.
         * Does not require TestConsoleProperties or framework dependencies.
         */
        fun parseForTest(
            output: String,
            testLocations: Map<String, List<CrystalSpecFileIndexer.TestLocation>> = emptyMap(),
        ): List<TestNode> {
            val parser = Parser(testLocations)
            parser.feedText(output)
            parser.finish()
            return parser.rootChildren
        }

        /**
         * Apply per-test timing from a JUnit XML file to an existing test tree.
         * Used for testing JUnit XML parsing independently of the full converter.
         */
        fun applyJUnitTimingFromXml(
            xmlFile: File,
            tree: List<TestNode>,
        ) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
            val testcases = doc.getElementsByTagName("testcase")

            // Build a queue of test nodes per fullName to handle duplicate names
            val queues = CrystalSpecTree.testQueues(tree)
            CrystalSpecTree.applyTiming(testcases, queues)
        }
    }
}
