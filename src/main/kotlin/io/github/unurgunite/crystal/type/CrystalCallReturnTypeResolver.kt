package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver.ResolvedType

/**
 * Return-type lookup for method call expressions: `Klasse.new` yields the class,
 * `Klasse.method` / bare `method` look up the annotated return type project-wide.
 */
object CrystalCallReturnTypeResolver {
    fun resolveMethodCallReturnType(expr: PsiElement): ResolvedType? {
        val text = expr.text.trim()

        // Pattern: Klasse.new(...) → type is "Klasse". Leading `::` tolerated
        // (`::Set(String).new` — redis cluster.cr); generic args kept
        // (`Set(String)`, not bare `Set`).
        val newPattern = Regex("""^(?:::)?([A-Z]\w*(?:::\w+)*)(\([^)]*\))?\.new(?:\(.*\))?$""", RegexOption.DOT_MATCHES_ALL)
        val newMatch = newPattern.find(text)
        if (newMatch != null) {
            val args = newMatch.groupValues[2]
            return ResolvedType(newMatch.groupValues[1] + args)
        }

        // Pattern: <expr>.to_json → "String (to_json of <receiver type>)".
        // The marker lets compatibility accept collections serialized at the
        // call site (`rc_post(path, hash.to_json)` vs `body : String`) while
        // bare collections still mismatch (`foo([1, 2])` vs `s : String`).
        resolveToJsonReturnType(expr)?.let { return it }

        // Pattern: Klasse.method(...) → look up return type
        resolveClassMethodReturnType(text, expr.project)?.let { return it }

        // Pattern: method_name(...) → look up return type
        val methodName = extractMethodName(expr)
        if (methodName != null && methodName[0].isLowerCase()) {
            val returnType = lookupReturnType(methodName, expr.project)
            if (returnType != null) return ResolvedType(returnType)
        }

        return null
    }

    /**
     * `<receiver>.to_json` (parens or bare): String with the receiver type
     * threaded through for the `.to_json` compatibility boundary. Receiver
     * resolved from the call's own PSI (the DOT_CALL_ACCESS receiver child),
     * never from re-parsed text; unknown receiver falls back to plain String
     * (still correct — `to_json` yields String for every receiver).
     */
    private fun resolveToJsonReturnType(expr: PsiElement): ResolvedType? {
        val methodName = extractMethodName(expr) ?: return null
        if (methodName != "to_json") return null
        val receiver = dotCallReceiver(expr) ?: return ResolvedType("String")
        val receiverType = CrystalExpressionTypeResolver.resolveType(receiver)?.typeName ?: return ResolvedType("String")
        return ResolvedType("String (to_json of $receiverType)")
    }

    /** Receiver child of a DOT-call expression (`recv` in `recv.method(args)`), if any. */
    private fun dotCallReceiver(expr: PsiElement): PsiElement? {
        var child = expr.firstChild
        while (child != null) {
            if (child.node?.elementType == CrystalTypes.DOT) {
                var prev = child.prevSibling
                while (prev is com.intellij.psi.PsiWhiteSpace) prev = prev.prevSibling
                return prev
            }
            child = child.nextSibling
        }
        return null
    }

    /** `Klasse.method(...)` return type, or null when the text is not a class-method call. */
    private fun resolveClassMethodReturnType(
        text: String,
        project: Project,
    ): ResolvedType? {
        val classMethodPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.(\w+)(?:\(.*\))?$""", RegexOption.DOT_MATCHES_ALL)
        val classMethodMatch = classMethodPattern.find(text) ?: return null
        val className = classMethodMatch.groupValues[1]
        val methodName = classMethodMatch.groupValues[2]
        if (methodName == "new") return ResolvedType(className)
        // Class-scoped first (`URI.parse` must take URI.parse, not a
        // same-named overload from another class): the project-wide
        // first hit ignores the receiver class entirely.
        lookupReturnTypeInClass(methodName, className.substringAfterLast("::"), project)?.let {
            return ResolvedType(it)
        }
        val returnType = lookupReturnType(methodName, project) ?: return null
        return ResolvedType(returnType)
    }

    private fun extractMethodName(expr: PsiElement): String? {
        val child = expr.firstChild
        if (child?.node?.elementType == CrystalTypes.IDENTIFIER) return child.text
        if (child?.node?.elementType == CrystalTypes.CONSTANT) return child.text
        return null
    }

    fun lookupReturnType(
        methodName: String,
        project: Project,
    ): String? {
        // Fixed-yield conversions, same family as `to_s → String`: the index
        // walk below would otherwise return the receiver's own type from the
        // first unannotated overload it meets (`ENV.fetch(...).to_i` typed
        // as String instead of Int32).
        FIXED_RETURN_TYPES[methodName]?.let { return it }
        val indexes = projectReturnTypes(project)
        if (indexes.byName.containsKey(methodName)) return indexes.byName[methodName]
        val computed =
            StubIndex
                .getElements(
                    CrystalMethodIndex.KEY,
                    methodName,
                    project,
                    CrystalSearchScopes.project(project),
                    CrystalMethodDefinition::class.java,
                ).firstNotNullOfOrNull { firstAnnotatedReturnType(it) }
        indexes.byName[methodName] = computed
        return computed
    }

    /**
     * Per-project memo of [lookupReturnType] / [lookupReturnTypeInClass]
     * results.
     *
     * Uncached, each call ran a project-wide `CrystalMethodIndex` query and
     * materialised PSI for *every* same-named overload, so a single file's
     * analysis cost grew with the number of same-named methods anywhere in
     * the project — measured 12x from 1 to 1001 files for one name, and a
     * 74-line file took 96s per highlighting pass once the index was warm.
     *
     * Only the resulting type string is stored (never PSI), so the memo
     * cannot pin or go stale, and one index walk per name serves every call
     * site. Misses are cached too: the negative case is the common one.
     * Map is mutable and filled lazily, so a cold start does not pay for
     * method names nothing ever asks about.
     */
    private fun projectReturnTypes(project: Project): ReturnTypeIndexes =
        CachedValuesManager.getManager(project).getCachedValue(
            project,
            RETURN_TYPE_INDEXES_KEY,
            {
                CachedValueProvider.Result.create(
                    ReturnTypeIndexes(HashMap(), HashMap()),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            false,
        )

    private data class ReturnTypeIndexes(
        /** Method name → annotated return type, null when none found. */
        val byName: MutableMap<String, String?>,
        /** `ClassName#methodName` → annotated return type in that class. */
        val byClass: MutableMap<String, String?>,
    )

    private val RETURN_TYPE_INDEXES_KEY =
        com.intellij.openapi.util.Key.create<CachedValue<ReturnTypeIndexes>>(
            "crystal.call.return.types",
        )

    /**
     * Conversion methods with a fixed yield regardless of receiver
     * (`Object#to_s`, `String#to_i`, ...). Table-driven: one lookup covers
     * the family without per-name branches. Public for the fragment
     * resolver (fixed yields precede receiver scoping).
     */
    internal fun fixedReturnType(methodName: String): String? = FIXED_RETURN_TYPES[methodName]

    private val FIXED_RETURN_TYPES =
        mapOf(
            "to_s" to "String",
            "to_i" to "Int32",
            "to_i8" to "Int8",
            "to_i16" to "Int16",
            "to_i32" to "Int32",
            "to_i64" to "Int64",
            "to_i128" to "Int128",
            "to_u8" to "UInt8",
            "to_u16" to "UInt16",
            "to_u32" to "UInt32",
            "to_u64" to "UInt64",
            "to_u128" to "UInt128",
            "to_f" to "Float64",
            "to_f32" to "Float32",
            "to_f64" to "Float64",
        )

    /**
     * Return type of [methodName] defined inside [className] (via the
     * class→methods index). [className] may be qualified (`HTTP::Params`):
     * the index keys by short name, so short hits are filtered by qualified
     * enclosing, short scope second. Null when the class defines no such
     * method — the caller falls back to the project-wide first hit. Scopes
     * receiver-typed DOT-calls (`data.to_hash` with data: PersonData) to
     * the receiver's own method instead of a same-named stdlib generic.
     */
    fun lookupReturnTypeInClass(
        methodName: String,
        className: String,
        project: Project,
    ): String? {
        FIXED_RETURN_TYPES[methodName]?.let { return it }
        val indexes = projectReturnTypes(project)
        val key = "$className#$methodName"
        if (indexes.byClass.containsKey(key)) return indexes.byClass[key]
        val short = className.substringAfterLast("::")
        val methods =
            StubIndex
                .getElements(
                    io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex.KEY,
                    short,
                    project,
                    CrystalSearchScopes.project(project),
                    CrystalMethodDefinition::class.java,
                ).filter { it.name == methodName }
        // Qualified scope first (`HTTP::Params.parse` must take Params under
        // HTTP, not another `Params.parse` elsewhere), then any same-named
        // definition of that class.
        val ordered = if (className.contains("::")) methods.sortedBy { ownerQualifiedOf(it) != className } else methods
        val resolved = ordered.firstNotNullOfOrNull { firstAnnotatedReturnType(it) }
        indexes.byClass[key] = resolved
        return resolved
    }

    /** Qualified enclosing type of a method via its stub tree (null when stub-less/top-level). */
    private fun ownerQualifiedOf(method: CrystalMethodDefinition): String? {
        val stub = (method as? com.intellij.psi.StubBasedPsiElement<*>)?.stub ?: return null
        val parent = stub.parentStub ?: return null
        return io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
            .buildQualifiedNameFromStub(parent)
            ?.takeIf { it.contains("::") || it.isNotEmpty() }
    }

    /** First union branch of a method's return annotation, generics kept. */
    private fun firstAnnotatedReturnType(method: CrystalMethodDefinition): String? {
        val returnType = method.typeReference?.text ?: return null
        // Full annotation text, generics included (`Hash(String, String)`):
        // typeReference holds only the return annotation (never parameter
        // lists), so parens are generic args, not noise. Stripping them
        // collapsed `Hash(String, String)` to bare `Hash` and broke
        // generic-argument compatibility downstream. Union split is
        // depth-aware: naive split("|") cut `Hash(K | L, V | W)` (stdlib
        // `Hash#merge`) inside its parens into `Hash(K`, poisoning every
        // downstream check with a truncated type. Crystal resolves
        // left-to-right, so the first whole branch wins.
        return CrystalMethodReturnTypeInference.splitTypeNames(returnType).firstOrNull()?.trim()
    }
}

/**
 * Direct textual receiver names (no type resolution): CONSTANT-adjacent
 * uppercase references and qualified namespace fragments. Separate object:
 * keeps CrystalExpressionTypeResolver under the per-file function budget.
 */
internal object CrystalReceiverNames {
/**
     * Direct textual receiver name without type resolution: CONSTANT tokens,
     * namespace fragments (`::Params` glued with the left base sibling), and
     * uppercase single-token references (`URI`). Null when resolution is
     * needed (lowercase variables). File-level: budget for both parents.
     */
    internal fun direct(receiver: PsiElement): String? {
        val refText = receiver.text.trim()
        // Namespace fragment (`::Params` in `HTTP::Params.parse`): glue the
        // base sibling on the left before any other check (`:` is not
        // uppercase, so the branch below would miss it entirely).
        if (refText.startsWith("::")) return qualifyWithBase(receiver, refText)
        // Uppercase single-token reference (`URI` parses as VARIABLE_REFERENCE,
        // not a CONSTANT token — same meaning for scoping).
        if (refText.isNotEmpty() && refText[0].isUpperCase() && refText.none { it.isWhitespace() }) {
            return refText.substringAfterLast("::")
        }
        return null
    }

/**
     * Qualified path for a namespace fragment (`::Params` in
     * `HTTP::Params.parse`): glues the base sibling on the left
     * (`HTTP` + `::Params`). Plain `URI`-style tokens pass through.
     * File-level: keeps receiverClassNameOf under complexity budget.
     */
    private fun qualifyWithBase(
        receiver: PsiElement,
        refText: String,
    ): String? {
        if (!refText.startsWith("::")) return refText.substringAfterLast("::")
        var sibling = receiver.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        val base = sibling?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return refText.substringAfterLast("::")
        return base + refText
    }
}

/**
 * Type named by an `as:` argument (`query_one?(sql, nil, as: {Int64, String})`),
 * or null when the call has no such argument. DB's generic queries declare
 * `as types : Tuple` and yield it, so the argument text is the result type.
 */
private fun asNamedArgumentType(expr: PsiElement): String? {
    // The argument sits inside CALL_ARGS / BARE_ARGUMENT_LIST, so the search
    // must descend. `as` is its own token and the value is a TUPLE_LITERAL
    // (`as: {Int64, String}`) or a bare type reference.
    val named =
        com.intellij.psi.util.PsiTreeUtil
            .findChildrenOfType(expr, com.intellij.psi.PsiElement::class.java)
            .firstOrNull { it.node?.elementType == CrystalTypes.AS }
            ?: return null
    val colon = named.nextSibling
    if (colon?.node?.elementType != CrystalTypes.COLON) return null
    val value = valueAfterColon(colon) ?: return null
    if (value.node?.elementType == CrystalTypes.TUPLE_LITERAL) {
        val members =
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(value, CrystalExpression::class.java)
                .mapNotNull { it.text?.trim() }
                .filter { it.isNotEmpty() }
        if (members.isNotEmpty()) return "Tuple(${members.joinToString(", ")})"
    }
    return value.text?.trim()?.takeIf { it.isNotEmpty() }
}

/** First meaningful sibling after a `COLON` leaf. */
private fun valueAfterColon(colon: PsiElement): PsiElement? {
    var next = colon.nextSibling
    while (next is PsiWhiteSpace) next = next.nextSibling
    return next
}

/**
 * Receiver-scoped lookup, then project-wide fallback for receiver-less
 * fragments. Unknown receiver: no guessing — the first hit is a lottery
 * across same-named methods (`command.downcase` typed as Char via
 * Char#downcase when `command` is untyped). File-level: ReturnCount.
 */
internal fun resolveScopedOrFallback(
    methodName: String,
    receiver: PsiElement?,
    expr: PsiElement,
): ResolvedType? {
    // Fixed shapes first: they hold for the whole receiver family, so no
    // index lookup is needed — and the index would miss them, because
    // `Enumerable#size` is defined on a module, not on the receiver's class.
    fixedShapes(methodName, receiver, expr)?.let { return it }
    // Receiver-class-scoped (`data.to_hash` where data: PersonData must
    // take PersonData#to_hash, not stdlib's generic `Hash#to_hash`).
    val receiverClass = receiverClassNameOf(receiver)
    if (receiverClass != null) {
        CrystalCallReturnTypeResolver.lookupReturnTypeInClass(methodName, receiverClass, expr.project)?.let {
            return ResolvedType(it)
        }
    }
    if (receiver != null) return null
    CrystalCallReturnTypeResolver.lookupReturnType(methodName, expr.project)?.let {
        return ResolvedType(it)
    }
    return null
}

/**
 * Return types known without any index lookup: fixed receiver-family members,
 * and the `as:` argument that DB's generic queries yield.
 */
private fun fixedShapes(
    methodName: String,
    receiver: PsiElement?,
    expr: PsiElement,
): ResolvedType? {
    receiver?.let { CrystalReceiverMemberTypes.resolve(methodName, it) }?.let { return ResolvedType(it) }
    // `query_one?(..., as: {Int64, String})` — the named argument IS the
    // return type; the generic block signature is not resolvable.
    asNamedArgumentType(expr)?.let { return ResolvedType(it) }
    return null
}
