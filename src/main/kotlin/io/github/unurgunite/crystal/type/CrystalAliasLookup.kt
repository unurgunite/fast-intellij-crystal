package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.psi.CrystalAliasDefinition
import io.github.unurgunite.crystal.psi.CrystalTypeReference
import io.github.unurgunite.crystal.stubs.CrystalAliasIndex

/**
 * Expands a leading type alias in a qualified name: athena declares
 * `alias AHK = Athena::HTTPKernel` and then writes
 * `AHK::Controller::ParameterMetadata(T).new(...)`. Without expansion the type
 * lookup searches for a class literally named `AHK::...`, finds nothing, and
 * constructor arity is checked against the wrong overloads (or none).
 */
internal object CrystalAliasLookup {
    /**
     * [qualified] with its first `::` segment replaced by the aliased target,
     * or null when the leading segment is not an alias.
     */
    fun expand(
        qualified: String,
        project: Project,
        currentFile: PsiFile?,
    ): String? {
        val head = qualified.substringBefore("::")
        if (head.isEmpty() || qualified == head) return null
        val target = resolveTarget(head, project, currentFile) ?: return null
        return target + qualified.removePrefix(head)
    }

    /** Aliased target of [name] (`Athena::HTTPKernel` for `AHK`). */
    fun resolveTarget(
        name: String,
        project: Project,
        currentFile: PsiFile?,
    ): String? {
        val hits =
            StubIndex.getElements(
                CrystalAliasIndex.KEY,
                name,
                project,
                GlobalSearchScope.allScope(project),
                CrystalAliasDefinition::class.java,
            )
        // Aliases are usually one per name per project; a file-owned definition
        // wins so a spec-local alias is not shadowed by a shard's same-named one.
        val alias = hits.firstOrNull { it.containingFile?.virtualFile == currentFile?.virtualFile } ?: hits.firstOrNull()
        val target = alias?.typeReferenceList?.firstOrNull()?.text ?: return null
        return target.ifEmpty { null }
    }

    /** The aliased target of a concrete reference, for tests and diagnostics. */
    fun targetOf(reference: CrystalTypeReference?): String? = reference?.text
}
