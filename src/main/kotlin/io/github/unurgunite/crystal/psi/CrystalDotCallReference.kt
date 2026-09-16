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
import io.github.unurgunite.crystal.stubs.CrystalClassIndex
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
 * 6. Enum member predicates (`color.red?` where `color: Color`, or
 *    `Color::Red.red?`) — Crystal generates a `member?` predicate per enum
 *    constant (`DarkBlue` → `dark_blue?`, verified 1.21.0). Matched textually
 *    against `CrystalEnumConstant` leaves under the resolved enum element, via
 *    [CrystalNameUtils.crystalUnderscore]. Stdlib enums resolve through the text
 *    table instead (`Enum#member?` keys).
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
            resolveClassMember(className, qualifiedName, classNames, info.isStatic, project, scope)?.let { return it }
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
        isStatic: Boolean,
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

        if (matchingByName.isEmpty()) {
            resolveNonMethodMember(className, qualifiedName, classNames, isStatic, project, scope)?.let { return it }
        }

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
     * Non-method members: C bindings (`C.foo` where `fun foo` lives in
     * `lib C`) and type fields (`point.x` where `x : Int32` is declared in
     * the struct body) have no method definitions — but they are direct PSI
     * children with computable offsets, so match them textually under the
     * resolved type element(s). Enum member predicates (`color.red?`) ride
     * along: they are instance methods generated per constant.
     */
    private fun resolveNonMethodMember(
        className: String,
        qualifiedName: String?,
        classNames: List<String>,
        isStatic: Boolean,
        project: Project,
        scope: GlobalSearchScope,
    ): PsiElement? {
        // Try every candidate type element for this class name (unqualified
        // keys may return several: `C` the lib and `C` the class). The
        // qualified-name filter applies to method results; a lib/fun match is
        // unambiguous enough by construction (the text scan keys `Lib#fun`
        // per lib body, and project libs rarely collide).
        //
        // NOTE: `lib` definitions carry NO stubs (lib_definition is not in the
        // stub elementTypeFactory list — stubs exist only for class/module/
        // struct/enum/method/macro/const), so CrystalClassIndex never returns a
        // lib. Instead walk the containing file's PSI for the lib body directly:
        // same-file `lib C` + call site is the overwhelmingly common shape
        // (stdlib `lib` targets resolve through the text table instead).
        val libBody = findLibBodyInFile(className)
        if (libBody != null) {
            matchFunInLibBody(libBody, methodName)?.let { return it }
        }
        val types =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                className,
                project,
                scope,
                CrystalNamedElement::class.java,
            )
        for (type in types) {
            matchMemberInTypeBody(type, methodName)?.let { return it }
            // Enum predicates are instance methods (`color.red?`,
            // `Color::Red.red?`); a bare `Color.red?` is invalid Crystal
            // (`undefined method 'red?' for Color.class`, verified 1.21.0),
            // so skip the constant match for plain static receivers.
            if (!isStatic || qualifiedName != null) {
                matchEnumConstantInTypeBody(type, methodName)?.let { return it }
            }
        }
        return matchEnumValueReceiver(qualifiedName, classNames, project, scope, methodName)
    }

    /**
     * Finds the `lib` body for [className] in the call site's own file (PSI
     * walk, no index — `lib` has no stubs).
     */
    private fun findLibBodyInFile(className: String): PsiElement? {
        val file = element.containingFile ?: return null
        var found: PsiElement? = null
        file.accept(
            object : com.intellij.psi.PsiRecursiveElementVisitor() {
                override fun visitElement(e: PsiElement) {
                    if (found != null) return
                    if (e is CrystalLibDefinition) {
                        val libName =
                            e.node
                                .getChildren(null)
                                .firstOrNull { it.elementType == CrystalTypes.CONSTANT }
                                ?.text
                        if (libName == className) {
                            val body = e.libBody
                            found = body
                            if (body == null) {
                                // No LIB_BODY composite (grammar shape?) — fall back to the
                                // lib element itself; matchFunInLibBody scans statements.
                                found = e
                            }
                            return
                        }
                    }
                    super.visitElement(e)
                }
            },
        )
        return found
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

// Non-method member matching, extracted from [CrystalDotCallReference]
// (file-level to keep the reference class under detekt's TooManyFunctions
// budget). All are pure PSI-leaf matchers: no receiver state, method name
// passed explicitly.

/** `fun` declarations directly inside a LIB_BODY element. Returns the name leaf or null. */
internal fun matchFunInLibBody(
    libBody: PsiElement,
    methodName: String,
): PsiElement? {
    // `fun` sits DIRECTLY under LIB_BODY (no statement wrapper — lib_member
    // is a flat alternative list, verified by probe: bodyKids=[FunDef]).
    val funDefs = ArrayList<CrystalFunDefinition>()
    for (child in libBody.children) {
        if (child is CrystalFunDefinition) funDefs.add(child)
        if (child is CrystalStatement) {
            child.children.filterIsInstanceTo<CrystalFunDefinition, MutableList<CrystalFunDefinition>>(funDefs)
        }
    }
    for (funDef in funDefs) {
        val leaf =
            funDef.node
                .getChildren(null)
                .firstOrNull { it.elementType == CrystalTypes.IDENTIFIER || it.elementType == CrystalTypes.CONSTANT }
                ?.psi
        if (leaf?.text == methodName) return leaf
    }
    return null
}

/**
 * `fun` declarations and field declarations (`x : Type`) directly inside the
 * resolved type element (class/struct/module/lib body). Returns the leaf token
 * so navigation lands on the name, like everywhere else. Null when the type
 * declares no such member.
 */
internal fun matchMemberInTypeBody(
    type: PsiElement,
    methodName: String,
): PsiElement? {
    // Members sit DIRECTLY under CLASS_BODY (struct/class bodies wrap
    // members in CrystalStatement; `x : Type` may also sit bare — collect
    // both, verified by probe on struct Point).
    val body =
        type.children.firstOrNull {
            val t = it.node.elementType.toString()
            t.endsWith("CLASS_BODY")
        } ?: return null
    val members = ArrayList<PsiElement>()
    for (child in body.children) {
        if (child is CrystalPropertyDeclaration || child is CrystalLibField) members.add(child)
        if (child is CrystalStatement) {
            child.children.filterIsInstanceTo<CrystalPropertyDeclaration, MutableList<PsiElement>>(members)
            child.children.filterIsInstanceTo<CrystalLibField, MutableList<PsiElement>>(members)
        }
    }
    for (member in members) {
        matchFieldLeaf(member, methodName)?.let { return it }
    }
    return null
}

/** One field member (`x : Type` / `@x : Type` / lib field) against [methodName]. */
private fun matchFieldLeaf(
    member: PsiElement,
    methodName: String,
): PsiElement? {
    // `x : Type` / `@x : Type` — leaf is the first IDENTIFIER/INSTANCE_VAR.
    val leaf =
        member.node
            .getChildren(null)
            .firstOrNull { it.elementType == CrystalTypes.IDENTIFIER || it.elementType == CrystalTypes.INSTANCE_VAR }
            ?.psi
    if (leaf?.text == methodName || leaf?.text == "@$methodName") return leaf
    // `x : Type` inside `lib` — same shape, different PSI class (CONSTANT names possible).
    val libLeaf =
        member.node
            .getChildren(null)
            .firstOrNull { it.elementType == CrystalTypes.IDENTIFIER || it.elementType == CrystalTypes.CONSTANT }
            ?.psi
    if (libLeaf?.text == methodName) return libLeaf
    return null
}

/**
 * Enum constants in the resolved type element. Crystal generates a `member?`
 * predicate per constant (`Red` → `red?`, `DarkBlue` → `dark_blue?`), so
 * `color.red?` lands on the CONSTANT leaf. Null when the type is not an enum,
 * the name has no `?` suffix, or no constant underscores to the predicate name.
 */
internal fun matchEnumConstantInTypeBody(
    type: PsiElement,
    methodName: String,
): PsiElement? {
    if (!methodName.endsWith("?")) return null
    if (type !is CrystalEnumDefinition) return null
    val body = type.enumBody ?: return null
    for (constant in body.enumConstantList) {
        val leaf =
            constant.node
                .getChildren(null)
                .firstOrNull { it.elementType == CrystalTypes.CONSTANT }
                ?.psi ?: continue
        if (CrystalNameUtils.crystalUnderscore(leaf.text) + "?" == methodName) return leaf
    }
    return null
}

/**
 * Enum-value receiver (`Color::Red.red?`): the namespace walk reports
 * className="Red", qualifiedName="Color::Red", but the predicate lives on
 * the ENCLOSING enum (`Color`). Strips the trailing member segment and
 * matches the predicate inside that enum. Qualified candidates are filtered
 * by their qualified name so `Foo::Color::Red.red?` prefers `Foo::Color`.
 */
internal fun matchEnumValueReceiver(
    qualifiedName: String?,
    classNames: List<String>,
    project: Project,
    scope: GlobalSearchScope,
    methodName: String,
): PsiElement? {
    val enumQualified = enumQualifiedName(qualifiedName, classNames, methodName) ?: return null
    val enumSimple = enumQualified.substringAfterLast("::")
    val candidates =
        StubIndex.getElements(
            CrystalClassIndex.KEY,
            enumSimple,
            project,
            scope,
            CrystalNamedElement::class.java,
        )
    for (candidate in candidates) {
        matchQualifiedEnumConstant(candidate, enumQualified, methodName)?.let { return it }
    }
    return null
}

/** Enclosing-enum qualified name for an enum-value receiver, or null when inapplicable. */
private fun enumQualifiedName(
    qualifiedName: String?,
    classNames: List<String>,
    methodName: String,
): String? {
    if (!methodName.endsWith("?")) return null
    if (classNames.size != 1 || qualifiedName == null) return null
    if (!qualifiedName.contains("::")) return null
    val enumQualified = qualifiedName.substringBeforeLast("::")
    if (enumQualified.isEmpty() || enumQualified == qualifiedName) return null
    return enumQualified
}

/** One enum candidate: qualified-name filter first, then the constant match. */
private fun matchQualifiedEnumConstant(
    candidate: PsiElement,
    enumQualified: String,
    methodName: String,
): PsiElement? {
    if (candidate !is CrystalEnumDefinition) return null
    if (enumQualified.contains("::")) {
        val candidateQualified = CrystalPsiUtils.buildQualifiedName(candidate) ?: return null
        if (candidateQualified != enumQualified) return null
    }
    return matchEnumConstantInTypeBody(candidate, methodName)
}
