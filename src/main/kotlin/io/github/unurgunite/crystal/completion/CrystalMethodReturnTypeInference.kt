package io.github.unurgunite.crystal.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.inspections.CrystalExpressionTypeResolver
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalStatement
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex

/**
 * Return-type inference for Crystal methods: explicit annotations first,
 * body shape (`return` statements, implicit last expression) second.
 * Union annotations are preserved as member lists.
 */
object CrystalMethodReturnTypeInference {
    /**
     * Finds the return type(s) of a method by name. If [className] is provided, searches
     * within that class; otherwise project-wide. When no explicit return type is annotated,
     * infers from the method body. Union return annotations are preserved.
     */
    fun inferReturnTypeOfMethodList(
        methodName: String,
        className: String?,
        project: Project,
    ): List<String> {
        val scope = GlobalSearchScope.allScope(project)
        val methods =
            StubIndex
                .getElements(
                    CrystalMethodIndex.KEY,
                    methodName,
                    project,
                    scope,
                    CrystalMethodDefinition::class.java,
                ).toMutableList()

        val results = mutableListOf<String>()
        for (method in methods) {
            if (className != null) {
                val enclosing = CrystalCompletionHelper.getEnclosingClassName(method)
                if (enclosing != className) continue
            }
            results.addAll(inferReturnTypeOfMethod(method))
        }
        return results.distinct()
    }

    /** Return type of a single method: annotation first, body shape second. */
    private fun inferReturnTypeOfMethod(method: CrystalMethodDefinition): List<String> {
        val returnType = method.typeReference?.text
        if (returnType != null) return splitTypeNames(returnType)
        val inferred = inferReturnTypeFromBody(method) ?: return emptyList()
        return splitTypeNames(inferred)
    }

    /**
     * Infers the return type from a method body when no explicit return type is annotated.
     * Checks for return statements first, then falls back to the last expression (implicit return).
     */
    private fun inferReturnTypeFromBody(method: CrystalMethodDefinition): String? {
        val body = method.methodBody ?: return null
        val statements = body.statementList.statementList
        if (statements.isEmpty()) return null

        // Check each statement for return statements
        for (stmt in statements) {
            resolveReturnStatement(stmt)?.let { return it }
        }

        return inferImplicitReturnType(statements.last())
    }

    /** Type of the last statement (implicit return): expression list first, whole statement second. */
    private fun inferImplicitReturnType(lastStmt: CrystalStatement): String? {
        val exprStmt = lastStmt.expressionStatement
        if (exprStmt != null) {
            resolveFirstExpression(exprStmt.expressionList)?.let { return it }
        }
        return CrystalExpressionTypeResolver.resolveType(lastStmt)?.typeName
    }

    /** Type name of a `return <expr>` statement's expression, if resolvable. */
    private fun resolveReturnStatement(stmt: CrystalStatement): String? {
        val returnStmt = stmt.returnStatement ?: return null
        val returnExpr = returnStmt.expressionList.firstOrNull() ?: return null
        return CrystalExpressionTypeResolver.resolveType(returnExpr)?.typeName
    }

    /** Type name of the first expression in [expressions], if resolvable. */
    private fun resolveFirstExpression(expressions: List<PsiElement>): String? {
        val innerExpr = expressions.firstOrNull() ?: return null
        return CrystalExpressionTypeResolver.resolveType(innerExpr)?.typeName
    }

    /**
     * Splits a union type annotation into its member type names, preserving
     * generics (e.g. `Array(String)` stays intact). Does NOT split on `|`
     * nested inside parentheses.
     * e.g. "Int32 | Nil" → ["Int32", "Nil"]; "Slice(UInt8) | Nil" → ["Slice(UInt8)", "Nil"]
     */
    fun splitTypeNames(typeText: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (ch in typeText) {
            when (ch) {
                '(' -> {
                    depth++
                    current.append(ch)
                }

                ')' -> {
                    depth = maxOf(0, depth - 1)
                    current.append(ch)
                }

                '|' -> {
                    if (depth == 0) {
                        val piece = current.toString().trim()
                        if (piece.isNotEmpty()) parts.add(piece)
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }

                else -> {
                    current.append(ch)
                }
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) parts.add(last)
        return parts
    }
}
