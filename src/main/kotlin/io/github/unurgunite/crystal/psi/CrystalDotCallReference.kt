package io.github.unurgunite.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.completion.CrystalRecordCompletion
import io.github.unurgunite.crystal.completion.CrystalTypeInference
import io.github.unurgunite.crystal.inspections.CrystalExpressionTypeResolver
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex

/**
 * Reference from a DOT-call method-name identifier to its definition.
 *
 * Resolution rules (no false-positive name-only guessing):
 *
 * 1. CONSTANT receiver (e.g. `Apfel.tanzen`, `Senf.new`) — resolve via
 *    [CrystalMethodByClassIndex] keyed by the receiver (class/module/struct/enum
 *    name). Filters results by method name. This is exact — no guessing.
 *
 * 2. Namespace receiver (e.g. `Foo::Sub.space`) — walks left through
 *    [CrystalNamespaceAccess] and [CrystalVariableReference] to reconstruct the
 *    full qualified name, resolves via CrystalClassIndex, then filters methods
 *    by the enclosing class's qualified name to avoid ambiguity (Foo::Sub vs Bar::Sub).
 *
 * 3. IDENTIFIER receiver (e.g. `a.essen` where `a = Apfel.new`):
 *    - Call [CrystalTypeInference.inferType] on the receiver variable name.
 *    - If a concrete type is returned, resolve via [CrystalMethodByClassIndex]
 *      keyed by the inferred type, filtering by method name.
 *    - If the type is **unknown** (untyped parameter, untyped return chain, …),
 *      return `null` — no jump, no false-positive popup.
 *
 * 4. `.new` constructor on a class — resolved via [CrystalMethodByClassIndex] for
 *    `def self.new` if it exists. If not found, falls through to `record` macro,
 *    then to `def initialize` via [CrystalCompletionHelper.getInitializeMethod].
 *    This makes Find Usages on both `.new` and `initialize` work correctly.
 *
 * 5. Project-scoped name-only fallback — when the receiver type is unknown (bare
 *    untyped local/parameter), resolve by method name within the PROJECT ONLY
 *    (never the stdlib) so local navigation works (`rule.auto_fixable?`) without
 *    the cross-project false-positive popups the strict design otherwise forbids.
 *
 * The receiver is found by walking `prevSibling` (skipping whitespace/NLS) from
 * this element — the DOT is the first child of [CrystalDotCallAccess], so the
 * receiver is the preceding sibling in the flattened `postfix_expression` sequence.
 */
class CrystalDotCallReference(
    element: PsiElement,
    private val methodName: String,
    rangeStart: Int,
    rangeLength: Int,
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {
    private val receiverInfo: ReceiverInfo? by lazy { CrystalDotCallReceiver.resolve(element) }

    override fun resolve(): PsiElement? {
        val info = receiverInfo ?: return null
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        val qualifiedName = info.qualifiedName
        val classNames = info.classNames

        // Iterate over every inferred member type (unions: `Int32 | Nil` resolve methods on
        // both members). Namespace disambiguation via qualifiedName only applies when there is
        // a single member type.
        for (className in classNames) {
            resolveClassMember(className, qualifiedName, classNames, project, scope)?.let { return it }
            resolveStdlibMember(className, qualifiedName, classNames, project)?.let { return it }
            resolveConstructor(className, project)?.let { return it }
        }

        return resolveUnknownReceiverFallback(info, project, scope)
    }

    /**
     * Exact class-based lookup (CONSTANT / namespace / inferred-type receivers),
     * narrowed by qualified name for single-member namespace receivers.
     */
    private fun resolveClassMember(
        className: String,
        qualifiedName: String?,
        classNames: List<String>,
        project: com.intellij.openapi.project.Project,
        scope: GlobalSearchScope,
    ): PsiElement? {
        val methods =
            StubIndex.getElements(
                CrystalMethodByClassIndex.KEY,
                className,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        val matchingByName = methods.filter { it.name == methodName }

        // Filter by qualified class name if available (for namespace disambiguation)
        if (qualifiedName == null || classNames.size != 1) {
            return matchingByName.firstOrNull()
        }
        return matchingByName
            .filter { method ->
                val enclosing = CrystalPsiUtils.getEnclosingType(method)
                enclosing != null && CrystalPsiUtils.buildQualifiedName(enclosing) == qualifiedName
            }.firstOrNull()
    }

    /**
     * Stdlib fallback. StubIndex cannot resolve stdlib classes/methods (their roots
     * are stored under an internal scope no GlobalSearchScope intersects), so when the
     * index misses for a known receiver class, fall back to the cached stdlib scan.
     * Prefers the fully-qualified class name (e.g. `Crystal::System::Dir`) so nested
     * classes resolve to the right file instead of a same-named simple class.
     */
    private fun resolveStdlibMember(
        className: String,
        qualifiedName: String?,
        classNames: List<String>,
        project: com.intellij.openapi.project.Project,
    ): PsiElement? {
        val lookupClass = if (classNames.size == 1 && qualifiedName != null) qualifiedName else className
        return CrystalReference.resolveStdlibMethod(project, lookupClass, methodName)
    }

    /**
     * `.new` constructor: record → initialize → stdlib symbol.
     * (Matches CrystalGotoDeclarationHandler priority: def self.new > record > initialize.)
     */
    private fun resolveConstructor(
        className: String,
        project: com.intellij.openapi.project.Project,
    ): PsiElement? {
        // For .new: fall through to record → initialize resolution
        if (methodName != "new") return null
        val file = element.containingFile ?: return null
        val recordDef = CrystalRecordCompletion.findRecordDefinition(className, file)
        if (recordDef != null) return recordDef
        val init = CrystalCompletionHelper.getInitializeMethod(className, project, file)
        if (init != null) return init
        return CrystalReference.resolveStdlibSymbol(project, className)
    }

    /**
     * Project-scoped name-only fallback for INSTANCE DOT-calls whose receiver type is
     * unknown (untyped local/parameter, e.g. `rule.auto_fixable?` where `rule` is a bare
     * parameter). Resolves by method name within the PROJECT ONLY (never the stdlib), so
     * we enable local navigation without the cross-project false-positive popups the
     * strict design otherwise forbids. Returns the first project match; ambiguity across
     * multiple same-named methods is acceptable for navigation (they are semantically
     * equivalent definitions of the same message).
     */
    private fun resolveUnknownReceiverFallback(
        info: ReceiverInfo,
        project: com.intellij.openapi.project.Project,
        scope: GlobalSearchScope,
    ): PsiElement? {
        if (info.classNames.isNotEmpty()) return null
        // CrystalMethodIndex (StubIndex) contains no stdlib methods — stdlib roots live
        // under a SyntheticLibrary scope that no GlobalSearchScope intersects (see
        // resolveStdlibMember above). Querying it therefore cannot produce stdlib
        // false positives; the only possible ambiguity is between same-named project
        // methods, which is acceptable for local navigation.
        val projectMethods =
            StubIndex.getElements(
                CrystalMethodIndex.KEY,
                methodName,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        return projectMethods.firstOrNull()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode =
            element.node.findChildByType(CrystalTypes.IDENTIFIER)
                ?: element.node.findChildByType(CrystalTypes.CONSTANT)
                ?: return element
        val newLeaf =
            createLeafFromText(element.project, newElementName, identNode.elementType)
                ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
