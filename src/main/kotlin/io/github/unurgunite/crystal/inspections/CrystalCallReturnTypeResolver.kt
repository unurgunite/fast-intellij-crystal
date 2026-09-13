package io.github.unurgunite.crystal.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.inspections.CrystalExpressionTypeResolver.ResolvedType
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex

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
                return returnType
                    .split("|")
                    .first()
                    .trim()
                    .replace(Regex("""\(.*\)"""), "")
                    .trim()
            }
        }
        return null
    }
}
