package io.github.unurgunite.crystal.run

import io.github.unurgunite.crystal.run.CrystalTestEventsConverter.TestNode
import org.w3c.dom.NodeList

/**
 * Tree operations over spec result trees: collecting tests by name, finding by
 * full name and applying JUnit timing. Shared by the converter, its `Parser`
 * and the test-only entry points. Split out of `CrystalTestEventsConverter`
 * (whose class and `Parser` each exceeded the function budget).
 */
internal object CrystalSpecTree {
    fun collectTestNodes(
        node: TestNode,
        queues: MutableMap<String, MutableList<TestNode.Test>>,
    ) {
        when (node) {
            is TestNode.Test -> {
                queues.getOrPut(node.fullName) { mutableListOf() }.add(node)
            }

            is TestNode.Suite -> {
                for (child in node.children) collectTestNodes(child, queues)
            }
        }
    }

    /** Queues of test nodes per full name (handles duplicate names). */
    fun testQueues(roots: List<TestNode>): MutableMap<String, MutableList<TestNode.Test>> {
        val queues = mutableMapOf<String, MutableList<TestNode.Test>>()
        for (root in roots) {
            collectTestNodes(root, queues)
        }
        return queues
    }

    fun findTestInTree(
        roots: List<TestNode>,
        fullName: String,
    ): TestNode.Test? {
        for (root in roots) {
            val found = findTestInNode(root, fullName)
            if (found != null) return found
        }
        return null
    }

    fun findTestInNode(
        node: TestNode,
        fullName: String,
    ): TestNode.Test? =
        when (node) {
            is TestNode.Test -> {
                if (node.fullName == fullName) node else null
            }

            is TestNode.Suite -> {
                findTestInSuite(node, fullName)
            }
        }

    /** First matching test in a suite's children, depth-first. */
    private fun findTestInSuite(
        suite: TestNode.Suite,
        fullName: String,
    ): TestNode.Test? {
        for (child in suite.children) {
            val found = findTestInNode(child, fullName)
            if (found != null) return found
        }
        return null
    }

    /** Applies per-`testcase` durations to the queued nodes (duplicate names in order). */
    fun applyTiming(
        testcases: NodeList,
        queues: MutableMap<String, MutableList<TestNode.Test>>,
    ) {
        for (i in 0 until testcases.length) {
            applyTestcaseTiming(testcases.item(i), queues)
        }
    }

    /** One `testcase` element: its `time` goes to the next queued node of that name. */
    private fun applyTestcaseTiming(
        tc: org.w3c.dom.Node,
        queues: MutableMap<String, MutableList<TestNode.Test>>,
    ) {
        val name = tc.attributes.getNamedItem("name")?.nodeValue ?: return
        val timeStr = tc.attributes.getNamedItem("time")?.nodeValue ?: return
        val timeSeconds = timeStr.toDoubleOrNull() ?: return
        val durationMs = (timeSeconds * 1000).toLong()

        val queue = queues[name]
        if (queue != null && queue.isNotEmpty()) {
            queue.removeFirst().durationMs = durationMs
        }
    }
}

/**
 * File/line location queues for spec tests: consumes `TestLocation`s in order
 * so duplicate test names map to successive locations.
 */
internal object CrystalSpecLocations {
    fun consumeNextLocation(
        locationQueues: MutableMap<String, MutableList<CrystalSpecFileIndexer.TestLocation>>,
        testLocations: Map<String, List<CrystalSpecFileIndexer.TestLocation>>,
        fullName: String,
    ): CrystalSpecFileIndexer.TestLocation? {
        val queue =
            locationQueues.getOrPut(fullName) {
                (testLocations[fullName] ?: emptyList()).toMutableList()
            }
        return if (queue.isNotEmpty()) queue.removeFirst() else null
    }
}
