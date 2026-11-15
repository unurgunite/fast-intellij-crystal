package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.psi.CrystalAliasDefinition
import io.github.unurgunite.crystal.psi.CrystalTypeReference
import io.github.unurgunite.crystal.stubs.CrystalAliasIndex

/**
 * Expands a leading type alias in a qualified name: shop declares
 * `alias HK = Shop::HTTP` and then writes
 * `HK::Controller::ParameterMetadata(T).new(...)`. Without expansion the type
 * lookup searches for a class literally named `HK::...`, finds nothing, and
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

    /** Aliased target of [name] (`Shop::HTTP` for `HK`). */
    fun resolveTarget(
        name: String,
        project: Project,
        currentFile: PsiFile?,
    ): String? {
        // Keyed by file as well as name: a file-owned alias shadows a shard's
        // same-named one, so the answer differs per calling file. Aliases are
        // immutable until the next PSI change, and the index walk otherwise
        // runs on every qualified type reference in the file.
        val cache =
            com.intellij.psi.util.CachedValuesManager
                .getManager(project)
                .getCachedValue(
                    project,
                    ALIAS_TARGETS_KEY,
                    {
                        com.intellij.psi.util.CachedValueProvider.Result.create(
                            HashMap<String, String?>(),
                            com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT,
                        )
                    },
                    false,
                )
        val key = (currentFile?.virtualFile?.path ?: "") + "#" + name
        if (cache.containsKey(key)) return cache[key]
        val resolved = lookupTarget(name, project, currentFile)
        cache[key] = resolved
        return resolved
    }

    private fun lookupTarget(
        name: String,
        project: Project,
        currentFile: PsiFile?,
    ): String? {
        val hits =
            StubIndex.getElements(
                CrystalAliasIndex.KEY,
                name,
                project,
                CrystalSearchScopes.project(project),
                CrystalAliasDefinition::class.java,
            )
        // Aliases are usually one per name per project; a file-owned definition
        // wins so a spec-local alias is not shadowed by a shard's same-named one.
        val alias = hits.firstOrNull { it.containingFile?.virtualFile == currentFile?.virtualFile } ?: hits.firstOrNull()
        val target = alias?.typeReferenceList?.firstOrNull()?.text ?: return null
        return target.ifEmpty { null }
    }

    private val ALIAS_TARGETS_KEY =
        com.intellij.openapi.util.Key.create<
            com.intellij.psi.util.CachedValue<MutableMap<String, String?>>,
        >("crystal.alias.targets")

    /** The aliased target of a concrete reference, for tests and diagnostics. */
    fun targetOf(reference: CrystalTypeReference?): String? = reference?.text
}
