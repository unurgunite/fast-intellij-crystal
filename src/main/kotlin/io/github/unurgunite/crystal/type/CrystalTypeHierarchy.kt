package io.github.unurgunite.crystal.type

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
     * Qualified names (`Mosquito::Job`) match by short name + enclosing
     * namespace — the class index keys by short name only.
     */
    fun findTypeByName(
        name: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): TypeLookupResult? {
        val scope = CrystalSearchScopes.project(project)
        if (name.contains("::")) {
            CrystalQualifiedLookup.find(name, project, scope, currentFile)?.let { return it }
        }
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

    /** Current-file definition first, then any definition. Internal: shared with qualified lookup. */
    internal fun preferCurrentFile(
        elements: Collection<CrystalNamedElement>,
        currentFile: PsiFile?,
    ): CrystalNamedElement? =
        if (currentFile != null) {
            elements.firstOrNull { it.containingFile?.virtualFile == currentFile.virtualFile }
                ?: elements.firstOrNull()
        } else {
            elements.firstOrNull()
        }

    /** Type kind of a named element, or null for non-type definitions. Internal: shared. */
    internal fun kindOf(element: CrystalNamedElement): TypeKind? =
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
        val scope = CrystalSearchScopes.project(project)
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
            val key = "$name${CrystalMethodLookup.getParameterSignature(method)}"
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
            CrystalParentResolve.enqueue(queue.removeFirst(), project, names, queue)
        }
        return names
    }

    /**
     * Resolved type chain from [name] upward (the type itself first, then
     * namespace-aware parents). Unlike [collectFullHierarchy] (short names
     * only), links carry their PSI elements, so callers can disambiguate
     * same-named classes (`Mosquito::Job` vs `Benchmark::IPS::Job`).
     */
    fun resolveChain(
        name: String,
        project: Project,
        currentFile: PsiFile? = null,
        callSite: PsiElement? = null,
    ): List<TypeLookupResult> {
        val chain = mutableListOf<TypeLookupResult>()
        val seen = mutableSetOf<String>()
        var current = findTypeForCall(name, project, currentFile, callSite)
        while (current != null) {
            // The link itself always belongs to the chain; step() only decides
            // whether the walk continues upward (a parentless type ends here
            // and must still be returned).
            chain.add(current)
            current = CrystalParentResolve.step(current, seen, project)
        }
        return chain
    }

    /**
     * Collects parent class/module names from superclass/include clauses.
     * Handles: class Foo < Bar, class Foo < Bar::Baz, include Bar, extend Bar
     * Internal: shared with CrystalParentResolve.
     */
    internal fun collectParentTypeNames(
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
    internal fun collectSuperclassName(element: CrystalNamedElement): String? {
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
    internal fun collectIncludeName(child: PsiElement): String? {
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

/**
 * Namespace-aware parent resolution (`Mosquito::Job` before bare `Job`) and
 * qualified lookup (`Mosquito::Job` by short name + namespace). Separate
 * object: keeps CrystalTypeHierarchy under TooManyFunctions budget.
 */
internal object CrystalParentResolve {
    /**
     * Parent lookup with namespace priority: `Mosquito::Job` before bare
     * `Job`, qualified name as written before its short form.
     */
    fun find(
        parentName: String,
        current: CrystalNamedElement,
        project: Project,
    ): CrystalTypeHierarchy.TypeLookupResult? {
        if (parentName.contains("::")) {
            CrystalTypeHierarchy
                .findTypeByName(parentName, project)
                ?.let { return it }
            // Relative reference: `Rule::Base` inside `module Lint` means
            // `Lint::Rule::Base`. Prefixing the enclosing namespace keeps
            // such parents inside their own namespace instead of falling
            // through to a bare-name lookup that grabs an unrelated type.
            enclosingNamespaceOf(current)?.let { namespace ->
                CrystalTypeHierarchy.findTypeByName("$namespace::$parentName", project)?.let { return it }
            }
            return CrystalTypeHierarchy.findTypeByName(parentName.substringAfterLast("::"), project)
        }
        enclosingNamespaceOf(current)?.let { namespace ->
            CrystalTypeHierarchy.findTypeByName("$namespace::$parentName", project)?.let { return it }
        }
        // A bare name declared in several namespaces: picking any one of them
        // gives the subclass a foreign hierarchy and a foreign initializer,
        // so staying unresolved beats reporting invented arguments.
        return CrystalTypeHierarchy
            .findTypeByName(parentName, project)
            ?.takeUnless { isAmbiguousTypeName(parentName, project) }
    }

    /**
     * One chain step: records [current]'s qualified name in [seen], returns
     * the resolved parent or null at the top/cycle. Null also stops the
     * loop, so a single break check suffices (detekt jump budget).
     */
    fun step(
        current: CrystalTypeHierarchy.TypeLookupResult,
        seen: MutableSet<String>,
        project: Project,
    ): CrystalTypeHierarchy.TypeLookupResult? {
        val qualified = qualifiedName(current.element) ?: current.element.name ?: return null
        if (!seen.add(qualified)) return null
        val parentName = parentNameOf(current) ?: return null
        if (parentName in seen) return null
        return find(parentName, current.element, project)
    }

    /**
     * One hierarchy level: record the type, queue its not-yet-seen parents.
     * Namespace-first: `class QueuedJob < Job` inside `module Mosquito`
     * means `Mosquito::Job` — resolving bare `Job` first grabbed stdlib
     * `Benchmark::IPS::Job` and poisoned every hierarchy walk with a
     * foreign `initialize` (redis/mosquito `.new` red).
     */
    fun enqueue(
        current: CrystalTypeHierarchy.TypeLookupResult,
        project: Project,
        names: MutableSet<String>,
        queue: ArrayDeque<CrystalTypeHierarchy.TypeLookupResult>,
    ) {
        val typeName = current.element.name ?: return
        if (!names.add(typeName)) return
        val parents = CrystalTypeHierarchy.collectParentTypeNames(current.element, current.kind)
        enqueueUnseen(parents, current, project, names, queue)
    }

    /** Queue parent names not yet seen (single-jump loop body: detekt budget). */
    private fun enqueueUnseen(
        parents: Set<String>,
        current: CrystalTypeHierarchy.TypeLookupResult,
        project: Project,
        names: MutableSet<String>,
        queue: ArrayDeque<CrystalTypeHierarchy.TypeLookupResult>,
    ) {
        for (parentName in parents) {
            if (parentName in names) continue
            find(parentName, current.element, project)?.let { queue.addLast(it) }
        }
    }

    /** First superclass/include name of a chain link, if any. */
    private fun parentNameOf(link: CrystalTypeHierarchy.TypeLookupResult): String? {
        val element = link.element
        val text =
            when (element) {
                is CrystalClassDefinition -> {
                    element.superclassClause
                        ?.typeReference
                        ?.text
                        ?.trim()
                }

                is CrystalStructDefinition -> {
                    element.superclassClause
                        ?.typeReference
                        ?.text
                        ?.trim()
                }

                else -> {
                    null
                }
            }
        if (!text.isNullOrEmpty() && text[0].isUpperCase()) return text
        return null
    }

    /** Qualified name of a type element, short name when unnested. */
    private fun qualifiedName(element: CrystalNamedElement): String? =
        io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
            .buildQualifiedName(element) ?: element.name

    /**
     * Namespace of a type definition (`Mosquito` for `Mosquito::QueuedJob`),
     * from the PSI parent chain. Null for top-level types.
     */
    internal fun enclosingNamespaceOf(element: CrystalNamedElement): String? {
        val qualified =
            io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
                .buildQualifiedName(element) ?: return null
        if (!qualified.contains("::")) return null
        return qualified.substringBeforeLast("::")
    }
}

/**
 * Qualified lookup (`Mosquito::Job`): short-name hits filtered by enclosing
 * namespace. Separate object: keeps CrystalTypeHierarchy under budget.
 */
internal object CrystalQualifiedLookup {
    /** Qualified type hit or null (bare fallback only for file-owned hits). */
    fun find(
        qualified: String,
        project: Project,
        scope: GlobalSearchScope,
        currentFile: PsiFile?,
    ): CrystalTypeHierarchy.TypeLookupResult? {
        // `HK::Controller::ParameterMetadata` -> `Shop::HTTP::...`
        val expanded = CrystalAliasLookup.expand(qualified, project, currentFile) ?: qualified
        val short = expanded.substringAfterLast("::")
        val namespace = expanded.substringBeforeLast("::")
        val elements =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                short,
                project,
                scope,
                CrystalNamedElement::class.java,
            )
        val namespaced = elements.firstOrNull { CrystalParentResolve.enclosingNamespaceOf(it) == namespace }
        if (namespaced != null) {
            val kind = CrystalTypeHierarchy.kindOf(namespaced) ?: return null
            return CrystalTypeHierarchy.TypeLookupResult(namespaced, kind)
        }
        // Bare fallback only for file-owned hits (old behavior for
        // unresolvable namespaces). Otherwise null — no guessing.
        val owned =
            elements.firstOrNull { it.containingFile?.virtualFile == currentFile?.virtualFile }
                ?: return null
        val kind = CrystalTypeHierarchy.kindOf(owned) ?: return null
        return CrystalTypeHierarchy.TypeLookupResult(owned, kind)
    }
}

/**
 * Resolution from a call site: a short name is first tried against the call's
 * own namespace (`Result` inside `module Catalyst` -> `Catalyst::Result`, or a
 * type nested deeper there) before falling back to the index-wide pick.
 * Same-named types from other shards otherwise win arbitrarily and make every
 * named argument of the real constructor look unknown.
 */
private fun findTypeForCall(
    name: String,
    project: Project,
    currentFile: PsiFile?,
    callSite: PsiElement?,
): CrystalTypeHierarchy.TypeLookupResult? {
    if (callSite != null && !name.contains("::")) {
        val hits =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                name,
                project,
                CrystalSearchScopes.project(project),
                CrystalNamedElement::class.java,
            )
        namespacePrefixes(callSite)
            .firstNotNullOfOrNull { prefix ->
                hits.firstOrNull {
                    val namespace = CrystalParentResolve.enclosingNamespaceOf(it)
                    namespace != null && (namespace == prefix || namespace.startsWith("$prefix::"))
                }
            }?.let { element ->
                CrystalTypeHierarchy.kindOf(element)?.let { return CrystalTypeHierarchy.TypeLookupResult(element, it) }
            }
    }
    return CrystalTypeHierarchy.findTypeByName(name, project, currentFile)
}

/**
 * Enclosing namespace paths of [element], innermost first: a call inside
 * `module A; module B; class C` yields `A::B::C`, `A::B`, `A`. Used to try a
 * short type name inside the caller's own namespaces before the index-wide
 * pick. Kept outside [CrystalTypeHierarchy] to stay within the function budget.
 */
private fun namespacePrefixes(element: PsiElement): List<String> {
    val qualified =
        io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
            .buildQualifiedName(element) ?: return emptyList()
    val parts = qualified.split("::")
    return (parts.size downTo 1).map { parts.take(it).joinToString("::") }
}

/**
 * True when a bare [name] is declared in more than one namespace, so no single
 * definition can be chosen for it. Kept outside [CrystalTypeHierarchy] to stay
 * within the object's function budget.
 */
private fun isAmbiguousTypeName(
    name: String,
    project: Project,
): Boolean =
    StubIndex
        .getElements(
            CrystalClassIndex.KEY,
            name,
            project,
            CrystalSearchScopes.project(project),
            CrystalNamedElement::class.java,
        ).count { CrystalTypeHierarchy.kindOf(it) != null } > 1
