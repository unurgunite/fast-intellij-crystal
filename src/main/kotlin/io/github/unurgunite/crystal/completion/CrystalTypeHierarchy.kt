package io.github.unurgunite.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalExtendStatement
import io.github.unurgunite.crystal.psi.CrystalIncludeStatement
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex

/**
 * Type lookup and hierarchy walking for completion: find a type by name,
 * collect its full hierarchy (superclasses, includes) and its methods.
 * Split out of `CrystalCompletionHelper` (which exceeded the function budget).
 */
object CrystalTypeHierarchy {
    /**
     * Result of finding a type definition — carries the PSI element and its kind.
     */
    enum class TypeKind { CLASS, MODULE, STRUCT, ENUM }

    data class TypeLookupResult(
        val element: CrystalNamedElement,
        val kind: TypeKind,
    )

    /**
     * Finds a class/module/struct/enum definition by name.
     * Returns the element and its kind, or null if not found.
     * If currentFile is provided, prefers the definition from that file.
     */
    fun findTypeByName(
        name: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): TypeLookupResult? {
        val scope = GlobalSearchScope.allScope(project)
        val elements =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                name,
                project,
                scope,
                CrystalNamedElement::class.java,
            )
        // Prefer the definition from the current file
        val element = preferCurrentFile(elements, currentFile) ?: return null
        return TypeLookupResult(element, kindOf(element) ?: return null)
    }

    /** Current-file definition first, then any definition. */
    private fun preferCurrentFile(
        elements: Collection<CrystalNamedElement>,
        currentFile: PsiFile?,
    ): CrystalNamedElement? =
        if (currentFile != null) {
            elements.firstOrNull { it.containingFile?.virtualFile == currentFile.virtualFile }
                ?: elements.firstOrNull()
        } else {
            elements.firstOrNull()
        }

    /** Type kind of a named element, or null for non-type definitions. */
    private fun kindOf(element: CrystalNamedElement): TypeKind? =
        when (element) {
            is CrystalClassDefinition -> TypeKind.CLASS
            is CrystalModuleDefinition -> TypeKind.MODULE
            is CrystalStructDefinition -> TypeKind.STRUCT
            is CrystalEnumDefinition -> TypeKind.ENUM
            else -> null
        }

    /**
     * Full hierarchy plus implicit `Object` for classes (Crystal classes always
     * inherit from Object; the hierarchy walk only covers explicit parents).
     */
    fun hierarchyWithImplicitObject(typeResult: TypeLookupResult): List<String> {
        val hierarchy = collectFullHierarchy(typeResult).toMutableList()
        if (typeResult.kind == TypeKind.CLASS && "Object" !in hierarchy) {
            hierarchy.add("Object")
        }
        return hierarchy
    }

    /**
     * Returns all methods belonging to a type, using the stub index.
     * Uses the CrystalMethodByClassIndex for O(1) class→methods lookups
     * instead of scanning the entire method index.
     */
    fun getMethodsFromType(typeResult: TypeLookupResult): List<CrystalMethodDefinition> {
        val project = typeResult.element.project

        // 1. Collect the full type hierarchy (self + parents via inheritance/include)
        val hierarchyNames = collectFullHierarchy(typeResult)

        // 2. For each class in the hierarchy, look up its methods directly via the index
        val scope = GlobalSearchScope.allScope(project)
        val result = mutableListOf<CrystalMethodDefinition>()
        val seen = mutableSetOf<String>()

        for (className in hierarchyNames) {
            addUniqueClassMethods(className, project, scope, seen, result)
        }
        return result
    }

    /** Methods of one hierarchy level, deduplicated by name+signature (overloads kept). */
    private fun addUniqueClassMethods(
        className: String,
        project: Project,
        scope: GlobalSearchScope,
        seen: MutableSet<String>,
        result: MutableList<CrystalMethodDefinition>,
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
            val name = method.name ?: continue
            // Deduplicate by name+signature to keep overloads with different params
            val key = "$name${CrystalLookupBuilders.getParameterSignature(method)}"
            if (seen.add(key)) result.add(method)
        }
    }

    /**
     * Collects the full type hierarchy: the type itself, plus all parents
     * reachable via superclass clauses and include/extend statements.
     */
    fun collectFullHierarchy(typeResult: TypeLookupResult): Set<String> {
        val project = typeResult.element.project
        val names = mutableSetOf<String>()
        val queue = ArrayDeque<TypeLookupResult>()
        queue.addLast(typeResult)

        while (queue.isNotEmpty()) {
            enqueueParents(queue.removeFirst(), project, names, queue)
        }
        return names
    }

    /** One hierarchy level: record the type, queue its not-yet-seen parents. */
    private fun enqueueParents(
        current: TypeLookupResult,
        project: Project,
        names: MutableSet<String>,
        queue: ArrayDeque<TypeLookupResult>,
    ) {
        val typeName = current.element.name ?: return
        if (!names.add(typeName)) return

        for (parentName in collectParentTypeNames(current.element, current.kind)) {
            if (parentName in names) continue
            val parentResult =
                findTypeByName(parentName, project)
                    ?: findTypeByName(parentName.substringAfterLast("::"), project)
            if (parentResult != null) queue.addLast(parentResult)
        }
    }

    /**
     * Collects parent class/module names from superclass/include clauses.
     * Handles: class Foo < Bar, class Foo < Bar::Baz, include Bar, extend Bar
     */
    private fun collectParentTypeNames(
        element: CrystalNamedElement,
        kind: TypeKind,
    ): Set<String> {
        val parentNames = mutableSetOf<String>()
        collectSuperclassName(element)?.let { parentNames.add(it) }

        // Include/Extend from class body: include Bar, extend Bar
        val body: PsiElement =
            when (kind) {
                TypeKind.CLASS -> (element as CrystalClassDefinition).classBody ?: return parentNames
                TypeKind.MODULE -> (element as CrystalModuleDefinition).classBody ?: return parentNames
                TypeKind.STRUCT -> (element as CrystalStructDefinition).classBody ?: return parentNames
                TypeKind.ENUM -> return parentNames
            }

        for (child in body.children) {
            collectIncludeName(child)?.let { parentNames.add(it) }
        }

        return parentNames
    }

    /** Superclass name from a class/struct header (`class Foo < Bar`), if present. */
    private fun collectSuperclassName(element: CrystalNamedElement): String? {
        val superClause =
            when (element) {
                is CrystalClassDefinition -> element.superclassClause
                is CrystalStructDefinition -> element.superclassClause
                else -> null
            } ?: return null
        val text = superClause.typeReference.text.trim()
        return text.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
    }

    /** Uppercase type name of an `include`/`extend` statement child, if any. */
    private fun collectIncludeName(child: PsiElement): String? {
        val typeRef =
            when (child) {
                is CrystalIncludeStatement -> child.typeReference
                is CrystalExtendStatement -> child.typeReference
                else -> null
            } ?: return null
        val text = typeRef.text.trim()
        return text.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
    }
}
