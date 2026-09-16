package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import io.github.unurgunite.crystal.psi.CrystalBareArgument
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCaseStatement
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalExpressionStatement
import io.github.unurgunite.crystal.psi.CrystalGroupedExpression
import io.github.unurgunite.crystal.psi.CrystalIfStatement
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalStatement
import io.github.unurgunite.crystal.psi.CrystalStringExpression
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * Resolves the type of a Crystal expression.
 * Returns a [ResolvedType] with the type name and whether numeric autocasting applies.
 * Shape-specific logic lives in [CrystalCollectionTypeResolver] (literals),
 * [CrystalControlFlowTypeResolver] (ternary/operators/branches) and
 * [CrystalCallReturnTypeResolver] (method calls).
 */
object CrystalExpressionTypeResolver {
    // Guard budget for mutual recursion with CrystalTypeInference
    // (resolveType → inferTypeList → inferFromAssignmentList →
    // inferTypeFromExpressionList → resolveType). Without this, self-referential
    // assignments kill BackgroundHighlighter with StackOverflowError.
    private const val MAX_RESOLUTION_DEPTH = 16

    /**
     * Result of type resolution.
     * @param typeName The resolved type name (e.g. "Int32", "String")
     * @param isUnsuffixedNumericLiteral True if this is a numeric literal without explicit suffix,
     *        meaning Crystal will autocast it to any compatible numeric type.
     */
    data class ResolvedType(
        val typeName: String,
        val isUnsuffixedNumericLiteral: Boolean = false,
    )

    /**
     * Resolves the type of a given expression PSI element.
     * Returns null if the type cannot be determined.
     */
    fun resolveType(expr: PsiElement): ResolvedType? {
        // Guard against infinite mutual recursion with CrystalTypeInference
        // (resolveType → inferTypeList → inferFromAssignmentList →
        // inferTypeFromExpressionList → resolveType, e.g. on self-referential
        // `x = ... x ...`). Without this, BackgroundHighlighter dies with
        // StackOverflowError on ordinary files (printer.cr). ThreadLocal because
        // resolution runs on EDT and background threads concurrently.
        val depth = recursionDepth.get()
        if (depth > MAX_RESOLUTION_DEPTH) return null
        recursionDepth.set(depth + 1)
        try {
            return resolveTypeInner(expr)
        } finally {
            recursionDepth.set(depth)
        }
    }

    private val recursionDepth = ThreadLocal.withInitial { 0 }

    private fun resolveTypeInner(expr: PsiElement): ResolvedType? {
        resolveLeafShape(expr)?.let { return it }
        return resolveCompositeShape(expr)
    }

    /** Transparent wrappers, leaf tokens and fixed-shape composites. */
    private fun resolveLeafShape(expr: PsiElement): ResolvedType? {
        resolveWrapper(expr)?.let { return it }
        resolveLiteralByToken(expr)?.let { return it }
        return CrystalCollectionTypeResolver.resolveCompositeLiteral(expr)
    }

    /** Control flow, references, grouped expressions and compound wrappers. */
    private fun resolveCompositeShape(expr: PsiElement): ResolvedType? {
        resolveControlFlowOrReference(expr)?.let { return it }

        // For composite expressions (e.g. grouped_expression), try the inner expression
        if (expr is CrystalGroupedExpression) {
            val inner = expr.children.firstOrNull { it is CrystalExpression }
            if (inner != null) return resolveType(inner)
        }

        // Expression wrapper — ternary, operators, then first meaningful child
        if (expr is CrystalExpression) {
            return CrystalControlFlowTypeResolver.resolveCompoundExpression(expr)
        }

        return null
    }

    /** Control-flow shapes, variable references and method calls. */
    private fun resolveControlFlowOrReference(expr: PsiElement): ResolvedType? {
        // Control-flow expressions
        if (expr is CrystalIfStatement) return CrystalControlFlowTypeResolver.resolveIfExpression(expr)
        if (expr is CrystalCaseStatement) return CrystalControlFlowTypeResolver.resolveCaseExpression(expr)

        // Variable references → delegate to existing type inference (unions preserved as "A | B")
        if (expr is CrystalVariableReference) {
            return resolveVariableReference(expr)
        }

        // Method call expressions → resolve return type
        if (expr is CrystalMethodCallExpression || expr is CrystalBareMethodCallExpression) {
            return CrystalCallReturnTypeResolver.resolveMethodCallReturnType(expr)
        }
        return null
    }

    /** Transparent wrappers (arguments, statements) around the real expression. */
    private fun resolveWrapper(expr: PsiElement): ResolvedType? {
        val inner: PsiElement? =
            when (expr) {
                is CrystalBareArgument -> CrystalPsiUtils.firstSignificantChild(expr)
                is CrystalStatement -> innerOfStatement(expr)
                is CrystalExpressionStatement -> expr.expressionList.firstOrNull()
                else -> return null
            }
        if (inner != null) return resolveType(inner)
        return null
    }

    /** The wrapped expression inside a statement (expression/if/begin/assignment), if any. */
    private fun innerOfStatement(expr: CrystalStatement): PsiElement? =
        expr.expressionStatement ?: expr.ifStatement
            ?: expr.beginStatement ?: expr.assignment ?: expr.multiAssignment
            ?: expr.children.firstOrNull { it !is PsiWhiteSpace }

    /** Leaf literal tokens: integers, floats, strings, chars, symbols, bools, nil. */
    private fun resolveLiteralByToken(expr: PsiElement): ResolvedType? {
        val factory = LITERAL_TYPES[expr.node?.elementType]
        if (factory != null) return factory(expr.text)
        // String expressions (interpolated strings)
        if (expr is CrystalStringExpression) return ResolvedType("String")
        return null
    }

    /** Token type → literal type factory. Table-driven so no return-per-branch. */
    private val LITERAL_TYPES: Map<IElementType, (String) -> ResolvedType> =
        mapOf(
            CrystalTypes.INTEGER_LITERAL to ::resolveIntegerLiteral,
            CrystalTypes.FLOAT_LITERAL to ::resolveFloatLiteral,
            CrystalTypes.STRING_LITERAL to { ResolvedType("String") },
            CrystalTypes.CHAR_LITERAL to { ResolvedType("Char") },
            CrystalTypes.SYMBOL_LITERAL to { ResolvedType("Symbol") },
            CrystalTypes.TRUE to { ResolvedType("Bool") },
            CrystalTypes.FALSE to { ResolvedType("Bool") },
            CrystalTypes.NIL to { ResolvedType("Nil") },
        )

    private fun resolveIntegerLiteral(text: String): ResolvedType {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("i8") -> ResolvedType("Int8")
            lower.endsWith("i16") -> ResolvedType("Int16")
            lower.endsWith("i32") -> ResolvedType("Int32")
            lower.endsWith("i64") -> ResolvedType("Int64")
            lower.endsWith("i128") -> ResolvedType("Int128")
            lower.endsWith("u8") -> ResolvedType("UInt8")
            lower.endsWith("u16") -> ResolvedType("UInt16")
            lower.endsWith("u32") -> ResolvedType("UInt32")
            lower.endsWith("u64") -> ResolvedType("UInt64")
            lower.endsWith("u128") -> ResolvedType("UInt128")
            else -> ResolvedType("Int32", isUnsuffixedNumericLiteral = true)
        }
    }

    private fun resolveFloatLiteral(text: String): ResolvedType {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("f32") -> ResolvedType("Float32")
            lower.endsWith("f64") -> ResolvedType("Float64")
            else -> ResolvedType("Float64", isUnsuffixedNumericLiteral = true)
        }
    }

    private fun resolveVariableReference(expr: CrystalVariableReference): ResolvedType? {
        val name = expr.text
        val project = expr.project
        val inferred = CrystalTypeInference.inferTypeList(name, expr, project)
        if (inferred.isNotEmpty()) return ResolvedType(inferred.joinToString(" | "))
        return null
    }
}
