package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.extractParameterName
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver
import io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference.inferReturnTypeOfMethodList
import io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference.splitTypeNames

/**
 * Basic type inference for Crystal variables.
 * Resolves the type of an identifier by analyzing its assignment or declaration context.
 * Literal shapes live in [CrystalLiteralTypeInference], method return types in
 * [CrystalMethodReturnTypeInference].
 */
object CrystalTypeInference {
    // Recursion budget for chained inference (`x = obj.foo`, unions, aliases).
    // Prevents the StackOverflow seen on self-referential assignments (`x = x.to_s`).
    private const val MAX_INFERENCE_DEPTH = 5

    /**
     * Infers the type (class name) of a variable at the given position.
     * Returns null if the type cannot be determined.
     */
    fun inferType(
        variableName: String,
        context: PsiElement,
        project: Project,
    ): String? {
        val types = inferTypeList(variableName, context, project)
        return if (types.isEmpty()) null else types.joinToString(" | ")
    }

    /**
     * All candidate types for [variableName], including union members (e.g. `Int32 | Nil`)
     * and receiver-derived types (e.g. `x = obj.foo` where `obj`'s type is known). Used by
     * Go-to-Definition and completion so a union-typed variable resolves methods across every
     * member type, not just the first.
     */
    fun inferTypeList(
        variableName: String,
        context: PsiElement,
        project: Project,
        depth: Int = 0,
    ): List<String> {
        if (depth > MAX_INFERENCE_DEPTH) return emptyList()
        val paramType = inferFromParameter(variableName, context)
        if (paramType != null) return paramType
        val assignTypes = inferFromAssignmentList(variableName, context, project, depth)
        if (assignTypes.isNotEmpty()) return assignTypes
        return emptyList()
    }

    /**
     * Check if the variable is a parameter with a type annotation.
     * e.g. def foo(x : Apfel) → type of x is "Apfel"; `x : Int32 | Nil` → ["Int32", "Nil"].
     */
    private fun inferFromParameter(
        name: String,
        context: PsiElement,
    ): List<String>? {
        // Find enclosing method
        val method =
            PsiTreeUtil.getParentOfType(context, CrystalMethodDefinition::class.java)
                ?: return null

        val paramList = method.parameterList ?: return null
        for (param in paramList.parameterList) {
            val paramName = extractParameterName(param)
            if (paramName == name) {
                // Has type annotation?
                val typeRef = param.typeReference
                if (typeRef != null) {
                    return splitTypeNames(typeRef.text)
                }
            }
        }
        return null
    }

    /**
     * Search for assignments to this variable in the current scope.
     * Patterns recognized:
     * - x = Klasse.new → type is "Klasse"
     * - x = Klasse.method_name → return type (union) of method_name
     * - x = receiver.method → return type of method on the inferred receiver type
     * - x = method_name → return type (union) of top-level/enclosing method
     * Reassigned variables accumulate all candidate types (unions are preserved).
     */
    private fun inferFromAssignmentList(
        name: String,
        context: PsiElement,
        project: Project,
        depth: Int,
    ): List<String> {
        val containingFile = context.containingFile ?: return emptyList()
        val assignments = PsiTreeUtil.collectElementsOfType(containingFile, CrystalAssignment::class.java)
        val results = mutableListOf<String>()
        for (assignment in assignments.reversed()) {
            results.addAll(inferFromSingleAssignment(assignment, name, context, project, depth))
        }
        return results.distinct()
    }

    /** Types contributed by one assignment, or empty when it does not target [name]. */
    private fun inferFromSingleAssignment(
        assignment: CrystalAssignment,
        name: String,
        context: PsiElement,
        project: Project,
        depth: Int,
    ): List<String> {
        val targetText = CrystalAssignmentTarget.targetText(assignment) ?: return emptyList()
        if (targetText != name && targetText != "@$name") return emptyList()
        if (assignment.textOffset > context.textOffset) return emptyList()
        val expr = CrystalAssignmentTarget.rhs(assignment) ?: return emptyList()
        return inferTypeFromExpressionList(expr, project, depth)
    }

    /**
     * Infers the type(s) from an expression, returning every candidate (union members
     * preserved). Dispatches in specificity order: literals, control-flow, collection
     * shapes, then `Klasse.new` / `Klasse.method` / `receiver.method` / bare calls.
     */
    private fun inferTypeFromExpressionList(
        expr: PsiElement,
        project: Project,
        depth: Int,
    ): List<String> {
        inferFromScalarOrControlFlow(expr)?.let { return it }
        CrystalLiteralTypeInference.inferFromCollectionShape(expr)?.let { return it }
        inferFromDottedCall(expr, project, depth)?.let { return it }
        return inferFromBareCall(expr, project)
    }

    /** Scalar literals and ternary/control-flow (delegated, may be a union). */
    private fun inferFromScalarOrControlFlow(expr: PsiElement): List<String>? {
        val literalType = CrystalLiteralTypeInference.inferFromLiteral(expr)
        if (literalType != null) return listOf(literalType)

        // Ternary / control-flow: delegate to CrystalExpressionTypeResolver (may be a union)
        if (expr is CrystalExpression) {
            val resolved = CrystalExpressionTypeResolver.resolveType(expr)
            if (resolved != null) return splitTypeNames(resolved.typeName)
        }
        return null
    }

    /**
     * Dotted calls: `Klasse.new`, `Klasse.method(...)` and `receiver.method`
     * (receiver type inferred recursively). Returns null when no dotted pattern matches.
     */
    private fun inferFromDottedCall(
        expr: PsiElement,
        project: Project,
        depth: Int,
    ): List<String>? {
        val text = expr.text.trim()

        // Pattern: Klasse.new (handles multi-line args and bare args without parens)
        val newMatch = Regex("""^([A-Z]\w*(?:::\w+)*)\.new(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
        if (newMatch != null) return listOf(newMatch.groupValues[1])

        // Pattern: Klasse.method_name(...) (handles multi-line args and bare args)
        val classMethodMatch = Regex("""^([A-Z]\w*(?:::\w+)*)\.(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
        if (classMethodMatch != null) {
            return inferFromClassMethodMatch(classMethodMatch, project)
        }

        return inferFromReceiverMethodCall(expr, text, project, depth)
    }

    private fun inferFromClassMethodMatch(
        classMethodMatch: MatchResult,
        project: Project,
    ): List<String> {
        val className = classMethodMatch.groupValues[1]
        val methodName = classMethodMatch.groupValues[2]
        if (methodName == "new") return listOf(className)
        return inferReturnTypeOfMethodList(methodName, className, project)
    }

    /**
     * Pattern: receiver.method where receiver is a variable (e.g. x = obj.foo).
     * Infers the receiver's type, then the method's return type across that type.
     * Returns null when the text is not a receiver-method call at all; an empty
     * list when it is, but the receiver type is unknown (no guessing).
     */
    private fun inferFromReceiverMethodCall(
        expr: PsiElement,
        text: String,
        project: Project,
        depth: Int,
    ): List<String>? {
        val recvMethodMatch =
            Regex("""^([a-z]\w*)\.(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(text)
                ?: return null
        val recv = recvMethodMatch.groupValues[1]
        val meth = recvMethodMatch.groupValues[2]
        val recvTypes = inferTypeList(recv, expr, project, depth + 1)
        val results = mutableListOf<String>()
        for (rt in recvTypes) results.addAll(inferReturnTypeOfMethodList(meth, rt, project))
        // Receiver type unknown — no guess; fall through (no bare-call match for `a.b`).
        return results
    }

    /** Bare method call (no dot): return type union of that method, if lowercase. */
    private fun inferFromBareCall(
        expr: PsiElement,
        project: Project,
    ): List<String> {
        // Pattern: bare method_call (no dot) (handles multi-line args and bare args)
        val bareCallMatch = Regex("""^(\w+)(?:\([\s\S]*\)|\s+[\s\S]+)?$""").find(expr.text.trim()) ?: return emptyList()
        val methodName = bareCallMatch.groupValues[1]
        // Only if it starts with lowercase (method, not class)
        if (!methodName[0].isLowerCase()) return emptyList()
        return inferReturnTypeOfMethodList(methodName, null, project)
    }
}
