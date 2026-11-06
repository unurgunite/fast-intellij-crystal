package io.github.unurgunite.crystal.tools

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.StdlibTestPaths
import java.io.File

/**
 * Parses every `.cr` file under a tree and reports files that produce
 * [PsiErrorElement]s. This is the regression metric for grammar edits: the unit
 * suite stays green while a broad rule quietly breaks real files, so a parser
 * change must be judged by this sweep.
 *
 * Root comes from `-Dgraph.root`, falling back to the installed Crystal stdlib.
 * Run it through the `sweepTree` Gradle task: bare `test` overwrites the `-D`
 * arguments with its own, so the tree you asked for is silently replaced by the
 * stdlib default and the numbers become incomparable.
 *
 * The task must not raise `grammar.kit.gpub.max.level`: with it the generated
 * parser produced different `PsiErrorElement`s for some files (84/86 vs 80/82
 * erroring/errors), so absolute counts were not comparable with `test`.
 *
 * Output: `<root>/../check_tree.txt`, rows of `relPath<TAB>line<TAB>error`.
 */
class CrystalTreeSweepTest : BasePlatformTestCase() {
    fun testSweepTree() {
        val rootProp =
            System
                .getProperty("graph.root")
                .orEmpty()
                .ifEmpty { System.getenv("GRAPH_ROOT").orEmpty() }
                .ifEmpty { StdlibTestPaths.STDLIB }
        val root = File(rootProp)
        val sources =
            root
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(".cr") }
                .sorted()
                .toList()

        val rows = StringBuilder()
        var erroring = 0
        var errors = 0
        sources.forEachIndexed { index, file ->
            // Document names must be unique per fixture; the tree may hold
            // same-named files in different shard directories.
            myFixture.configureByText("sweep$index.cr", file.readText())
            val text = myFixture.file.text
            val found = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java).toList()
            if (found.isEmpty()) return@forEachIndexed
            erroring++
            errors += found.size
            found.forEach { element ->
                rows
                    .append(file.relativeTo(root).invariantSeparatorsPath)
                    .append('\t')
                    .append(text.substring(0, element.textRange.startOffset).count { it == '\n' } + 1)
                    .append('\t')
                    .append(element.errorDescription.replace('\n', ' '))
                    .append('\n')
            }
        }

        java.io
            .File(System.getenv("ARGS_OUT") ?: "/tmp/args_none.txt")
            .writeText(
                java.lang.management.ManagementFactory
                    .getRuntimeMXBean()
                    .inputArguments
                    .sorted()
                    .joinToString("\n"),
            )
        File(root, "../check_tree.txt").writeText(rows.toString())
        System.err.println("SWEEP files=${sources.size} erroring=$erroring errors=$errors")
    }
}
