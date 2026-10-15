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

        // Pattern: Klasse.new(...) → type is "Klasse"
        val newPattern = Regex("""^([A-Z]\w*(?:::\w+)*)\.new(?:\(.*\))?$""", RegexOption.DOT_MATCHES_ALL)
        val newMatch = newPattern.find(text)
        if (newMatch != null) return ResolvedType(newMatch.groupValues[1])

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
        // `to_s` always yields String in Crystal (every overload, including
        // `Object#to_s`, `Int#to_s`, `Nil#to_s`). Short-circuit: the index
        // walk below would otherwise return the receiver's own type from the
        // first unannotated `to_s` overload it meets.
        if (methodName == "to_s") return "String"
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
            val returnType = method.typeReference?.text
            if (returnType != null) {
                // Full annotation text, generics included (`Hash(String, String)`):
                // typeReference holds only the return annotation (never parameter
                // lists), so parens are generic args, not noise. Stripping them
                // collapsed `Hash(String, String)` to bare `Hash` and broke
                // generic-argument compatibility downstream. Union members keep
                // the first branch (Crystal resolves left-to-right).
                return returnType
                    .split("|")
                    .first()
                    .trim()
            }
        }
        return null
    }
}
