package io.github.unurgunite.crystal.tools

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.psi.references.CrystalReference
import io.github.unurgunite.crystal.psi.references.SymbolLoc
import java.io.BufferedWriter
import java.io.FileWriter
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

class StdlibGraphToolTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(testRootDisposable, STDLIB)
        System.setProperty("grammar.kit.gpub.max.level", "6000")
    }

    fun testAggregateParseErrors() {
        val root = LocalFileSystem.getInstance().findFileByPath(STDLIB)!!
        val outDir = java.io.File(workspaceRoot(), "stdlib-graph").also { it.mkdirs() }
        val tsv = java.io.File(outDir, "parse_errors.tsv")
        tsv.writeText("relPath\terrors\tfirstError\tline\n")
        val rows = collectParseErrorRows(root, System.currentTimeMillis() + 28 * 60_000L)
        for (r in rows) {
            tsv.appendText("${r.relPath}\t${r.errors}\t${r.firstError.replace("\t", " ")}\t${r.line.replace("\t", " ")}\n")
        }
        val total = rows.size
        val withErrors = rows.count { it.errors > 0 }
        val totalErrors = rows.sumOf { it.errors }
        val top =
            rows
                .filter { it.errors > 0 }
                .sortedByDescending { it.errors }
        val sb = StringBuilder()
        sb.append("TOTAL_FILES=$total FILES_WITH_ERRORS=$withErrors TOTAL_ERRORS=$totalErrors\n")
        sb.append("TOP_FILES_BY_ERRORS:\n")
        top.take(80).forEach { sb.append("  ${it.relPath} x${it.errors}\n") }
        java.io.File(outDir, "parse_errors_summary.txt").writeText(sb.toString())
        println("PARSE_ERROR_AGG: " + sb.toString().replace("\n", " | "))
    }

    /** One row of the parse-error aggregate: per-file PSI error count + first-error sample. */
    private data class ParseErrRow(
        val relPath: String,
        val errors: Int,
        val firstError: String,
        val line: String,
    )

    /**
     * Whole-stdlib PSI parse-error walk. Shared by [testAggregateParseErrors] (which
     * persists the TSV) and [testBuildStructureJson] (which needs live counts).
     * Each test computes its own rows so results never depend on JUnit method
     * order or a stale `parse_errors.tsv` from a previous run.
     */
    private fun collectParseErrorRows(
        root: VirtualFile,
        deadlineMs: Long,
    ): List<ParseErrRow> {
        val rows = ArrayList<ParseErrRow>()
        VfsUtilCore.visitChildrenRecursively(
            root,
            object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory || file.extension != "cr") return true
                    if (System.currentTimeMillis() > deadlineMs) return false
                    val relPath = VfsUtilCore.getRelativePath(file, root) ?: return true
                    val raw =
                        try {
                            String(file.contentsToByteArray(), Charsets.UTF_8)
                        } catch (_: Throwable) {
                            ""
                        }
                    val (errs, desc, line) =
                        ReadAction.compute<Triple<Int, String, String>, Throwable> {
                            val psi = PsiManager.getInstance(project).findFile(file) ?: return@compute Triple(0, "", "")
                            var n = 0
                            var firstDesc = ""
                            var firstOffset = -1
                            psi.accept(
                                object : PsiRecursiveElementVisitor() {
                                    override fun visitErrorElement(e: PsiErrorElement) {
                                        n++
                                        if (firstDesc.isEmpty()) {
                                            firstDesc = e.errorDescription
                                            firstOffset = e.textRange.startOffset
                                        }
                                        super.visitErrorElement(e)
                                    }
                                },
                            )
                            val srcLine = if (firstOffset >= 0) lineAt(raw, firstOffset) else ""
                            Triple(n, firstDesc, srcLine)
                        }
                    synchronized(rows) {
                        rows.add(ParseErrRow(relPath, errs, desc, line))
                    }
                    return true
                }
            },
        )
        return rows.sortedBy { it.relPath }
    }

    fun testBuildGraph() {
        val root = LocalFileSystem.getInstance().findFileByPath(STDLIB)!!
        val table = CrystalReference.getStdlibSymbolTable(project)
        val deadlineMs = System.currentTimeMillis() + 5 * 60_000L
        val start = System.currentTimeMillis()
        val outDir = java.io.File(workspaceRoot(), "stdlib-graph").also { it.mkdirs() }
        val jsonl = java.io.File(outDir, "stdlib_graph.jsonl").absolutePath

        val scan = GraphScan(root, table, deadlineMs)
        BufferedWriter(FileWriter(jsonl)).use { out ->
            scan.out = out
            VfsUtilCore.visitChildrenRecursively(root, scan.visitor())
        }

        val elapsedSec = (System.currentTimeMillis() - start) / 1000
        val resolvedPct = if (scan.totalRefs.get() > 0) 100 * scan.totalResolved.get() / scan.totalRefs.get() else 0
        val actionablePct = if (scan.totalRefs.get() > 0) 100 * scan.totalUnresolved.get() / scan.totalRefs.get() else 0

        val sb = StringBuilder()
        sb.append("FILES_PROCESSED=${scan.filesProcessed}\n")
        sb.append("ELAPSED_SEC=$elapsedSec\n")
        sb.append("SYMBOL_TABLE_SIZE=${table.size}\n")
        sb.append(
            "TOTAL_REFS=${scan.totalRefs.get()} RESOLVED=${scan.totalResolved.get()} ($resolvedPct%) " +
                "UNRESOLVED(actionable)=${scan.totalUnresolved.get()} ($actionablePct%) " +
                "NA(noise)=${scan.totalNa.get()}\n",
        )
        appendTopEntries(sb, "TOP_ACTIONABLE_UNRESOLVED_TOKENS", scan.unresolvedByName, 40)
        appendTopEntries(sb, "TOP_ACTIONABLE_UNRESOLVED_FILES", scan.unresolvedByFile, 40)
        appendTopEntries(sb, "TOP_NA_TOKENS", scan.naByName, 20)
        sb.append("JSONL=$jsonl\n")
        sb.append("HTML=${java.io.File(outDir, "index.html").absolutePath}\n")
        java.io.File(outDir, "stdlib_graph_summary.txt").writeText(sb.toString())
        println("StdlibReferenceGraph: " + sb.toString().replace("\n", " | "))

        GraphHtml.build(
            outDir,
            Stats(
                scan.filesProcessed,
                elapsedSec,
                table.size,
                scan.totalRefs.get(),
                scan.totalResolved.get(),
                scan.totalUnresolved.get(),
                scan.totalNa.get(),
                resolvedPct.toInt(),
                actionablePct.toInt(),
            ),
            scan.unresolvedByName,
            scan.unresolvedByFile,
            scan.naByName,
            scan.unresolvedRows,
            scan.naRows,
            scan.resolvedRows,
        )
    }

    /** Top-N counter lines (`  name xcount`) under a header. */
    private fun appendTopEntries(
        sb: StringBuilder,
        header: String,
        counters: Map<String, AtomicLong>,
        limit: Int,
    ) {
        sb.append("$header:\n")
        counters.entries.sortedByDescending { it.value.get() }.take(limit).forEach {
            sb.append("  ${it.key} x${it.value.get()}\n")
        }
    }

    /** Mutable counters/rows for one whole-stdlib graph walk. */
    private inner class GraphScan(
        private val root: VirtualFile,
        private val table: Map<String, SymbolLoc>,
        private val deadlineMs: Long,
    ) {
        var out: BufferedWriter? = null
        val totalRefs = AtomicLong(0)
        val totalResolved = AtomicLong(0)
        val totalUnresolved = AtomicLong(0)
        val totalNa = AtomicLong(0)
        val unresolvedByName = HashMap<String, AtomicLong>()
        val unresolvedByFile = HashMap<String, AtomicLong>()
        val naByName = HashMap<String, AtomicLong>()
        val unresolvedRows = ArrayList<Array<String?>>()
        val naRows = ArrayList<Array<String?>>()
        val resolvedRows = ArrayList<Array<String?>>()
        var filesProcessed = 0

        fun visitor(): VirtualFileVisitor<Any> =
            object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory || file.extension != "cr") return true
                    if (System.currentTimeMillis() > deadlineMs) return false
                    val relPath = VfsUtilCore.getRelativePath(file, root) ?: return true
                    val text = readStdlibFileText(file) ?: return true
                    recordFile(relPath, text)
                    filesProcessed++
                    return true
                }
            }

        private fun recordFile(
            relPath: String,
            text: String,
        ) {
            val isNaFile = isGeneratedFile(relPath)
            for (call in scanFileCalls(text)) {
                recordCall(relPath, call, isNaFile)
            }
        }

        private fun recordCall(
            relPath: String,
            call: CallRef,
            isNaFile: Boolean,
        ) {
            totalRefs.incrementAndGet()
            val resolvedRelPath = resolve(table, call)
            if (resolvedRelPath != null) {
                totalResolved.incrementAndGet()
                resolvedRows.add(arrayOf(relPath, call.token, call.recvClass, resolvedRelPath))
                out!!.write("""{"src":"$relPath","tok":"${call.token}","def":"$resolvedRelPath"}""")
            } else if (isNaFile || isNoise(call.token)) {
                totalNa.incrementAndGet()
                naByName.computeIfAbsent(call.token) { AtomicLong(0) }.incrementAndGet()
                naRows.add(arrayOf(relPath, call.token))
                out!!.write("""{"src":"$relPath","tok":"${call.token}","def":"NA"}""")
            } else {
                totalUnresolved.incrementAndGet()
                unresolvedByName.computeIfAbsent(call.token) { AtomicLong(0) }.incrementAndGet()
                unresolvedByFile.computeIfAbsent(relPath) { AtomicLong(0) }.incrementAndGet()
                unresolvedRows.add(arrayOf(relPath, call.token, call.recvClass))
                out!!.write("""{"src":"$relPath","tok":"${call.token}","def":"UNRESOLVED"}""")
            }
            out!!.newLine()
        }
    }

    fun testBuildStructureJson() {
        val root = LocalFileSystem.getInstance().findFileByPath(STDLIB)!!
        val table = CrystalReference.getStdlibSymbolTable(project)
        val outDir = java.io.File(workspaceRoot(), "stdlib-graph").also { it.mkdirs() }

        val parseErrs =
            collectParseErrorRows(root, System.currentTimeMillis() + 28 * 60_000L)
                .associate { it.relPath to it.errors }
        val deadline = System.currentTimeMillis() + 28 * 60_000L

        val scan: StructureScan
        BufferedWriter(FileWriter(java.io.File(outDir, "structure.json"))).use { out ->
            out.write(
                "{\n  \"meta\": { \"generator\": \"Crystal StdlibGraphToolTest.testBuildStructureJson\", \"stdlib\": \"$STDLIB\" },\n",
            )
            out.write("  \"files\": {\n")
            scan = StructureScan(root, table, parseErrs, out, deadline)
            VfsUtilCore.visitChildrenRecursively(root, scan.visitor())
            out.write("\n  },\n")
            out.write(scan.summaryJson())
        }

        val sb = StringBuilder()
        sb.append(
            "STRUCTURE_JSON files=${scan.filesCount} types=${scan.totalTypes} " +
                "methods=${scan.totalMethods} calls=${scan.totalCalls}\n",
        )
        sb.append("CALLS resolved=${scan.callsResolved} unresolved=${scan.callsUnresolved} na=${scan.callsNa}\n")
        sb.append("FILES_WITH_PARSE_ERRORS=${scan.filesWithParseErrors}\n")
        java.io.File(outDir, "structure_summary.txt").writeText(sb.toString())
        println("StdlibStructure: " + sb.toString().replace("\n", " | "))
    }

    /** Mutable counters + JSON emission for one whole-stdlib structure walk. */
    private inner class StructureScan(
        private val root: VirtualFile,
        private val table: Map<String, SymbolLoc>,
        private val parseErrs: Map<String, Int>,
        private val out: BufferedWriter,
        private val deadline: Long,
    ) {
        var filesCount = 0
        var totalTypes = 0
        var totalMethods = 0
        var totalCalls = 0
        var callsResolved = 0
        var callsUnresolved = 0
        var callsNa = 0
        var filesWithParseErrors = 0
        private var firstFile = true

        fun visitor(): VirtualFileVisitor<Any> =
            object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory || file.extension != "cr") return true
                    if (System.currentTimeMillis() > deadline) return false
                    val relPath = VfsUtilCore.getRelativePath(file, root) ?: return true
                    val text = readStdlibFileText(file) ?: return true
                    emitFile(relPath, text)
                    return true
                }
            }

        private fun emitFile(
            relPath: String,
            text: String,
        ) {
            val (fileTypes, fileMethods, fileCalls) = scanStructure(text)
            if (!firstFile) out.write(",\n")
            firstFile = false
            out.write("    ${je(relPath)}: {")
            out.write("\"parse_errors\":${parseErrs[relPath] ?: 0},")
            out.write("\"types\":[${typesJson(fileTypes)}],")
            out.write("\"methods\":[${methodsJson(fileMethods)}],")
            out.write("\"calls\":[${callsJson(fileCalls, isGeneratedFile(relPath))}]}")
            totalTypes += fileTypes.size
            totalMethods += fileMethods.size
            if ((parseErrs[relPath] ?: 0) > 0) filesWithParseErrors++
            filesCount++
        }

        private fun typesJson(fileTypes: List<StructType>): String =
            fileTypes.joinToString(",") { t ->
                "{\"kind\":${je(t.kind)},\"name\":${je(t.name)},\"qualified\":${je(t.qualified)},\"line\":${t.line}}"
            }

        private fun methodsJson(fileMethods: List<StructMethod>): String =
            fileMethods.joinToString(",") { m ->
                "{\"name\":${je(m.name)},\"owner\":${if (m.owner == null) "null" else je(m.owner)}," +
                    "\"kind\":${je(m.kind)},\"line\":${m.line}}"
            }

        private fun callsJson(
            fileCalls: List<StructCall>,
            isNaFile: Boolean,
        ): String =
            fileCalls.joinToString(",") { c ->
                val resolved = resolve(table, c.ref)
                val status = classifyStructureCall(c.ref, resolved, isNaFile)
                totalCalls++
                "{\"token\":${je(c.ref.token)},\"kind\":${je(c.ref.kind)}," +
                    "\"recv\":${if (c.ref.recvClass == null) "null" else je(c.ref.recvClass)}," +
                    "\"resolved\":${if (resolved == null) "null" else je(resolved)},\"status\":${je(status)}}"
            }

        /** Resolution status of one structure call, counting the verdict. */
        private fun classifyStructureCall(
            ref: CallRef,
            resolved: String?,
            isNaFile: Boolean,
        ): String =
            if (resolved != null) {
                callsResolved++
                "resolved"
            } else if (isNaFile || isNoise(ref.token)) {
                callsNa++
                "na"
            } else {
                callsUnresolved++
                "unresolved"
            }

        fun summaryJson(): String =
            "  \"summary\": {\n" +
                "    \"files\": $filesCount,\n" +
                "    \"files_with_parse_errors\": $filesWithParseErrors,\n" +
                "    \"total_types\": $totalTypes,\n" +
                "    \"total_methods\": $totalMethods,\n" +
                "    \"total_calls\": $totalCalls,\n" +
                "    \"calls_resolved\": $callsResolved,\n" +
                "    \"calls_unresolved\": $callsUnresolved,\n" +
                "    \"calls_na\": $callsNa\n" +
                "  }\n}\n"
    }

    fun testCheckSingleFile() {
        val relPath = System.getProperty("graph.file")
        if (relPath.isNullOrBlank()) {
            return
        }
        val root = LocalFileSystem.getInstance().findFileByPath(STDLIB)!!
        val file =
            VfsUtilCore.findRelativeFile(relPath, root)
                ?: error("File not found under stdlib: $relPath")
        val text = String(file.contentsToByteArray(), Charsets.UTF_8)
        val table = CrystalReference.getStdlibSymbolTable(project)

        val psi =
            ReadAction.compute<PsiFile, Throwable> {
                PsiManager.getInstance(project).findFile(file)!!
            }
        var parseErrors = 0
        val errorSamples = ArrayList<String>()
        psi.accept(
            object : PsiRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    parseErrors++
                    if (errorSamples.size < 20) {
                        val ctx = element.text.take(40).replace("\n", "\\n")
                        errorSamples.add("@${element.textRange.startOffset}: ${element.errorDescription} ['$ctx']")
                    }
                    super.visitErrorElement(element)
                }
            },
        )

        var resolved = 0
        var unresolved = 0
        var na = 0
        val unresolvedTokens = HashMap<String, Int>()
        val isNaFile = isGeneratedFile(relPath)
        for (call in scanFileCalls(text)) {
            val r = resolve(table, call)
            when {
                r != null -> {
                    resolved++
                }

                isNaFile || isNoise(call.token) -> {
                    na++
                }

                else -> {
                    unresolved++
                    unresolvedTokens[call.token] = (unresolvedTokens[call.token] ?: 0) + 1
                }
            }
        }
        val sb = StringBuilder()
        sb.append("CHECK_FILE=$relPath\n")
        sb.append("PARSE_ERRORS=$parseErrors\n")
        errorSamples.forEach { sb.append("  ERR $it\n") }
        sb.append("CALLS resolved=$resolved unresolved=$unresolved NA=$na (total=${resolved + unresolved + na})\n")
        unresolvedTokens.entries.sortedByDescending { it.value }.take(30).forEach {
            sb.append("  UNRESOLVED ${it.key} x${it.value}\n")
        }
        java.io.File(workspaceRoot(), "stdlib-graph/check_${relPath.replace('/', '_')}.txt").writeText(sb.toString())
        println("StdlibFileCheck: " + sb.toString().replace("\n", " | "))
    }

    // -- helpers --

    private fun lineAt(
        text: String,
        offset: Int,
    ): String {
        if (offset < 0 || offset >= text.length) return ""
        var s = offset
        while (s > 0 && text[s - 1] != '\n') s--
        var e = offset
        while (e < text.length && text[e] != '\n') e++
        return text.substring(s, e).trim().take(120)
    }

    private data class Stats(
        val files: Int,
        val elapsedSec: Long,
        val tableSize: Int,
        val total: Long,
        val resolved: Long,
        val unresolved: Long,
        val na: Long,
        val resolvedPct: Int,
        val actionablePct: Int,
    )

    private fun workspaceRoot(): String = java.io.File("").canonicalPath

    /** HTML report for the stdlib reference graph (rows/tops JSON + document). */
    private object GraphHtml {
        fun build(
            outDir: java.io.File,
            stats: Stats,
            unresolvedByName: Map<String, AtomicLong>,
            unresolvedByFile: Map<String, AtomicLong>,
            naByName: Map<String, AtomicLong>,
            unresolvedRows: List<Array<String?>>,
            naRows: List<Array<String?>>,
            resolvedRows: List<Array<String?>>,
        ) {
            val (unresJson, naJson, resJson) = rowsJson(unresolvedRows, naRows, resolvedRows)
            val (topTok, topFile, naTok) = topsJson(unresolvedByName, unresolvedByFile, naByName)
            val html = document(stats, unresJson, naJson, resJson, topTok, topFile, naTok)
            java.io.File(outDir, "index.html").writeText(html.replace("@@{", "\${"))
        }

        private fun rowsJson(
            unresolvedRows: List<Array<String?>>,
            naRows: List<Array<String?>>,
            resolvedRows: List<Array<String?>>,
        ): Triple<String, String, String> {
            val unresJson =
                unresolvedRows.joinToString(",") { r ->
                    "[${je(r[0])},${je(r[1])},${if (r[2] == null) "null" else je(r[2])}]"
                }
            val naJson = naRows.joinToString(",") { r -> "[${je(r[0])},${je(r[1])}]" }
            val resJson =
                resolvedRows.joinToString(",") { r ->
                    "[${je(r[0])},${je(r[1])},${if (r[2] == null) "null" else je(r[2])},${je(r[3])}]"
                }
            return Triple(unresJson, naJson, resJson)
        }

        private fun topsJson(
            unresolvedByName: Map<String, AtomicLong>,
            unresolvedByFile: Map<String, AtomicLong>,
            naByName: Map<String, AtomicLong>,
        ): Triple<String, String, String> {
            val topTok = topJson(unresolvedByName, 40)
            val topFile = topJson(unresolvedByFile, 40)
            val naTok = topJson(naByName, 20)
            return Triple(topTok, topFile, naTok)
        }

        private fun topJson(
            counters: Map<String, AtomicLong>,
            limit: Int,
        ): String =
            counters.entries
                .sortedByDescending { it.value.get() }
                .take(limit)
                .joinToString(",") { "[${je(it.key)},${it.value.get()}]" }

        private fun document(
            stats: Stats,
            unresJson: String,
            naJson: String,
            resJson: String,
            topTok: String,
            topFile: String,
            naTok: String,
        ): String =
            """
<!DOCTYPE html><html lang="en" data-theme="light" data-density="comfortable"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>Crystal Stdlib Reference Graph</title>
<style>
$GRAPH_CSS
</style></head>
<body>
${headerHtml(stats)}
<div class="wrap">
${statsHtml(stats)}
${tabsHtml()}
${overviewTabHtml()}
${rankTabHtml("tokens", "All unresolved tokens", "filter tokens…", "Filter tokens", "qTok", "countTok", "tokWrap")}
${rankTabHtml("files", "All unresolved files", "filter files…", "Filter files", "qFile", "countFile", "fileWrap")}
${callsTabHtml()}
  <footer>Generated by <code>StdlibGraphToolTest.testBuildGraph</code> · ${stats.total} refs · ${stats.tableSize} symbols</footer>
</div>
<script>
const UNRESOLVED = [$unresJson];
const NA = [$naJson];
const RESOLVED = [$resJson];
const TOP_TOK = [$topTok];
const TOP_FILE = [$topFile];
const NA_TOK = [$naTok];
$GRAPH_SCRIPT
</script></body></html>
            """.trimIndent()

        private fun headerHtml(stats: Stats): String =
            """
<header class="top">
  <div class="brand">
    <span class="gem" aria-hidden="true"></span>
    <div>
      <h1>Crystal Stdlib Reference Graph</h1>
      <div class="sub">Whole-stdlib dot-call / bare-call resolution — text-scan harness · ${stats.files} files · ${stats.elapsedSec}s scan</div>
    </div>
  </div>
  <div class="toggles" role="group" aria-label="Display options">
    <div class="seg" id="themeSeg" role="group" aria-label="Theme">
      <button type="button" data-theme-val="light" class="on" aria-pressed="true">Light</button>
      <button type="button" data-theme-val="dark" aria-pressed="false">Dark</button>
    </div>
    <div class="seg" id="densitySeg" role="group" aria-label="Density">
      <button type="button" data-density-val="comfortable" class="on" aria-pressed="true">Comfy</button>
      <button type="button" data-density-val="compact" aria-pressed="false">Dense</button>
    </div>
  </div>
</header>
            """.trimIndent()

        private fun statsHtml(stats: Stats): String =
            """
            <section class="stats" aria-label="Summary">
              <div class="donut-card panel">
                <div class="donut" style="background:conic-gradient(var(--green) 0 ${stats.resolvedPct}%, var(--red) ${stats.resolvedPct}% ${stats.resolvedPct + stats.actionablePct}%, var(--na) ${stats.resolvedPct + stats.actionablePct}% 100%)" role="img" aria-label="${stats.resolvedPct}% resolved, ${stats.actionablePct}% unresolved"></div>
                <div class="donut-legend">
                  <div><i class="dot g"></i>resolved ${stats.resolvedPct}%</div>
                  <div><i class="dot r"></i>unresolved ${stats.actionablePct}%</div>
                  <div><i class="dot n"></i>NA noise</div>
                </div>
              </div>
              <div class="card"><div class="n">${stats.files}</div><div class="l">files</div></div>
              <div class="card"><div class="n">${stats.tableSize}</div><div class="l">symbol table</div></div>
              <div class="card green"><div class="n">${stats.resolvedPct}%</div><div class="l">resolved · ${stats.resolved}</div></div>
              <div class="card red"><div class="n">${stats.actionablePct}%</div><div class="l">unresolved · ${stats.unresolved}</div></div>
              <div class="card gray"><div class="n">${stats.na}</div><div class="l">NA (noise)</div></div>
            </section>
            """.trimIndent()

        private fun tabsHtml(): String =
            """
            <nav class="tabs" role="tablist" aria-label="Report sections">
              <button type="button" role="tab" aria-selected="true" data-tab="overview" class="on">Overview</button>
              <button type="button" role="tab" aria-selected="false" data-tab="tokens">Tokens <span class="badge" id="badgeTok"></span></button>
              <button type="button" role="tab" aria-selected="false" data-tab="files">Files <span class="badge" id="badgeFile"></span></button>
              <button type="button" role="tab" aria-selected="false" data-tab="calls">Calls <span class="badge" id="badgeCalls"></span></button>
            </nav>
            """.trimIndent()

        private fun overviewTabHtml(): String =
            """
            <section id="tab-overview" role="tabpanel">
              <div class="grid2">
                <div class="panel"><h2>Top unresolved tokens <span class="hint-inline">click a row to filter calls</span></h2><div class="bars" id="topTok"></div></div>
                <div class="panel"><h2>Top unresolved files <span class="hint-inline">click a row to filter calls</span></h2><div class="bars" id="topFile"></div></div>
              </div>
              <div class="panel"><h2>Top NA tokens (noise)</h2><div class="bars" id="naTok"></div></div>
            </section>
            """.trimIndent()

        private fun rankTabHtml(
            tab: String,
            title: String,
            placeholder: String,
            ariaLabel: String,
            inputId: String,
            countId: String,
            wrapId: String,
        ): String =
            """
            <section id="tab-$tab" role="tabpanel" hidden>
              <div class="panel">
                <h2>$title</h2>
                <div class="controls">
                  <input type="text" id="$inputId" placeholder="$placeholder" aria-label="$ariaLabel" autocomplete="off">
                  <span id="$countId" class="count"></span>
                </div>
                <div id="$wrapId" class="scroll"></div>
              </div>
            </section>
            """.trimIndent()

        private fun callsTabHtml(): String =
            """
            <section id="tab-calls" role="tabpanel" hidden>
              <div class="panel">
                <h2>Calls</h2>
                <div class="controls">
                  <input type="text" id="q" placeholder="filter by token, receiver class, or file…" aria-label="Filter calls" autocomplete="off">
                  <div class="seg cats" role="radiogroup" aria-label="Category">
                    <label class="on"><input type="radio" name="cat" value="all" checked> all</label>
                    <label><input type="radio" name="cat" value="U"> unresolved</label>
                    <label><input type="radio" name="cat" value="N"> NA</label>
                    <label><input type="radio" name="cat" value="R"> resolved</label>
                  </div>
                  <button type="button" id="clearFilters" class="ghost" hidden>clear ×</button>
                </div>
                <div id="activeFilter" class="active-filter" hidden></div>
                <div id="tblWrap" class="scroll"></div>
                <div class="pager">
                  <button type="button" id="prev">← prev</button>
                  <span id="pageInfo" class="count"></span>
                  <button type="button" id="next">next →</button>
                  <select id="perPage" aria-label="Rows per page">
                    <option value="100">100 / page</option>
                    <option value="200" selected>200 / page</option>
                    <option value="500">500 / page</option>
                  </select>
                </div>
                <div class="hint">receiver = attempted class for dot/bare calls (null = top-level / unknown). Click a column header to sort. Click a token or file to pin it as a filter.</div>
              </div>
            </section>
            """.trimIndent()

        private fun je(s: String?): String {
            if (s == null) return "null"
            val b = StringBuilder("\"")
            for (c in s) {
                when (c) {
                    '"' -> b.append("\\\"")
                    '\\' -> b.append("\\\\")
                    '\n' -> b.append("\\n")
                    '\r' -> b.append("\\r")
                    '\t' -> b.append("\\t")
                    else -> b.append(c)
                }
            }
            b.append("\"")
            return b.toString()
        }
    }

    private fun je(s: String?): String {
        if (s == null) return "null"
        val b = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> b.append("\\\"")
                '\\' -> b.append("\\\\")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                '\t' -> b.append("\\t")
                else -> b.append(c)
            }
        }
        b.append("\"")
        return b.toString()
    }

    /** Raw text of a stdlib file, or null when it cannot be read. */
    private fun readStdlibFileText(file: VirtualFile): String? =
        try {
            String(file.contentsToByteArray(), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }

    private fun resolve(
        table: Map<String, SymbolLoc>,
        call: CallRef,
    ): String? {
        if (call.recvClass != null) {
            table["${call.recvClass}#${call.token}"]?.let { return it.relPath }
        }
        table[call.token]?.let { return it.relPath }
        for ((key, loc) in table) {
            if (key.endsWith("#${call.token}")) return loc.relPath
        }
        return null
    }

    private fun scanFileCalls(text: String): List<CallRef> {
        val calls = ArrayList<CallRef>()
        val stack = ArrayDeque<String>()
        for (raw in text.lines()) {
            if (handleScanStructureLine(raw, stack)) continue
            collectScanCalls(raw, stack, calls)
        }
        return calls
    }

    /**
     * Namespace bookkeeping for one line: block end pops, type definition
     * pushes. Returns true when the line carries no calls.
     */
    private fun handleScanStructureLine(
        raw: String,
        stack: ArrayDeque<String>,
    ): Boolean {
        val trimmed = raw.trim()
        if (trimmed == "end" || trimmed == "}") {
            if (stack.isNotEmpty()) stack.removeLast()
            return true
        }
        val typeRe = Regex("""^\s*(?:class|struct|module|enum|lib|annotation)\s+([A-Z][\w:]*)""")
        val match = typeRe.find(raw) ?: return false
        pushNamespace(stack, match.groupValues[1])
        return true
    }

    /** Dot-calls and bare calls of one line into [calls]. */
    private fun collectScanCalls(
        raw: String,
        stack: ArrayDeque<String>,
        calls: ArrayList<CallRef>,
    ) {
        val enclosing = stack.lastOrNull()
        val line = cleanLine(raw)
        val dotRe = Regex("""(\bself\b|[A-Z]\w*|[a-z_]\w*)\s*\.\s*([a-z_]\w*[!?]?)""")
        dotRe.findAll(line).forEach { m ->
            val recv = m.groupValues[1]
            val method = m.groupValues[2]
            val recvClass =
                when {
                    recv == "self" -> enclosing
                    recv.first().isUpperCase() -> buildConstPath(recv, stack)
                    else -> null
                }
            calls.add(CallRef(method, "dot", recvClass))
        }
        val bareRe = Regex("""\b([a-z_]\w*[!?]?)\s*\(""")
        bareRe.findAll(line).forEach { m ->
            calls.add(CallRef(m.groupValues[1], "bare", enclosing))
        }
    }

    private fun buildConstPath(
        recv: String,
        stack: ArrayDeque<String>,
    ): String =
        if (recv.contains("::")) {
            recv
        } else {
            stack.lastOrNull()?.let {
                "$it::$recv"
            } ?: recv
        }

    private fun pushNamespace(
        stack: ArrayDeque<String>,
        full: String,
    ) {
        if (full.contains("::")) {
            val prefix = full.substringBeforeLast("::")
            while (stack.isNotEmpty() && !prefix.startsWith(stack.joinToString("::"))) stack.removeLast()
            for (seg in full.split("::")) stack.addLast(seg)
        } else {
            stack.addLast(full)
        }
    }

    private fun cleanLine(raw: String): String {
        var s = raw
        val urlIdx = s.indexOf("://")
        if (urlIdx >= 0) s = s.substring(0, urlIdx)
        val hashMatch = Regex("""(^|\s)#""").find(s)
        if (hashMatch != null) s = s.substring(0, hashMatch.range.first)
        return s
    }

    private fun isGeneratedFile(relPath: String): Boolean =
        relPath == "html/entities.cr" ||
            relPath.startsWith("llvm/") ||
            relPath.startsWith("crystal/llvm/")

    private fun isNoise(token: String): Boolean {
        if (isIoNoise(token)) return true
        if (MACRO_NAMES.contains(token)) return true
        if (KEYWORDS.contains(token)) return true
        // Verified 2026-09-16: no `def`/`macro`/`getter` named `asm` or `w`
        // anywhere in the stdlib — `asm(...)` is the inline-assembly keyword
        // (fiber/context/*) and `w` is always a block param/local
        // (io/byte_format.cr, log/log.cr). Unresolvable by construction.
        if (token == "asm" || token == "w") return true
        return false
    }

    private fun isIoNoise(token: String): Boolean =
        token == "to_slice" ||
            token == "inspect" ||
            token.startsWith("read_") ||
            token.startsWith("write_")

    private data class CallRef(
        val token: String,
        val kind: String,
        val recvClass: String?,
    )

    private data class StructType(
        val kind: String,
        val name: String,
        val qualified: String,
        val line: Int,
    )

    private data class StructMethod(
        val name: String,
        val owner: String?,
        val kind: String,
        val line: Int,
    )

    private data class StructCall(
        val ref: CallRef,
        val line: Int,
    )

    private fun scanStructure(text: String): Triple<List<StructType>, List<StructMethod>, List<StructCall>> {
        val acc = StructureAcc()
        text.lines().forEachIndexed { idx, raw ->
            acc.processLine(raw, idx + 1)
        }
        return Triple(acc.types, acc.methods, acc.calls)
    }

    /** Mutable per-file accumulator for [scanStructure]. */
    private inner class StructureAcc {
        val types = ArrayList<StructType>()
        val methods = ArrayList<StructMethod>()
        val calls = ArrayList<StructCall>()
        private val stack = ArrayDeque<String>()

        fun processLine(
            raw: String,
            lineNo: Int,
        ) {
            val trimmed = raw.trim()
            if (trimmed == "end" || trimmed == "}") {
                if (stack.isNotEmpty()) stack.removeLast()
                return
            }
            if (addTypeDef(raw, lineNo)) return
            if (addMethodDef(raw, lineNo)) return
            addCalls(raw, lineNo)
        }

        /** Type/alias/record definitions; true when the line was one. */
        private fun addTypeDef(
            raw: String,
            lineNo: Int,
        ): Boolean {
            val typeRe = Regex("""^\s*(?:abstract\s+)?(class|struct|module|enum|lib|annotation)\s+([A-Z][\w:]*)""")
            typeRe.find(raw)?.let { m ->
                val full = m.groupValues[2]
                pushNamespace(stack, full)
                types.add(StructType(m.groupValues[1], full.substringAfterLast("::"), full, lineNo))
                return true
            }
            val aliasRe = Regex("""^\s*alias\s+([A-Z][\w:]*)\s*=""")
            aliasRe.find(raw)?.let { m ->
                val full = m.groupValues[1]
                types.add(StructType("alias", full.substringAfterLast("::"), full, lineNo))
                return true
            }
            val recordRe = Regex("""^\s*record\s+([A-Z]\w*)""")
            recordRe.find(raw)?.let { m ->
                types.add(StructType("record", m.groupValues[1], m.groupValues[1], lineNo))
                return true
            }
            return false
        }

        /** `fun`/`def` definitions; true when the line was one. */
        private fun addMethodDef(
            raw: String,
            lineNo: Int,
        ): Boolean {
            val funRe = Regex("""^\s*fun\s+([A-Za-z_]\w*)""")
            funRe.find(raw)?.let { m ->
                methods.add(StructMethod(m.groupValues[1], stack.lastOrNull(), "fun", lineNo))
                return true
            }
            val defRe = Regex("""(?:^|\s)def\s+(?:self\s*\.\s*|self\s+)?([a-z_]\w*[!?]?)""")
            defRe.find(raw)?.let { m ->
                methods.add(StructMethod(m.groupValues[1], stack.lastOrNull(), "def", lineNo))
                return true
            }
            return false
        }

        /** Dot-calls and bare calls of a code line. */
        private fun addCalls(
            raw: String,
            lineNo: Int,
        ) {
            val enclosing = stack.lastOrNull()
            val line = cleanLine(raw)
            val dotRe = Regex("""(\bself\b|[A-Z]\w*|[a-z_]\w*)\s*\.\s*([a-z_]\w*[!?]?)""")
            dotRe.findAll(line).forEach { m ->
                val recv = m.groupValues[1]
                val method = m.groupValues[2]
                val recvClass =
                    when {
                        recv == "self" -> enclosing
                        recv.first().isUpperCase() -> buildConstPath(recv, stack)
                        else -> null
                    }
                calls.add(StructCall(CallRef(method, "dot", recvClass), lineNo))
            }
            val bareRe = Regex("""\b([a-z_]\w*[!?]?)\s*\(""")
            bareRe.findAll(line).forEach { m ->
                calls.add(StructCall(CallRef(m.groupValues[1], "bare", enclosing), lineNo))
            }
        }
    }

    companion object {
        private val STDLIB get() = io.github.unurgunite.crystal.StdlibTestPaths.STDLIB

        /** Report stylesheet (static — no interpolation). */
        private val GRAPH_CSS: String =
            """
            :root{--bg:#f4f6f9;--panel:#fff;--text:#1c2530;--muted:#6b7684;--border:#e2e7ee;--accent:#2563eb;--accent-soft:#e3ecfd;--green:#15803d;--green-soft:#e5f4ea;--red:#b3261e;--red-soft:#fdecea;--na:#64748b;--na-soft:#eef2f7;--mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;--radius:10px;--pad:16px;--fs:14px;--row-h:30px}
            html[data-theme="dark"]{--bg:#0e141c;--panel:#151d29;--text:#e6ebf2;--muted:#8b96a5;--border:#26303f;--accent:#6ea8fe;--accent-soft:#1d2f4d;--green:#4ade80;--green-soft:#122e1c;--red:#f87171;--red-soft:#3a1512;--na:#94a3b8;--na-soft:#212c3d}
            html[data-density="compact"]{--pad:10px;--fs:13px;--row-h:24px}
            *{box-sizing:border-box}
            [hidden]{display:none!important}
            body{font:var(--fs)/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;margin:0;background:var(--bg);color:var(--text)}
            .top{position:sticky;top:0;z-index:5;display:flex;justify-content:space-between;align-items:center;gap:16px;padding:12px 24px;background:var(--panel);border-bottom:1px solid var(--border)}
            .brand{display:flex;align-items:center;gap:12px;min-width:0}
            .gem{flex:0 0 26px;width:26px;height:26px;background:linear-gradient(135deg,#7c3aed,#2563eb 60%,#22d3ee);transform:rotate(45deg);border-radius:6px}
            .top h1{margin:0;font-size:17px;letter-spacing:-.01em;white-space:nowrap}
            .top .sub{color:var(--muted);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .toggles{display:flex;gap:8px;flex-shrink:0}
            .seg{display:inline-flex;background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:2px;gap:2px}
            .seg button{border:0;background:transparent;color:var(--muted);font-size:12px;padding:4px 10px;border-radius:6px;cursor:pointer}
            .seg button.on{background:var(--panel);color:var(--text);box-shadow:0 1px 2px rgba(0,0,0,.12)}
            .seg label{font-size:12px;color:var(--muted);padding:4px 10px;border-radius:6px;cursor:pointer;display:flex;gap:6px;align-items:center}
            .seg label.on{background:var(--panel);color:var(--text);box-shadow:0 1px 2px rgba(0,0,0,.12)}
            .seg input{accent-color:var(--accent)}
            .wrap{padding:20px 24px 40px;max-width:1180px;margin:0 auto}
            .stats{display:grid;grid-template-columns:220px repeat(5,1fr);gap:12px;margin-bottom:16px}
            .panel{background:var(--panel);border:1px solid var(--border);border-radius:var(--radius);padding:var(--pad) calc(var(--pad) + 2px);margin-bottom:16px}
            .card{background:var(--panel);border:1px solid var(--border);border-radius:var(--radius);padding:12px 14px;min-width:0}
            .card .n{font-size:24px;font-weight:750;letter-spacing:-.02em;font-variant-numeric:tabular-nums}
            .card .l{font-size:11px;text-transform:uppercase;letter-spacing:.05em;color:var(--muted);margin-top:2px}
            .card.green{border-top:3px solid var(--green)}.card.green .n{color:var(--green)}
            .card.red{border-top:3px solid var(--red)}.card.red .n{color:var(--red)}
            .card.gray .n{color:var(--na)}
            .donut-card{display:flex;align-items:center;gap:14px}
            .donut{flex:0 0 64px;width:64px;height:64px;border-radius:50%;-webkit-mask:radial-gradient(circle,transparent 55%,#000 56%);mask:radial-gradient(circle,transparent 55%,#000 56%)}
            .donut-legend{font-size:12px;color:var(--muted);display:grid;gap:3px}
            .dot{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:6px}
            .dot.g{background:var(--green)}.dot.r{background:var(--red)}.dot.n{background:var(--na)}
            .tabs{display:flex;gap:4px;margin-bottom:14px;border-bottom:1px solid var(--border);position:sticky;top:57px;z-index:4;background:var(--bg);padding-top:6px}
            .tabs button{border:0;background:transparent;color:var(--muted);font-size:13.5px;font-weight:600;padding:9px 14px;cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-1px}
            .tabs button.on{color:var(--accent);border-bottom-color:var(--accent)}
            .badge{display:inline-block;min-width:20px;text-align:center;font-size:11px;font-weight:700;background:var(--accent-soft);color:var(--accent);border-radius:10px;padding:0 7px;margin-left:4px;font-variant-numeric:tabular-nums}
            .panel h2{margin:0 0 10px;font-size:14px;display:flex;align-items:baseline;gap:8px;flex-wrap:wrap}
            .hint-inline{font-size:11.5px;font-weight:400;color:var(--muted)}
            .grid2{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}
            .bars{display:grid;gap:1px}
            .bar{display:grid;grid-template-columns:minmax(0,240px) 1fr 52px;align-items:center;gap:10px;width:100%;border:0;background:transparent;color:var(--text);font-family:var(--mono);font-size:12px;padding:3px 6px;border-radius:6px;cursor:pointer;text-align:left;min-height:var(--row-h)}
            button.bar:hover{background:var(--accent-soft)}
            .bar .k{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .bar .track{display:block;height:8px;background:var(--border);border-radius:4px;overflow:hidden}
            .bar .fill{display:block;height:100%;background:var(--accent);border-radius:4px}
            .bar .v{color:var(--muted);text-align:right;font-variant-numeric:tabular-nums}
            .controls{display:flex;gap:10px;align-items:center;margin-bottom:10px;flex-wrap:wrap}
            input[type=text]{flex:1;min-width:220px;padding:8px 12px;border:1px solid var(--border);border-radius:8px;font-size:13px;background:var(--panel);color:var(--text)}
            input[type=text]:focus{outline:2px solid var(--accent);outline-offset:-1px;border-color:var(--accent)}
            button.ghost{border:1px solid var(--border);background:var(--panel);color:var(--text);border-radius:8px;padding:6px 12px;font-size:12.5px;cursor:pointer}
            button.ghost:hover{border-color:var(--accent);color:var(--accent)}
            .active-filter{margin-bottom:10px;font-size:12.5px;color:var(--muted)}
            .active-filter b{color:var(--text);font-family:var(--mono);font-weight:600}
            .scroll{max-height:60vh;overflow:auto;border:1px solid var(--border);border-radius:8px}
            table.grid{border-collapse:collapse;width:100%;font-size:12.5px;table-layout:fixed}
            th,td{padding:5px 10px;text-align:left;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;border-bottom:1px solid var(--border)}
            th{position:sticky;top:0;background:var(--na-soft);cursor:pointer;user-select:none;font-size:11px;text-transform:uppercase;letter-spacing:.05em;color:var(--muted);z-index:1}
            tbody tr:hover{background:var(--accent-soft)}
            tbody tr:nth-child(even){background:color-mix(in srgb,var(--na-soft) 35%,transparent)}
            td.mono,th.mono{font-family:var(--mono)}
            .tokbtn,.filebtn{border:0;background:none;padding:0;font:inherit;color:var(--accent);cursor:pointer;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .tokbtn:hover,.filebtn:hover{text-decoration:underline}
            .filebtn{color:var(--text)}
            .tag{display:inline-block;font-size:11px;font-weight:600;padding:1px 8px;border-radius:20px;white-space:nowrap}
            .tag.u{background:var(--red-soft);color:var(--red)}.tag.n{background:var(--na-soft);color:var(--na)}.tag.r{background:var(--green-soft);color:var(--green)}
            .count{color:var(--muted);font-size:12px;margin-left:auto;font-variant-numeric:tabular-nums}
            .pager{display:flex;align-items:center;gap:10px;margin-top:10px}
            .pager button{border:1px solid var(--border);background:var(--panel);color:var(--text);border-radius:8px;padding:6px 14px;font-size:12.5px;cursor:pointer}
            .pager button:disabled{opacity:.4;cursor:default}
            .pager button:not(:disabled):hover{border-color:var(--accent);color:var(--accent)}
            .pager select{background:var(--panel);color:var(--text);border:1px solid var(--border);border-radius:8px;padding:6px 8px;font-size:12.5px;margin-left:auto}
            .hint{color:var(--muted);font-size:12px;margin-top:8px}
            .empty{padding:28px;text-align:center;color:var(--muted)}
            footer{color:var(--muted);font-size:12px;text-align:center;margin-top:8px}
            footer code{font-family:var(--mono)}
            button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--accent);outline-offset:1px}
            @media (max-width:1000px){.stats{grid-template-columns:repeat(3,1fr)}.donut-card{grid-column:1/-1}.grid2{grid-template-columns:1fr}.top h1{white-space:normal}.top .sub{display:none}}
            @media (max-width:640px){.stats{grid-template-columns:repeat(2,1fr)}.wrap{padding:12px 12px 32px}.tabs{top:65px;overflow-x:auto}}
            @media (prefers-reduced-motion:reduce){*{transition:none!important}}
            """.trimIndent()

        /** Report script below the data consts (static — no interpolation). */
        private val GRAPH_SCRIPT: String =
            """
            function esc(s){return (s==null?'':String(s)).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
            function byId(id){return document.getElementById(id);}
            function fmt(n){return Number(n).toLocaleString('en-US');}
            var state={q:'',cat:'all',sortK:0,sortAsc:true,page:0,perPage:200,file:'',token:'',qTok:'',qFile:''};
            var rows=RESOLVED.map(function(r){return [r[0],r[1],r[2],'R',r[3]];})
              .concat(UNRESOLVED.map(function(r){return [r[0],r[1],r[2],'U',''];}))
              .concat(NA.map(function(r){return [r[0],r[1],null,'N',''];}));
            function setTheme(v){
              document.documentElement.setAttribute('data-theme',v);
              try{localStorage.setItem('sg-theme',v);}catch(err){}
              document.querySelectorAll('#themeSeg button').forEach(function(b){
                var on=b.getAttribute('data-theme-val')===v;
                b.classList.toggle('on',on);b.setAttribute('aria-pressed',on?'true':'false');
              });
            }
            function setDensity(v){
              document.documentElement.setAttribute('data-density',v);
              try{localStorage.setItem('sg-density',v);}catch(err){}
              document.querySelectorAll('#densitySeg button').forEach(function(b){
                var on=b.getAttribute('data-density-val')===v;
                b.classList.toggle('on',on);b.setAttribute('aria-pressed',on?'true':'false');
              });
            }
            function switchTab(name){
              document.querySelectorAll('.tabs [data-tab]').forEach(function(b){
                var on=b.getAttribute('data-tab')===name;
                b.classList.toggle('on',on);b.setAttribute('aria-selected',on?'true':'false');
              });
              ['overview','tokens','files','calls'].forEach(function(t){byId('tab-'+t).hidden=(t!==name);});
            }
            function bar(host,data,max,pick){
              host.innerHTML='';
              data.forEach(function(pair){
                var k=pair[0],v=pair[1];
                var w=max?Math.max(2,Math.round(100*v/max)):0;
                var b=document.createElement('button');
                b.className='bar';b.type='button';b.title=k+' — '+v+' (click to filter calls)';
                b.innerHTML='<span class="k">'+esc(k)+'</span>'+
                  '<span class="track"><span class="fill" style="width:'+w+'%"></span></span>'+
                  '<span class="v">'+fmt(v)+'</span>';
                b.addEventListener('click',function(){pick(k);});
                host.appendChild(b);
              });
            }
            function aggPairs(list,idx){
              var m=new Map();
              list.forEach(function(r){var k=r[idx]||'(top-level)';m.set(k,(m.get(k)||0)+1);});
              return Array.from(m.entries()).sort(function(a,b){return b[1]-a[1];});
            }
            var tokPairs=aggPairs(UNRESOLVED,1);
            var filePairs=aggPairs(UNRESOLVED,0);
            function renderRank(wrapId,countId,pairs,q,pick,label){
              var ql=q.toLowerCase();
              var f=pairs.filter(function(p){return !ql||p[0].toLowerCase().indexOf(ql)>=0;});
              var hits=f.reduce(function(a,p){return a+p[1];},0);
              var max=pairs.length?pairs[0][1]:1;
              var wrap=byId(wrapId);wrap.innerHTML='';
              var tbl=document.createElement('table');tbl.className='grid';
              var thead=tbl.createTHead();var htr=thead.insertRow(-1);
              ['#',label,'count','share'].forEach(function(h){var c=document.createElement('th');c.textContent=h;htr.appendChild(c);});
              var tb=tbl.createTBody();
              f.slice(0,500).forEach(function(p,i){
                var tr=tb.insertRow(-1);
                var c0=tr.insertCell(-1);c0.textContent=String(i+1);
                var c1=tr.insertCell(-1);c1.className='mono';
                var btn=document.createElement('button');btn.className='tokbtn';btn.type='button';
                btn.textContent=p[0];btn.title=p[0]+' — filter calls';
                btn.addEventListener('click',function(){pick(p[0]);});
                c1.appendChild(btn);
                var c2=tr.insertCell(-1);c2.textContent=fmt(p[1]);
                var c3=tr.insertCell(-1);
                var w=Math.max(2,Math.round(100*p[1]/max));
                c3.innerHTML='<span class="track"><span class="fill" style="width:'+w+'%"></span></span>';
              });
              wrap.appendChild(tbl);
              byId(countId).textContent=fmt(f.length)+' distinct · '+fmt(hits)+' hits';
            }
            function pickToken(k){state.token=k;state.page=0;switchTab('calls');render();}
            function pickFile(k){state.file=k;state.page=0;switchTab('calls');render();}
            function filteredRows(){
              var q=state.q.toLowerCase();
              return rows.filter(function(r){
                if(state.cat!=='all'&&r[3]!==state.cat)return false;
                if(state.file&&r[0]!==state.file)return false;
                if(state.token&&r[1]!==state.token)return false;
                if(!q)return true;
                return (r[0]||'').toLowerCase().indexOf(q)>=0||(r[1]||'').toLowerCase().indexOf(q)>=0||(r[2]||'').toLowerCase().indexOf(q)>=0;
              });
            }
            function statusPill(r){
              var span=document.createElement('span');
              if(r[3]==='U'){span.className='tag u';span.textContent='unresolved';}
              else if(r[3]==='N'){span.className='tag n';span.textContent='NA';}
              else{span.className='tag r';span.textContent='resolved';span.title=r[4]||'';}
              return span;
            }
            function render(){
              var f=filteredRows();
              f.sort(function(a,b){var x=a[state.sortK]||'',y=b[state.sortK]||'';return (x<y?-1:x>y?1:0)*(state.sortAsc?1:-1);});
              var tot=f.length;
              var pages=Math.max(1,Math.ceil(tot/state.perPage));
              if(state.page>=pages)state.page=pages-1;
              var slice=f.slice(state.page*state.perPage,(state.page+1)*state.perPage);
              var wrap=byId('tblWrap');wrap.innerHTML='';
              if(!slice.length){
                wrap.innerHTML='<div class="empty">No calls match these filters.</div>';
              }else{
                var tbl=document.createElement('table');tbl.className='grid';
                var thead=tbl.createTHead();var htr=thead.insertRow(-1);
                var heads=['file','token','receiver','status'];
                for(var i=0;i<heads.length;i++){
                  var c=document.createElement('th');c.className='mono';c.setAttribute('data-k',i);
                  c.textContent=heads[i]+(state.sortK===i?(state.sortAsc?' ▲':' ▼'):'');
                  htr.appendChild(c);
                }
                var tb=tbl.createTBody();
                slice.forEach(function(r){
                  var tr=tb.insertRow(-1);
                  var c0=tr.insertCell(-1);c0.className='mono';
                  var fb=document.createElement('button');fb.className='filebtn';fb.type='button';
                  fb.textContent=r[0]||'';fb.title=(r[0]||'')+' — filter calls';
                  fb.setAttribute('data-file',r[0]||'');c0.appendChild(fb);c0.title=r[0]||'';
                  var c1=tr.insertCell(-1);c1.className='mono';
                  var tb2=document.createElement('button');tb2.className='tokbtn';tb2.type='button';
                  tb2.textContent=r[1]||'';tb2.title=(r[1]||'')+' — filter calls';
                  tb2.setAttribute('data-tok',r[1]||'');c1.appendChild(tb2);
                  var c2=tr.insertCell(-1);c2.className='mono';c2.textContent=r[2]||'';
                  var c3=tr.insertCell(-1);c3.appendChild(statusPill(r));
                  if(r[3]==='R'&&r[4]){c3.title=r[4];}
                });
                wrap.appendChild(tbl);
              }
              byId('pageInfo').textContent='page '+(state.page+1)+' of '+fmt(pages)+' · showing '+fmt(slice.length)+' of '+fmt(tot);
              byId('prev').disabled=state.page<=0;
              byId('next').disabled=state.page>=pages-1;
              var pins=[];
              if(state.file)pins.push('file = '+state.file);
              if(state.token)pins.push('token = '+state.token);
              var af=byId('activeFilter');
              if(pins.length){af.hidden=false;af.innerHTML='pinned: <b>'+esc(pins.join(' · '))+'</b>';}
              else{af.hidden=true;af.innerHTML='';}
              byId('clearFilters').hidden=!pins.length;
            }
            function clearPins(){state.file='';state.token='';state.page=0;render();}
            document.querySelectorAll('#themeSeg button').forEach(function(b){
              b.addEventListener('click',function(){setTheme(b.getAttribute('data-theme-val'));});
            });
            document.querySelectorAll('#densitySeg button').forEach(function(b){
              b.addEventListener('click',function(){setDensity(b.getAttribute('data-density-val'));});
            });
            document.querySelectorAll('.tabs [data-tab]').forEach(function(b){
              b.addEventListener('click',function(){switchTab(b.getAttribute('data-tab'));});
            });
            byId('q').addEventListener('input',function(){state.q=byId('q').value;state.page=0;render();});
            document.querySelectorAll('input[name=cat]').forEach(function(el){
              el.addEventListener('change',function(){
                state.cat=(document.querySelector('input[name=cat]:checked')||{}).value||'all';
                state.page=0;
                document.querySelectorAll('.cats label').forEach(function(l){
                  l.classList.toggle('on',l.querySelector('input').checked);
                });
                render();
              });
            });
            byId('tblWrap').addEventListener('click',function(e){
              var th=e.target.closest('th[data-k]');
              if(th){var k=+th.getAttribute('data-k');if(k===state.sortK)state.sortAsc=!state.sortAsc;else{state.sortK=k;state.sortAsc=true;}render();return;}
              var fb=e.target.closest('.filebtn');
              if(fb){state.file=fb.getAttribute('data-file');state.page=0;render();return;}
              var tk=e.target.closest('.tokbtn');
              if(tk){state.token=tk.getAttribute('data-tok');state.page=0;render();}
            });
            byId('prev').addEventListener('click',function(){if(state.page>0){state.page--;render();}});
            byId('next').addEventListener('click',function(){state.page++;render();});
            byId('perPage').addEventListener('change',function(){state.perPage=+byId('perPage').value;state.page=0;render();});
            byId('clearFilters').addEventListener('click',clearPins);
            byId('qTok').addEventListener('input',function(){state.qTok=byId('qTok').value;renderRank('tokWrap','countTok',tokPairs,state.qTok,pickToken,'token');});
            byId('qFile').addEventListener('input',function(){state.qFile=byId('qFile').value;renderRank('fileWrap','countFile',filePairs,state.qFile,pickFile,'file');});
            try{
              var st=localStorage.getItem('sg-theme');if(st==='light'||st==='dark')setTheme(st);
              var dn=localStorage.getItem('sg-density');if(dn==='comfortable'||dn==='compact')setDensity(dn);
            }catch(err2){}
            byId('badgeTok').textContent=fmt(tokPairs.length);
            byId('badgeFile').textContent=fmt(filePairs.length);
            byId('badgeCalls').textContent=fmt(rows.length);
            var maxTok=TOP_TOK.length?TOP_TOK[0][1]:1;
            var maxFile=TOP_FILE.length?TOP_FILE[0][1]:1;
            var maxNa=NA_TOK.length?NA_TOK[0][1]:1;
            bar(byId('topTok'),TOP_TOK,maxTok,pickToken);
            bar(byId('topFile'),TOP_FILE,maxFile,pickFile);
            bar(byId('naTok'),NA_TOK,maxNa,pickToken);
            renderRank('tokWrap','countTok',tokPairs,'',pickToken,'token');
            renderRank('fileWrap','countFile',filePairs,'',pickFile,'file');
            render();
            """.trimIndent()
        private val MACRO_NAMES =
            setOf(
                "getter",
                "setter",
                "property",
                "delegate",
                "forward_missing_to",
                "define_new",
                "define_clone",
                "define_finalize",
                "define_initialize",
            )
        private val KEYWORDS =
            setOf(
                "if",
                "unless",
                "while",
                "until",
                "case",
                "when",
                "begin",
                "def",
                "class",
                "module",
                "struct",
                "enum",
                "lib",
                "macro",
                "annotation",
                "include",
                "extend",
                "yield",
                "return",
                "next",
                "break",
                "super",
                "self",
                "nil",
                "true",
                "false",
                "typeof",
                "sizeof",
                "instance_sizeof",
                "pointerof",
                "uninitialized",
                "as",
                "is_a?",
                "responds_to?",
                "nil?",
                "as?",
                "char_sequence?",
                "fun",
                "require",
                "private",
                "protected",
                "abstract",
                "out",
                "in",
                "do",
                "then",
                "else",
                "elsif",
                "rescue",
                "ensure",
            )
    }
}
