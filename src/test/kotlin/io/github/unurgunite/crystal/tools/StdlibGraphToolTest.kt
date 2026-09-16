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
import io.github.unurgunite.crystal.psi.CrystalReference
import io.github.unurgunite.crystal.psi.SymbolLoc
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
<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Crystal Stdlib Reference Graph</title>
<style>
$GRAPH_CSS
</style></head>
<body>
<header><h1>Crystal Stdlib Reference Graph</h1>
<div class="sub">Whole-stdlib dot-call / bare-call resolution — text-scan harness (item 1-3)</div></header>
<div class="wrap">
  <div class="cards">
    <div class="card"><div class="n">${stats.files}</div><div class="l">files</div></div>
    <div class="card"><div class="n">${stats.tableSize}</div><div class="l">symbol table</div></div>
    <div class="card green"><div class="n">${stats.resolvedPct}%</div><div class="l">resolved (${stats.resolved})</div></div>
    <div class="card red"><div class="n">${stats.actionablePct}%</div><div class="l">unresolved (${stats.unresolved})</div></div>
    <div class="card gray"><div class="n">${stats.na}</div><div class="l">NA (noise)</div></div>
    <div class="card"><div class="n">${stats.elapsedSec}s</div><div class="l">scan time</div></div>
  </div>

  <div class="panel"><h2>Top unresolved tokens (actionable)</h2><div class="bars" id="topTok"></div></div>
  <div class="panel"><h2>Top unresolved files</h2><div class="bars" id="topFile"></div></div>
  <div class="panel"><h2>Top NA tokens (noise)</h2><div class="bars" id="naTok"></div></div>

  <div class="panel">
    <h2>Calls</h2>
    <div class="controls">
      <input type="text" id="q" placeholder="filter by token, receiver class, or file…">
      <label><input type="radio" name="cat" id="catAll" value="all" checked> all</label>
      <label><input type="radio" name="cat" id="catU" value="U"> unresolved</label>
      <label><input type="radio" name="cat" id="catN" value="N"> NA</label>
      <label><input type="radio" name="cat" id="catR" value="R"> resolved</label>
      <span id="count"></span>
    </div>
    <div id="tblWrap" style="max-height:60vh;overflow:auto"></div>
    <div class="hint">receiver = attempted class for dot/bare calls (null = top-level / unknown). Click a column header to sort.</div>
  </div>
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
            body{font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;margin:0;background:#f6f7f9;color:#222}
            header{background:#1e2a3a;color:#fff;padding:18px 24px}
            header h1{margin:0;font-size:18px}
            header .sub{opacity:.7;font-size:12px;margin-top:4px}
            .wrap{padding:20px 24px;max-width:1100px;margin:0 auto}
            .cards{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:18px}
            .card{background:#fff;border:1px solid #e3e6ea;border-radius:8px;padding:12px 16px;min-width:120px}
            .card .n{font-size:22px;font-weight:700}
            .card .l{font-size:11px;text-transform:uppercase;letter-spacing:.04em;color:#7a828c}
            .card.green .n{color:#137a3b}.card.red .n{color:#b3261e}.card.gray .n{color:#6b7280}
            .panel{background:#fff;border:1px solid #e3e6ea;border-radius:8px;padding:14px 16px;margin-bottom:18px}
            .panel h2{margin:0 0 10px;font-size:14px}
            .bars{font-family:ui-monospace,Menlo,monospace;font-size:12px}
            .bar{display:flex;align-items:center;gap:8px;padding:2px 0}
            .bar .k{flex:0 0 260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .bar .v{color:#555}
            .bar .fill{height:8px;background:#3b82f6;border-radius:4px}
            .controls{display:flex;gap:10px;align-items:center;margin-bottom:10px;flex-wrap:wrap}
            input[type=text]{flex:1;min-width:220px;padding:8px 10px;border:1px solid #ccd2d9;border-radius:6px;font-size:13px}
            label{font-size:13px;display:flex;gap:6px;align-items:center}
            table.grid{border-collapse:collapse;width:100%;font-size:12.5px;table-layout:fixed}
            th,td{border:1px solid #d3d8de;padding:4px 8px;text-align:left;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            th{position:sticky;top:0;background:#e9edf2;cursor:pointer;user-select:none;border-bottom:2px solid #b9c0c9}
            tbody tr:nth-child(even){background:#f6f8fa}
            tbody tr:hover{background:#e8f0fb}
            td.mono,th.mono{font-family:ui-monospace,Menlo,monospace}
            #tblWrap{font-size:12.5px}
            th:nth-child(1),td:nth-child(1){width:42%}
            th:nth-child(2),td:nth-child(2){width:24%}
            th:nth-child(3),td:nth-child(3){width:24%}
            th:nth-child(4),td:nth-child(4){width:10%}
            .tag{font-size:11px;padding:1px 6px;border-radius:4px}
            .tag.u{background:#fde8e6;color:#b3261e}.tag.n{background:#eef2f7;color:#555}.tag.r{background:#e6f4ea;color:#137a3b}
            #count{color:#7a828c;font-size:12px;margin:6px 0}
            .hint{color:#7a828c;font-size:12px}
            """.trimIndent()

        /** Report script below the data consts (static — no interpolation). */
        private val GRAPH_SCRIPT: String =
            """
            function esc(s){return (s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
            function bar(host,data,max){
              host.innerHTML = data.map(([k,v])=>{
                const w = max? Math.max(2,Math.round(100*v/max)) : 0;
                return '<div class="bar"><div class="k" title="'+esc(k)+'">'+esc(k)+
                  '</div><div class="fill" style="width:'+w+'%"></div><div class="v">'+v+'</div></div>';
              }).join('');
            }
            const maxTok = TOP_TOK.length?TOP_TOK[0][1]:1;
            const maxFile = TOP_FILE.length?TOP_FILE[0][1]:1;
            const maxNa = NA_TOK.length?NA_TOK[0][1]:1;
            bar(document.getElementById('topTok'),TOP_TOK,maxTok);
            bar(document.getElementById('topFile'),TOP_FILE,maxFile);
            bar(document.getElementById('naTok'),NA_TOK,maxNa);

            let rows = RESOLVED.map(r=>[r[0],r[1],r[2],'R',r[3]])
              .concat(UNRESOLVED.map(r=>[r[0],r[1],r[2],'U','']))
              .concat(NA.map(r=>[r[0],r[1],null,'N','']));
            let sortK=0, sortAsc=true;
            function render(){
              const q = document.getElementById('q').value.toLowerCase();
              const cat = (document.querySelector('input[name=cat]:checked')||{}).value || 'all';
              let f = rows.filter(r=>{
                if(cat!=='all' && r[3]!==cat) return false;
                if(!q) return true;
                return (r[0]||'').toLowerCase().includes(q)||(r[1]||'').toLowerCase().includes(q)||(r[2]||'').toLowerCase().includes(q);
              });
              f.sort((a,b)=>{const x=a[sortK]||'',y=b[sortK]||'';return (x<y?-1:x>y?1:0)*(sortAsc?1:-1);});
              const tot=f.length;
              f=f.slice(0,2000);
              const wrap = document.getElementById('tblWrap');
              wrap.innerHTML = '';
              const tbl = document.createElement('table');
              tbl.className = 'grid';
              const thead = tbl.createTHead();
              const htr = thead.insertRow(-1);
              const heads = ['file','token','receiver','status'];
              for (let i=0;i<heads.length;i++){
                const c = document.createElement('th');
                c.className = 'mono'; c.dataset.k = i; c.textContent = heads[i];
                htr.appendChild(c);
              }
              const tb = tbl.createTBody();
              for (const r of f){
                const tr = tb.insertRow(-1);
                const c0 = tr.insertCell(-1); c0.className='mono'; c0.textContent=r[0]||''; c0.title=r[0]||'';
                const c1 = tr.insertCell(-1); c1.className='mono'; c1.textContent=r[1]||'';
                const c2 = tr.insertCell(-1); c2.className='mono'; c2.textContent=r[2]||'';
                const c3 = tr.insertCell(-1);
                const span = document.createElement('span');
                let cls, label;
                if(r[3]==='U'){cls='u';label='unresolved';}
                else if(r[3]==='N'){cls='n';label='NA';}
                else {cls='r';label='resolved → '+(r[4]||'');}
                span.className = 'tag ' + cls;
                span.textContent = label;
                span.title = r[4]||'';
                c3.appendChild(span);
              }
              wrap.appendChild(tbl);
              document.getElementById('count').textContent = 'showing ' + f.length + ' of ' + tot;
            }
            document.getElementById('q').addEventListener('input',render);
            document.querySelectorAll('input[name=cat]').forEach(el=>el.addEventListener('change',render));
            document.getElementById('tblWrap').addEventListener('click',e=>{
              const th = e.target.closest('th[data-k]'); if(!th) return;
              const k = +th.dataset.k; if(k===sortK) sortAsc=!sortAsc; else { sortK=k; sortAsc=true; } render();
            });
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
