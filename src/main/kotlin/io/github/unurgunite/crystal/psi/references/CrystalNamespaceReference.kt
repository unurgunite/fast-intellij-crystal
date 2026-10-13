package io.github.unurgunite.crystal.psi.references

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalNamespaceAccess
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.stubs.CrystalClassIndex

/**
 * Reference from a `namespace_access` element (e.g. `::Unterklasse` in `Oberklasse::Unterklasse`)
 * to the class/module/struct/enum definition.
 *
 * Reconstructs the full namespace path by walking left through prevSibling elements:
 * - `Oberklasse::Unterklasse` → looks up `"Oberklasse::Unterklasse"` then falls back to `"Unterklasse"`
 * - `A::B::C` → looks up `"A::B::C"` then falls back to `"C"`
 * - `::Foo` → looks up `"Foo"` (no preceding namespace part)
 *
 * The fallback to the simple name handles lexically-nested classes (e.g. `class A; class B; end; end`)
 * where `CrystalClassIndex` is keyed by the simple name `"B"`, not the full path.
 *
 * A rooted path (leading `::`, e.g. `::DB` inside `module DB`) resolves from the
 * project root + stdlib only: lexically-enclosing types with the same simple name
 * must NOT match. When no root candidate exists (e.g. an unindexed shard under
 * `lib/`, which StubIndex never sees), resolution returns null — no false jump.
 */
class CrystalNamespaceReference(
    element: PsiElement,
    private val simpleName: String,
    rangeStart: Int,
    rangeLength: Int,
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {
    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)
        val fullName = buildFullName()
        val isGlobal = isRooted()

        // 1. Try full path first (for namespace-defined classes: `class A::B`).
        //    Rooted paths skip local candidates (see [isLocalCandidate]); a lone
        //    rooted name (`::Foo`) additionally takes top-level candidates only.
        val byFullName =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                fullName,
                project,
                scope,
                CrystalNamedElement::class.java,
            )
        firstNonLocal(byFullName, isGlobal, fullName == simpleName)?.let { return it }

        // 2. Fall back to filtered simple-name lookup (for lexically-nested classes).
        //    Filter by qualified name to disambiguate: Foo::Sub vs Bar::Sub.
        if (fullName != simpleName) {
            val candidates =
                StubIndex.getElements(
                    CrystalClassIndex.KEY,
                    simpleName,
                    project,
                    scope,
                    CrystalNamedElement::class.java,
                )
            val filtered =
                candidates
                    .filter { candidate ->
                        CrystalPsiUtils.buildQualifiedName(candidate) == fullName &&
                            (!isGlobal || !isLocalCandidate(candidate))
                    }.firstOrNull()
            if (filtered != null) return filtered
        }

        // 3. Simple name only (e.g., `::Foo` — no preceding path). Rooted paths
        //    take only top-level, non-local candidates: `::DB` inside `module DB`
        //    must not land on the enclosing module, and `::String` must not land
        //    on a nested `M::String`. Unindexed shards (`lib/` is not indexed)
        //    yield no candidate → null, no false jump.
        firstNonLocal(
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                simpleName,
                project,
                scope,
                CrystalNamedElement::class.java,
            ),
            isGlobal,
            requireTopLevel = fullName == simpleName,
        )?.let { return it }

        // 4. Stdlib fallback. Constants (e.g. `Crystal::VERSION`) and macros are not in
        //    the StubIndex (their roots live under an internal SyntheticLibrary scope), so
        //    fall back to the cached stdlib scan. Prefer the full qualified name, then the
        //    simple name (matches the class-index fallback above).
        val stdlibFull = CrystalReference.resolveStdlibSymbol(project, fullName)
        if (stdlibFull != null) return stdlibFull
        return CrystalReference.resolveStdlibSymbol(project, simpleName)
    }

    /**
     * First candidate, or — for rooted paths — the first candidate that is not
     * local to the reference site. For a lone rooted name (`::Foo`,
     * [requireTopLevel]) only top-level candidates qualify: a nested `M::Foo`
     * is never the root `Foo`. Null when nothing qualifies (e.g. the target
     * lives in an unindexed shard).
     */
    private fun firstNonLocal(
        candidates: Collection<CrystalNamedElement>,
        isGlobal: Boolean,
        requireTopLevel: Boolean = false,
    ): CrystalNamedElement? {
        if (candidates.isEmpty()) return null
        if (!isGlobal) return candidates.first()
        return candidates.firstOrNull { !isLocalCandidate(it) && (!requireTopLevel || isTopLevel(it)) }
    }

    /** True when [candidate] is defined at the project root (no enclosing type). */
    private fun isTopLevel(candidate: PsiElement): Boolean = CrystalPsiUtils.getEnclosingType(candidate.parent) == null

    /**
     * True when [candidate] (or one of its enclosing types) lexically encloses
     * the reference site: e.g. `module DB` enclosing a `::DB` usage, or the
     * `class Database` nested in it enclosing a `::DB::Database` usage.
     * Rooted paths must skip such candidates and resolve from the project
     * root + stdlib instead.
     */
    private fun isLocalCandidate(candidate: PsiElement): Boolean {
        var current: PsiElement? = candidate
        while (current != null) {
            if (PsiTreeUtil.isAncestor(current, element, false)) return true
            current = CrystalPsiUtils.getEnclosingType(current.parent)
        }
        return false
    }

    /**
     * Walks left through prevSibling elements to reconstruct the full namespace path.
     * Collects CONSTANT names from preceding [CrystalNamespaceAccess] and
     * [CrystalVariableReference] elements, joining them with `::`.
     *
     * Example: for `A::B::C`, when called on the `::C` element, returns `"A::B::C"`.
     */
    private fun buildFullName(): String {
        val parts = mutableListOf(simpleName)
        var current: PsiElement? = element.prevSibling

        while (current != null && collectNamespacePart(current, parts)) {
            current = current.prevSibling
        }

        return parts.joinToString("::")
    }

    /**
     * True when the path is rooted (leading `::`): the chain head is a
     * [CrystalNamespaceAccess] or nothing precedes (lone `::Foo`). A
     * [CrystalVariableReference] head (`Foo` in `Foo::Sub`) is relative.
     * Every `::Seg` is its own `namespace_access` composite (grammar:
     * `namespace_access ::= DOUBLE_COLON CONSTANT`).
     */
    private fun isRooted(): Boolean {
        val head = chainHead(element.prevSibling)
        return head !is CrystalVariableReference
    }

    /**
     * Collects one CONSTANT segment from [current] into [parts].
     * Returns false at the path start (leading variable reference) or at any
     * non-namespace element, ending the walk without a jump statement.
     */
    private fun collectNamespacePart(
        current: PsiElement,
        parts: MutableList<String>,
    ): Boolean {
        if (current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE) return true
        if (current is CrystalNamespaceAccess) {
            // Another namespace_access — get its CONSTANT
            current.node
                .findChildByType(CrystalTypes.CONSTANT)
                ?.text
                ?.let { parts.add(0, it) }
            return true
        }
        if (current is CrystalVariableReference) {
            // The leading variable_reference — get its CONSTANT, then stop
            current.node
                .findChildByType(CrystalTypes.CONSTANT)
                ?.text
                ?.let { parts.add(0, it) }
        }
        return false
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val constantNode = element.node.findChildByType(CrystalTypes.CONSTANT) ?: return element
        val newLeaf = createLeafFromText(element.project, newElementName) ?: return element
        constantNode.treeParent.replaceChild(constantNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private fun createLeafFromText(
            project: com.intellij.openapi.project.Project,
            text: String,
        ): ASTNode? {
            val file =
                PsiFileFactory
                    .getInstance(project)
                    .createFileFromText("dummy.cr", io.github.unurgunite.crystal.CrystalLanguage, text)
            return file.firstChild?.node?.firstChildNode
        }
    }
}

/**
 * Leftmost namespace-chain segment from [start], skipping whitespace.
 * Null when nothing precedes (lone `::Foo`) or at any non-namespace element.
 */
private fun chainHead(start: PsiElement?): PsiElement? {
    var current = start
    var head: PsiElement? = null
    while (current != null && (isSkipped(current) || isChainSegment(current))) {
        if (isChainSegment(current)) head = current
        current = current.prevSibling
    }
    return head
}

/** Whitespace between namespace segments — skipped, never a chain head. */
private fun isSkipped(element: PsiElement): Boolean = element is PsiWhiteSpace || element.node?.elementType == CrystalTypes.NEWLINE

/** One `::Seg` of the chain: a namespace_access or the leading variable reference. */
private fun isChainSegment(element: PsiElement): Boolean = element is CrystalNamespaceAccess || element is CrystalVariableReference
