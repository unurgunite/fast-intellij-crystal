package io.github.unurgunite.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.CrystalFile
import io.github.unurgunite.crystal.sdk.CrystalStdlibResolver
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
import io.github.unurgunite.crystal.stubs.CrystalConstantIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex
import java.util.ArrayDeque

/**
 * Reference from an identifier usage to its definition (class/module/struct/enum/method/macro).
 *
 * Resolution order:
 * 1. Local scope (fast — walks up PSI tree, no I/O) — for variables and parameters
 * 2. StubIndex lookup (fast — in-memory index) — for methods, classes, etc.
 *
 * IMPORTANT: Does NOT use CrystalDefinitionFinder.findDefinitions() for anything
 * beyond StubIndex lookups: historically it included a FileTypeIndex fallback
 * that scanned ALL .cr files in the project and walked their PSI trees,
 * causing 90+ second delays on every right-click/hover. The fallback is gone
 * (CrystalDefinitionFinder is StubIndex-only now); this reference never scans
 * the project, only the bounded stdlib cache below.
 * Go to Definition via CrystalGotoDeclarationHandler uses CrystalDefinitionFinder
 * (StubIndex-only) plus the same bounded stdlib cache.
 *
 * A stable location of a stdlib definition: a path relative to the stdlib root plus a character
 * offset. Unlike a PsiElement, a [SymbolLoc] can never go stale when a stdlib file is reparsed,
 * so the PsiElement is materialized fresh on every resolve from the current VFS. File-level
 * (internal) so both [CrystalReference] and [CrystalDotCallReference] can reference it.
 */
internal data class SymbolLoc(
    val relPath: String,
    val offset: Int,
)

class CrystalReference(
    element: PsiElement,
    private val name: String,
    rangeStart: Int,
    rangeLength: Int,
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {
    override fun resolve(): PsiElement? {
        // 1. Local scope fallback (fast — no I/O, walks up PSI tree)
        promoteToOwner(CrystalLocalScopeResolve.resolveLocal(element, name))?.let { return it }

        // 2. StubIndex lookup only (fast — in-memory index, no FileTypeIndex scan).
        // Include the stdlib root explicitly: stdlib symbols come from an
        // AdditionalLibraryRootsProvider (SyntheticLibrary) whose stubs are stored
        // under a scope not covered by allScope, so a plain allScope query misses them.
        resolveProjectStub()?.let { return it }

        // 2b. For a bare method call whose project index missed, prefer a stdlib method
        //     defined on the call site's enclosing class — e.g. `exists?` inside `class File`
        //     should resolve to `File.exists?`, not the first `exists?` in the scanned stdlib
        //     (which would land on `Dir.exists?`). Only applies when the call is a real method
        //     invocation; project methods are already handled by the StubIndex above.
        resolveEnclosingClassStdlibMethod()?.let { return it }

        // 3. Bounded stdlib fallback. Stubs indexed from an AdditionalLibraryRootsProvider
        // (SyntheticLibrary) are stored under an internal scope that StubIndex.getElements
        // cannot query with any GlobalSearchScope (verified: allScope / everythingScope /
        // null all return nothing for stdlib symbols, while getAllKeys still lists them).
        // So when the index misses, fall back to a one-time scan of ONLY the known stdlib
        //    root (bounded, cached per project) — never the whole project, to avoid the
        // 90s FileTypeIndex-style freeze.
        return resolveStdlibSymbol(element.project, name)
    }

    /** Project StubIndex hits: classes, methods, then project constants. */
    private fun resolveProjectStub(): PsiElement? {
        val scope = indexScopeWithStdlib(element.project)
        return firstUsableStub(CrystalClassIndex.KEY, scope, CrystalNamedElement::class.java)
            ?: firstUsableStub(CrystalMethodIndex.KEY, scope, CrystalMethodDefinition::class.java)
            // Constant lookup (project constants only — stdlib constants are handled
            // by the bounded stdlib cache below, since their stubs are skipped).
            ?: firstUsableStub(CrystalConstantIndex.KEY, scope, CrystalConstantAssignment::class.java)
    }

    /** Stdlib method on the call site's enclosing class, if the call sits in one. */
    private fun resolveEnclosingClassStdlibMethod(): PsiElement? {
        val enclosingClass = enclosingClassOfCall(element) ?: return null
        return resolveStdlibMethod(element.project, enclosingClass, name)
    }

    /**
     * If [local] is an IDENTIFIER leaf (not PsiNameIdentifierOwner), promotes it to
     * its parent composite when that implements PsiNameIdentifierOwner. This ensures
     * IntelliJ's rename framework activates (requires element instanceof
     * PsiNameIdentifierOwner in MemberInplaceRenameHandler). Go to Definition still
     * works because getNavigationElement() returns the IDENTIFIER leaf.
     */
    private fun promoteToOwner(local: PsiElement?): PsiElement? {
        if (local == null) return null
        if (local is PsiNameIdentifierOwner) return local
        val parent = local.parent
        if (parent is PsiNameIdentifierOwner) return parent
        return local
    }

    /**
     * Index scope covering the project plus the stdlib root (whose SyntheticLibrary
     * scope plain allScope misses).
     */
    private fun indexScopeWithStdlib(project: com.intellij.openapi.project.Project): GlobalSearchScope {
        val baseScope = GlobalSearchScope.allScope(project)
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return baseScope
        return baseScope.union(GlobalSearchScope.fileScope(project, stdlibRoot))
    }

    /**
     * First `isUsable` stub for [name] under [key], or null when none qualifies.
     */
    private fun <T : PsiElement> firstUsableStub(
        key: com.intellij.psi.stubs.StubIndexKey<String, T>,
        scope: GlobalSearchScope,
        requiredClass: Class<T>,
    ): PsiElement? {
        val found =
            StubIndex.getElements(
                key,
                name,
                element.project,
                scope,
                requiredClass,
            )
        for (candidate in found) {
            if (isUsable(candidate, element.project)) return candidate
        }
        return null
    }

    /**
     * If [element] is the variable-reference of a bare method call (inside a
     * method_call_expression / bare_method_call_expression / bare_command_expression),
     * returns the qualified name of the class/module/struct/enum that lexically encloses
     * the call site. Used to prefer a stdlib method defined on that class (e.g. `exists?`
     * inside `class File` → `File.exists?`) over a same-named method elsewhere.
     * Returns null for non-call references or calls not inside a type definition.
     */
    private fun enclosingClassOfCall(element: PsiElement): String? {
        val isMethodCall =
            element is CrystalMethodCallExpression ||
                element is CrystalBareMethodCallExpression ||
                element is CrystalBareCommandExpression ||
                element is CrystalBareCommandSpaceFirst
        if (!isMethodCall) return null
        val enclosing = CrystalPsiUtils.getEnclosingType(element) ?: return null
        return CrystalPsiUtils.buildQualifiedName(enclosing)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode =
            element.node.findChildByType(CrystalTypes.IDENTIFIER)
                ?: element.node.findChildByType(CrystalTypes.CONSTANT)
                ?: element.node.findChildByType(CrystalTypes.INSTANCE_VAR)
                ?: element.node.findChildByType(CrystalTypes.CLASS_VAR)
                ?: return element

        // Strip any @/@@ prefix the user may have typed, then re-apply from original token type.
        val bareName = newElementName.removePrefix("@").removePrefix("@")
        val fixedName =
            when (identNode.elementType) {
                CrystalTypes.INSTANCE_VAR -> "@$bareName"
                CrystalTypes.CLASS_VAR -> "@@$bareName"
                else -> bareName
            }

        val newLeaf = createLeafFromText(element.project, fixedName, identNode.elementType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        /**
         * A stable location of a stdlib definition: a path relative to the stdlib root plus a
         * character offset. Unlike a PsiElement, a [SymbolLoc] can never go stale when a stdlib
         * file is reparsed, so the PsiElement is materialized fresh on every resolve from the
         * current VFS. This eliminates the multi-second "Resolving reference" freeze that the
         * previous PsiElement-caching design caused whenever a reparse invalidated the cache.
         *
         * Per-project cache of lazily-resolved stdlib symbols, keyed by a qualified name
         * ("Class", "Class::member", or "Class#method"). Stores [SymbolLoc] (stable), never a
         * PsiElement, so the cache is immune to reparse-driven staleness.
         */
        private data class StdlibCache(
            val root: com.intellij.openapi.vfs.VirtualFile,
            val resolved: MutableMap<String, SymbolLoc?>,
        )

        private val stdlibCaches =
            java.util.concurrent.ConcurrentHashMap<Project, StdlibCache>()

        private fun cache(
            project: Project,
            root: com.intellij.openapi.vfs.VirtualFile,
        ): StdlibCache {
            stdlibCaches[project]?.let { if (it.root == root) return it }
            val c = StdlibCache(root, java.util.Collections.synchronizedMap(HashMap<String, SymbolLoc?>()))
            stdlibCaches[project] = c
            return c
        }

        /** Resolve a class/module/struct/enum/constant/method name from the Crystal stdlib to a
         * stable [SymbolLoc] WITHOUT materializing PSI. No-PSI variant of [resolveStdlibSymbol],
         * used by the reference-graph harness. */
        internal fun resolveStdlibSymbolLoc(
            project: Project,
            name: String,
        ): SymbolLoc? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibSymbolLoc(project, root, name)
        }

        /** Resolve a class/module/struct/enum/constant/method name from the Crystal stdlib
         * (materializes the target PsiElement). See [resolveStdlibSymbolLoc] for the no-PSI variant. */
        fun resolveStdlibSymbol(
            project: Project,
            name: String,
        ): PsiElement? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibSymbolLoc(project, root, name)?.let { CrystalStdlibFileResolve.materialize(project, root, it) }
        }

        private fun computeStdlibSymbolLoc(
            project: Project,
            root: com.intellij.openapi.vfs.VirtualFile,
            name: String,
        ): SymbolLoc? {
            val c = cache(project, root)
            if (c.resolved.containsKey(name)) return c.resolved[name]
            // Precise, per-file resolution (class/constant or "Class::member").
            val precise = CrystalStdlibFileResolve.resolveByName(project, root, name)
            if (precise != null) {
                c.resolved[name] = precise
                return precise
            }
            // Fallback: a one-time bounded text scan of the stdlib root. Needed for
            // bare lowercase method names (e.g. `puts`) that have no home-file
            // convention, and for constants the precise per-file lookup cannot
            // locate — either because the name has no canonical file
            // (DEFAULT_CREATE_PERMISSIONS lives in file.cr) or because the
            // definition is nested in a subdirectory (Math::PI lives in
            // math/math.cr). Built once (text scan, no PSI parse — sub-second),
            // then cached per project.
            val loc = CrystalStdlibTextScan.globalStdlibData(project, root).symbols[name]
            if (loc != null) {
                c.resolved[name] = loc
                return loc
            }
            c.resolved[name] = null
            return null
        }

        /**
         * Returns true only if [el] is a live PsiElement belonging to [project].
         * A detached element from a reparsed stdlib file reports a stale
         * cross-provider FileViewProvider and throws [PsiInvalidElementAccessException]
         * on `containingFile` access; we treat that as unusable. (Used only for the
         * project StubIndex results above; the stdlib cache no longer stores
         * PsiElements, so it needs no staleness handling.)
         */
        private fun isUsable(
            el: PsiElement?,
            project: Project,
        ): Boolean {
            if (el == null) return false
            return try {
                el.containingFile?.virtualFile != null && el.manager.project == project
            } catch (_: com.intellij.psi.PsiInvalidElementAccessException) {
                false
            }
        }

        /** No-op: resolution is lazy and per-file, so there is nothing to warm up. */
        @Suppress("EmptyFunctionBlock")
        fun warmStdlibCache() {}

        /** Expose the bounded stdlib text-symbol table (name → stable [SymbolLoc]) for the
         * reference-graph harness, so it can classify references without materializing PSI. */
        internal fun getStdlibSymbolTable(project: Project): Map<String, SymbolLoc> {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return emptyMap()
            return CrystalStdlibTextScan.globalStdlibData(project, root).symbols
        }

        /** Build a stable [SymbolLoc] for an already-resolved PsiElement (e.g. a project StubIndex
         * hit) relative to the stdlib [root]. Returns null when the element is outside the root. */
        internal fun locOf(
            element: PsiElement,
            root: com.intellij.openapi.vfs.VirtualFile,
        ): SymbolLoc? {
            val cf = element.containingFile as? CrystalFile ?: return null
            val vf = cf.virtualFile ?: return null
            val relPath =
                com.intellij.openapi.vfs.VfsUtilCore
                    .getRelativePath(vf, root) ?: return null
            val owner = element as? PsiNameIdentifierOwner
            val ident = owner?.nameIdentifier
            val offset = ident?.textOffset ?: element.textOffset
            return SymbolLoc(relPath, offset)
        }

        /** Resolve a `ClassName#method` from the stdlib to a stable [SymbolLoc] WITHOUT
         * materializing PSI. Never returns a method belonging to a different class, so project
         * classes (handled by the StubIndex) and coincidentally-named stdlib symbols are never
         * hijacked. Used by the reference-graph harness for a fast (no-PSI) whole-stdlib scan.
         *
         * [className] may be a fully-qualified name (e.g. `Crystal::System::Dir`) — the precise
         * per-file lookup then uses the namespace's home file, and on a miss the bounded stdlib
         * text scan (keyed by `Class#method`) is consulted so nested-class methods resolve. */
        internal fun resolveStdlibMethodLoc(
            project: Project,
            className: String,
            methodName: String,
        ): SymbolLoc? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibMethodLoc(project, root, className, methodName)
        }

        /** Resolve a `ClassName#method` from the stdlib, precisely and fast (materializes the
         * target PsiElement). See [resolveStdlibMethodLoc] for the no-PSI variant. */
        fun resolveStdlibMethod(
            project: Project,
            className: String,
            methodName: String,
        ): PsiElement? {
            val root = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
            return computeStdlibMethodLoc(project, root, className, methodName)?.let {
                CrystalStdlibFileResolve.materialize(project, root, it)
            }
        }

        private fun computeStdlibMethodLoc(
            project: Project,
            root: com.intellij.openapi.vfs.VirtualFile,
            className: String,
            methodName: String,
        ): SymbolLoc? {
            val key = "$className#$methodName"
            val c = cache(project, root)
            if (c.resolved.containsKey(key)) return c.resolved[key]
            var result = CrystalStdlibFileResolve.resolveMemberInFile(project, root, className, methodName)
            if (result == null) {
                // Bounded fallback: the text scan keys methods by qualified `Class#method`
                // (see buildStdlibData), covering classes whose definition lives in a subfile of
                // the namespace home — e.g. `Crystal::System::Dir#info` lives in
                // crystal/system/unix/dir.cr, not a `Dir.cr`.
                result = CrystalStdlibTextScan.globalStdlibData(project, root).symbols[key]
            }
            c.resolved[key] = result
            return result
        }
    }
}
