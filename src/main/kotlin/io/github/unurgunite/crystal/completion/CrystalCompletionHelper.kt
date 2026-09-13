package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.completion.CrystalTypeHierarchy.TypeKind
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex

/**
 * Shared queries for completion and navigation: type lookup and hierarchy
 * walking live in [CrystalTypeHierarchy], `record` support in
 * [CrystalRecordCompletion], lookup builders in [CrystalLookupBuilders].
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
        return CrystalTypeHierarchy.getMethodsFromType(result).filter { isStaticMethod(it) }
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
        if (isStaticMethod(method)) return
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

    /**
     * Finds the `initialize` method of a class/struct (the Crystal constructor).
     */
    fun getInitializeMethod(
        typeName: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): CrystalMethodDefinition? {
        // Fast path: direct lookup by class name — avoids full hierarchy traversal
        directInitialize(typeName, project)?.let { return it }

        // Slow path: resolve type hierarchy (for inherited initialize from parent classes)
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile) ?: return null
        return CrystalTypeHierarchy.getMethodsFromType(result).firstOrNull { it.name == "initialize" }
    }

    /** `initialize` defined directly on [typeName], without hierarchy traversal. */
    private fun directInitialize(
        typeName: String,
        project: Project,
    ): CrystalMethodDefinition? {
        val scope = GlobalSearchScope.allScope(project)
        return StubIndex
            .getElements(
                CrystalMethodByClassIndex.KEY,
                typeName,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            ).firstOrNull { it.name == "initialize" }
    }

    /**
     * Checks whether a method is a class method (def self.xxx).
     */
    fun isStaticMethod(method: CrystalMethodDefinition): Boolean = method.node.findChildByType(CrystalTypes.SELF) != null

    /**
     * Returns the enclosing type name of a method, or null if top-level.
     */
    fun getEnclosingClassName(method: CrystalMethodDefinition): String? {
        val classDef = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
        if (classDef != null) return classDef.name
        val moduleDef = PsiTreeUtil.getParentOfType(method, CrystalModuleDefinition::class.java)
        if (moduleDef != null) return moduleDef.name
        val structDef = PsiTreeUtil.getParentOfType(method, CrystalStructDefinition::class.java)
        if (structDef != null) return structDef.name
        val enumDef = PsiTreeUtil.getParentOfType(method, CrystalEnumDefinition::class.java)
        if (enumDef != null) return enumDef.name
        return null
    }
}
