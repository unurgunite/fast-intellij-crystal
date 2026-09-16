package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex
import io.github.unurgunite.crystal.type.CrystalMethodLookup
import io.github.unurgunite.crystal.type.CrystalRecordLookup
import io.github.unurgunite.crystal.type.CrystalTypeHierarchy
import io.github.unurgunite.crystal.type.CrystalTypeHierarchy.TypeKind

/**
 * Shared lookup orchestration for completion and navigation: hierarchy-based
 * method lookups with fading priority. Pure type-system queries
 * (`getInitializeMethod`, `getEnclosingClassName`, signatures) live in
 * [CrystalMethodLookup]; `record` index queries in `CrystalRecordLookup`;
 * lookup builders in [CrystalLookupBuilders]; hierarchy walking in
 * [CrystalTypeHierarchy].
 */
object CrystalCompletionHelper {
    // Lookup priorities by hierarchy depth (own class first, ancestors fading out).
    private const val HIERARCHY_PRIORITY_SELF = 10.0
    private const val HIERARCHY_PRIORITY_PARENT = 5.0
    private const val HIERARCHY_PRIORITY_GRANDPARENT = 2.0
    private const val HIERARCHY_PRIORITY_DISTANT = 1.0

    /**
     * Returns all static methods (def self.xxx) of a type definition.
     */
    fun getStaticMethods(
        typeName: String,
        project: Project,
    ): List<CrystalMethodDefinition> {
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project) ?: return emptyList()
        return CrystalTypeHierarchy.getMethodsFromType(result).filter { CrystalMethodLookup.isStaticMethod(it) }
    }

    /**
     * Returns instance methods as LookupElements with hierarchy-based priority.
     * Own class methods get highest priority, inherited methods get progressively lower.
     */
    fun getMethodsAsLookups(
        typeName: String,
        project: Project,
    ): List<LookupElement> {
        val typeResult = CrystalTypeHierarchy.findTypeByName(typeName, project) ?: return emptyList()
        val hierarchy = CrystalTypeHierarchy.hierarchyWithImplicitObject(typeResult)

        val scope = GlobalSearchScope.allScope(project)
        val result = mutableListOf<LookupElement>()
        val seen = mutableSetOf<String>()

        for ((depth, className) in hierarchy.withIndex()) {
            addHierarchyLevelLookups(className, project, scope, seen, result, hierarchyPriority(depth))
        }
        return result
    }

    /** Instance-method lookups of one hierarchy level, deduplicated (overloads kept). */
    private fun addHierarchyLevelLookups(
        className: String,
        project: Project,
        scope: GlobalSearchScope,
        seen: MutableSet<String>,
        result: MutableList<LookupElement>,
        priority: Double,
    ) {
        val elements =
            StubIndex.getElements(
                CrystalMethodByClassIndex.KEY,
                className,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        for (method in elements) {
            addUniqueMethodLookup(method, seen, result, priority)
        }
    }

    /** One non-static method as a lookup unless its name+signature was already added. */
    private fun addUniqueMethodLookup(
        method: CrystalMethodDefinition,
        seen: MutableSet<String>,
        result: MutableList<LookupElement>,
        priority: Double,
    ) {
        if (CrystalMethodLookup.isStaticMethod(method)) return
        val name = method.name ?: return
        // Deduplicate by name+signature so overloads with different params appear separately
        val key = "$name${CrystalLookupBuilders.getParameterSignature(method)}"
        if (seen.add(key)) {
            result.add(CrystalLookupBuilders.buildMethodLookup(method, priority))
        }
    }

    /** Lookup priority by hierarchy depth: own methods first, ancestors fading out. */
    private fun hierarchyPriority(depth: Int): Double =
        when (depth) {
            0 -> HIERARCHY_PRIORITY_SELF
            1 -> HIERARCHY_PRIORITY_PARENT
            2 -> HIERARCHY_PRIORITY_GRANDPARENT
            else -> HIERARCHY_PRIORITY_DISTANT
        }

    /**
     * Returns whether a type can be instantiated with .new (classes and structs only).
     */
    fun canInstantiate(
        typeName: String,
        project: Project,
    ): Boolean {
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project) ?: return true // unknown type — offer new as fallback
        return result.kind == TypeKind.CLASS || result.kind == TypeKind.STRUCT
    }

    /**
     * Returns all class/module/struct/enum names from the project-wide StubIndex.
     */
    fun getAllClassNames(project: Project): Collection<String> = StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)
}
