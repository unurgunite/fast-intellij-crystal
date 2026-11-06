package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareCommandSpaceFirst
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.stubs.CrystalClassIndex

/**
 * `param` macro declarations as virtual constructor parameters.
 *
 * Job frameworks (Mosquito `param attempt_id : Int64`, and any other `param`
 * macro) generate `initialize` at compile time — no literal `def initialize`
 * exists in source, so constructor resolution found nothing and every
 * named argument (`Job.new(attempt_id: ...)`) reported "Unknown named
 * argument". This index collects `param name : Type [= default]` calls per
 * enclosing type, once per file version (dropped on any PSI change).
 *
 * Priority (in the caller): literal `def initialize` > `record` > this.
 * Only offsets/names/types are cached (never PSI), so the cache cannot go stale.
 */
internal object CrystalParamLookup {
    /** One `param` declaration: bare name, optional default presence. */
    data class ParamEntry(
        val name: String,
        val hasDefault: Boolean,
    )

    /**
     * `param` entries declared directly in [typeName]'s body, in document
     * order. Empty when the type declares none (no guessing across types).
     *
     * Current-file only: the call site and the class usually share a file
     * (tests, single-file scripts). Cross-file callers use [paramsForProject].
     */
    fun paramsFor(
        typeName: String,
        file: PsiFile,
    ): List<ParamEntry> {
        val byType =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result(indexFile(file), PsiModificationTracker.MODIFICATION_COUNT)
            }
        return byType[typeName].orEmpty()
    }

    /**
     * Project-wide `param` lookup: current-file fast path, then the file
     * where [typeName] is actually defined (stub class index).
     *
     * Real projects split call sites and class definitions across files
     * (`app.cr` calls `CamTestJob.new`, the class lives in `pipeline.cr`):
     * file-local lookup reported "Unknown named argument" on every such
     * call. The definition file's own per-file index is reused, so no
     * whole-project PSI walk happens on the inspection hot path.
     */
    fun paramsForProject(
        typeName: String,
        project: Project,
        currentFile: PsiFile,
    ): List<ParamEntry> {
        paramsFor(typeName, currentFile).takeIf { it.isNotEmpty() }?.let { return it }
        // A Crystal type may be reopened: `class Job` with `param count : Int32`
        // in one file and `param attempt : Int64 = 1` in another. Resolving a
        // single declaration reported the other closure's params as unknown
        // and its own as required, so every reopening is merged.
        val files =
            reopenedDefinitionFiles(typeName, project).ifEmpty {
                val definition =
                    CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile)?.element
                        ?: return emptyList()
                listOfNotNull(definition.containingFile)
            }
        return files
            .filter { it.virtualFile != currentFile.virtualFile }
            .flatMap { paramsFor(typeName, it) }
    }

    /**
     * The same lookup split per reopening: each reopening declares its own
     * generated `initialize`, so params of different reopenings are separate
     * overloads and must not be merged into one mandatory-param list.
     */
    fun paramSetsForProject(
        typeName: String,
        project: Project,
        currentFile: PsiFile,
    ): List<List<ParamEntry>> {
        val local = paramsFor(typeName, currentFile)
        if (local.isNotEmpty()) return listOf(local)
        val files =
            reopenedDefinitionFiles(typeName, project).ifEmpty {
                val definition =
                    CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile)?.element
                        ?: return emptyList()
                listOfNotNull(definition.containingFile)
            }
        return files.mapNotNull { paramsFor(typeName, it).takeIf { set -> set.isNotEmpty() } }
    }

    /**
     * Every file that opens a type with this short name. Reopenings are
     * separate PSI elements in the same `CrystalClassIndex` bucket.
     */
    private fun reopenedDefinitionFiles(
        typeName: String,
        project: Project,
    ): List<PsiFile> {
        val short = typeName.substringAfterLast("::")
        val files = LinkedHashSet<PsiFile>()
        for (element in StubIndex.getElements(
            CrystalClassIndex.KEY,
            short,
            project,
            GlobalSearchScope.allScope(project),
            CrystalNamedElement::class.java,
        )) {
            element.containingFile?.let { files.add(it) }
        }
        return files.toList()
    }

    /** All `param` calls of [file] grouped by enclosing type name. */
    private fun indexFile(file: PsiFile): Map<String, List<ParamEntry>> {
        val byType = LinkedHashMap<String, MutableList<ParamEntry>>()
        for (call in collectParamCalls(file)) {
            val enclosing = CrystalPsiUtils.getEnclosingType(call)?.let { CrystalPsiUtils.buildQualifiedName(it) }
            val simple = enclosing?.substringAfterLast("::") ?: continue
            paramEntry(call)?.let { byType.getOrPut(simple) { ArrayList() }.add(it) }
        }
        return byType
    }

    /** Every `param ...` bare-command call in [file] (both spacing shapes). */
    private fun collectParamCalls(file: PsiFile): List<PsiElement> {
        val out = ArrayList<PsiElement>()
        for (call in PsiTreeUtil.collectElementsOfType(file, CrystalBareCommandExpression::class.java)) {
            if (calleeName(call) == "param") out.add(call)
        }
        for (call in PsiTreeUtil.collectElementsOfType(file, CrystalBareCommandSpaceFirst::class.java)) {
            if (calleeName(call) == "param") out.add(call)
        }
        return out
    }

    /** Callee name of a bare command (first IDENTIFIER child), or null. */
    private fun calleeName(call: PsiElement): String? {
        val list =
            (call as? CrystalBareCommandExpression)?.bareArgumentList
                ?: (call as? CrystalBareCommandSpaceFirst)?.bareArgumentList
                ?: return null
        var child = call.firstChild
        while (child != null && child != list) {
            if (child.node?.elementType == CrystalTypes.IDENTIFIER) return child.text
            child = child.nextSibling
        }
        return null
    }

    /**
     * One `param name : Type [= default]` entry, or null when the shape does
     * not match (wrong callee already filtered; guards the argument shape).
     */
    private fun paramEntry(call: PsiElement): ParamEntry? {
        val argText = firstArgText(call) ?: return null
        if (!argText.contains(":")) return null
        val name = argText.substringBefore(":").trim()
        if (name.isEmpty() || (!name[0].isLowerCase() && name[0] != '_')) return null
        return ParamEntry(name, hasDefault = argText.contains("="))
    }

    /** Raw text of the first bare argument (the `name : Type [= default]` chunk). */
    private fun firstArgText(call: PsiElement): String? {
        val list =
            (call as? CrystalBareCommandExpression)?.bareArgumentList
                ?: (call as? CrystalBareCommandSpaceFirst)?.bareArgumentList
                ?: return null
        return list.bareArgumentList.firstOrNull()?.text
    }
}
