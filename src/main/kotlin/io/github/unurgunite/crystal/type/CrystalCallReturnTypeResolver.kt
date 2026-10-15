package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
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
        val scope = GlobalSearchScope.allScope(project)
        val methods =
            StubIndex.getElements(
                CrystalMethodIndex.KEY,
                methodName,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        for (method in methods) {
            firstAnnotatedReturnType(method)?.let { return it }
        }
        return null
    }

    /**
     * Conversion methods with a fixed yield regardless of receiver
     * (`Object#to_s`, `String#to_i`, ...). Table-driven: one lookup covers
     * the family without per-name branches.
     */
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
     * class→methods index). Null when the class defines no such method —
     * the caller falls back to the project-wide first hit. Scopes
     * receiver-typed DOT-calls (`data.to_hash` with data: PersonData) to
     * the receiver's own method instead of a same-named stdlib generic.
     */
    fun lookupReturnTypeInClass(
        methodName: String,
        className: String,
        project: Project,
    ): String? {
        FIXED_RETURN_TYPES[methodName]?.let { return it }
        val scope = GlobalSearchScope.allScope(project)
        val methods =
            StubIndex.getElements(
                io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex.KEY,
                className,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        for (method in methods) {
            if (method.name != methodName) continue
            firstAnnotatedReturnType(method)?.let { return it }
        }
        return null
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
