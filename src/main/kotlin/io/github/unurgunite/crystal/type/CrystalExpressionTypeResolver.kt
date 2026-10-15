package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import io.github.unurgunite.crystal.psi.CrystalBareArgument
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCaseStatement
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalExpressionStatement
import io.github.unurgunite.crystal.psi.CrystalGroupedExpression
import io.github.unurgunite.crystal.psi.CrystalIfStatement
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
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

        // Expression wrapper — ternary, operators, then first meaningful child.
        // NOTE: a DOT-call fragment nested anywhere inside (`{...}.to_json`
        // as an argument value) resolves FIRST: firstChild descent below
        // would take the bare receiver (`{...}` → Hash) and lose the call.
        if (expr is CrystalExpression) {
            findDotCallDeep(expr)?.let { dotCall ->
                resolveDotCallFragmentOf(dotCall)?.let { return it }
            }
            return CrystalControlFlowTypeResolver.resolveCompoundExpression(expr)
        }

        return null
    }

    /** Control-flow shapes, variable references and method calls. */
    private fun resolveControlFlowOrReference(expr: PsiElement): ResolvedType? {
        // Control-flow expressions
        if (expr is CrystalIfStatement) return CrystalControlFlowTypeResolver.resolveIfExpression(expr)
        if (expr is CrystalCaseStatement) return CrystalControlFlowTypeResolver.resolveCaseExpression(expr)

        // Variable / ivar references (unions preserved as "A | B")
        resolveReferenceOperand(expr)?.let { return it }

        return resolveCallShape(expr)
    }

    /**
     * Method call shapes: full calls resolve the return type; DOT_CALL_ACCESS
     * (`recv.to_json`) is a postfix fragment — resolve the enclosing call
     * shape instead of the bare fragment. File-level dispatch keeps this
     * function under detekt's ReturnCount budget.
     */
    private fun resolveCallShape(expr: PsiElement): ResolvedType? {
        if (expr is CrystalMethodCallExpression || expr is CrystalBareMethodCallExpression) {
            return CrystalCallReturnTypeResolver.resolveMethodCallReturnType(expr)
        }
        // NOTE: compare by PSI class, never by elementType.toString() — its
        // rendering is "CrystalElementType.DOT_CALL_ACCESS", so string
        // equality against "DOT_CALL_ACCESS" is always false (dead branch
        // that silently dropped every DOT-call fragment resolution).
        if (expr !is io.github.unurgunite.crystal.psi.CrystalDotCallAccess) return null
        val parentCall = expr.parent
        if (parentCall is CrystalMethodCallExpression || parentCall is CrystalBareMethodCallExpression) {
            return CrystalCallReturnTypeResolver.resolveMethodCallReturnType(parentCall)
        }
        return resolveDotCallFragmentOf(expr)
    }

    /** Transparent wrappers (arguments, statements) around the real expression. */
    private fun resolveWrapper(expr: PsiElement): ResolvedType? {
        // `X.to_json` ANYWHERE inside the wrapper subtree (argument values
        // like `{...}.to_json`): the call yields String for every receiver.
        // Checked FIRST via raw text scan: structural fragment search misses
        // shapes where the receiver and the DOT-call sit in different
        // subtrees (verified by probe: `{...}.to_json` resolves Hash without
        // this). Text scan is safe here: `.to_json` with a word boundary is
        // unambiguous, and String is correct for every receiver.
        if (hasToJsonCall(expr)) return ResolvedType("String")
        return resolveWrapperInnerOf(expr)
    }

    /** The wrapped expression inside a statement (expression/if/begin/assignment), if any. */
    private fun innerOfStatement(expr: CrystalStatement): PsiElement? =
        innerOfStatementOf(expr)
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
}

/**
 * Variable / ivar reference operand of an expression, if any: plain locals
 * delegate to the generic inference, `@ivar` / `@@cvar` to the declared-type
 * index first. Merged single function (file-level: detekt budget).
 */
private fun resolveReferenceOperand(expr: PsiElement): CrystalExpressionTypeResolver.ResolvedType? {
    val inferred =
        when (expr) {
            is CrystalVariableReference -> {
                CrystalTypeInference.inferTypeList(expr.text, expr, expr.project)
            }

            is CrystalInstanceVarAccess, is CrystalClassVarAccess -> {
                CrystalInstanceVarTypeInference.inferIvarType(expr.text, expr, expr.project, 0)
            }

            else -> {
                return null
            }
        }
    if (inferred.isNotEmpty()) return CrystalExpressionTypeResolver.ResolvedType(inferred.joinToString(" | "))
    return null
}

/** DOT-call fragment method+receiver reader (file-level: detekt budget). */
private fun dotCallPartsOf(expr: PsiElement): Pair<String?, PsiElement?> {
    var methodName: String? = null
    var receiver: PsiElement? = null
    var child = expr.firstChild
    while (child != null) {
        if (child.node?.elementType == CrystalTypes.DOT) {
            var next = child.nextSibling
            while (next is PsiWhiteSpace) next = next.nextSibling
            methodName = next?.text
            var prev = child.prevSibling
            while (prev is PsiWhiteSpace) prev = prev.prevSibling
            // DOT-first composite (`.parse(s)` in `URI.parse(s)`): the
            // receiver lives outside, as the composite's preceding sibling
            // in the flattened postfix chain — not inside as a child.
            receiver = prev ?: CrystalPsiUtils.receiverBeforeDot(next ?: child, child)
        }
        child = child.nextSibling
    }
    return methodName to receiver
}

/** DOT-call fragment type (file-level: detekt budget). `to_json` is String for every receiver. */
private fun resolveDotCallFragmentOf(expr: PsiElement): CrystalExpressionTypeResolver.ResolvedType? {
    val (methodName, receiver) = dotCallPartsOf(expr)
    if (methodName == "to_json") return CrystalExpressionTypeResolver.ResolvedType("String")
    if (methodName != null) {
        // Receiver-class-scoped first (`data.to_hash` where data: PersonData
        // must take PersonData#to_hash, not stdlib's generic `Hash#to_hash`):
        // the project-wide first-hit fallback ignores the receiver and loses
        // to same-named stdlib methods.
        receiverClassNameOf(receiver)?.let { receiverClass ->
            CrystalCallReturnTypeResolver.lookupReturnTypeInClass(methodName, receiverClass, expr.project)?.let {
                return CrystalExpressionTypeResolver.ResolvedType(it)
            }
        }
        CrystalCallReturnTypeResolver.lookupReturnType(methodName, expr.project)?.let {
            return CrystalExpressionTypeResolver.ResolvedType(it)
        }
    }
    return null
}

/**
 * Class name behind a DOT-call receiver: a CONSTANT (`PersonData.new`), an
 * uppercase reference (`URI` parses as VARIABLE_REFERENCE, not a CONSTANT
 * token — same meaning), a qualified path (`HTTP::Params` parses as
 * VARIABLE_REFERENCE + NAMESPACE_ACCESS siblings), or the resolved type
 * of a lowercase variable reference (`data` → PersonData via inference).
 * Returns the FULL qualified path when known (`HTTP::Params`, not just
 * `Params`) — the caller tries qualified scope first, short scope second.
 * Null when the receiver shape is unknown (fallback applies).
 */
private fun receiverClassNameOf(receiver: PsiElement?): String? {
    if (receiver == null) return null
    if (receiver.node?.elementType == io.github.unurgunite.crystal.psi.CrystalTypes.CONSTANT) return receiver.text
    CrystalReceiverNames.direct(receiver)?.let { return it }
    val typeName = CrystalExpressionTypeResolver.resolveType(receiver)?.typeName ?: return null
    // Strip union/nilable wrappers to the first concrete member for scoping.
    // Depth-aware split: naive split("|") cut `Hash(K | L, V | W)` into `Hash(K`.
    return io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference
        .splitTypeNames(typeName)
        .firstOrNull()
        ?.trim()
        ?.removeSuffix("?")
        ?.substringAfterLast("::")
        ?.takeIf { it.isNotEmpty() }
}

/** Value inside an ARGUMENT shell, re-resolved (file-level: detekt budget). */
private fun resolveArgumentValueOf(expr: PsiElement): CrystalExpressionTypeResolver.ResolvedType? {
    val inner = expr.firstChild ?: return null
    if (inner !== expr) return CrystalExpressionTypeResolver.resolveType(inner)
    return null
}

/** Leaf wrapper shapes (file-level: detekt budget). */
private fun resolveWrapperLeafOf(expr: PsiElement): CrystalExpressionTypeResolver.ResolvedType? {
    val inner: PsiElement? =
        when (expr) {
            is CrystalBareArgument -> CrystalPsiUtils.firstSignificantChild(expr)
            is CrystalStatement -> innerOfStatementOf(expr)
            is CrystalExpressionStatement -> expr.expressionList.firstOrNull()
            else -> return null
        }
    if (inner != null) return CrystalExpressionTypeResolver.resolveType(inner)
    return null
}

/** Statement inner shape (file-level: detekt budget). */
private fun innerOfStatementOf(expr: CrystalStatement): PsiElement? =
    expr.expressionStatement ?: expr.ifStatement
        ?: expr.beginStatement ?: expr.assignment ?: expr.multiAssignment
        ?: expr.children.firstOrNull { it !is PsiWhiteSpace }

/**
 * Last DOT-call fragment of an expression: the chain tail decides the type
 * (`ENV.fetch(...).to_i` is Int32 by `.to_i`, not String by `.fetch`).
 * Direct children win over nested shells; among siblings the rightmost
 * one is the tail. The old first-hit order typed chains by their head.
 * Single function (file-level: detekt budget).
 */
private fun findDotCallDeep(expr: PsiElement): PsiElement? {
    var current: PsiElement? = expr.firstChild
    var found: PsiElement? = null
    while (current != null) {
        if (current is io.github.unurgunite.crystal.psi.CrystalDotCallAccess) found = current
        if (isTransparentShell(current)) {
            findDotCallDeep(current)?.let { found = it }
        }
        current = current.nextSibling
    }
    return found
}

/** Shell node types transparent to the fragment search. */
private fun isTransparentShell(child: PsiElement): Boolean =
    child is CrystalExpression ||
        child is io.github.unurgunite.crystal.psi.CrystalHashLiteral ||
        child is io.github.unurgunite.crystal.psi.CrystalArrayLiteral ||
        child is io.github.unurgunite.crystal.psi.CrystalTupleLiteral ||
        child is CrystalGroupedExpression

/** Wrapper descent once fragments are ruled out (file-level: detekt budget). */
private fun resolveWrapperInnerOf(expr: PsiElement): CrystalExpressionTypeResolver.ResolvedType? {
    if (expr is CrystalExpression) {
        // DOT-call fragment first (`data.to_hash`): firstChild descent
        // would take the bare receiver (`data` → PersonData) and lose the
        // call's own return type. But ONLY the fragment resolves here —
        // anything else (ternary, operators) falls through to composite's
        // resolveCompoundExpression; descending here would steal the
        // condition/left-operand type instead of computing the result.
        findDotCallDeep(expr)?.let { dotCall ->
            resolveDotCallFragmentOf(dotCall)?.let { return it }
        }
        return null
    }
    if (expr is io.github.unurgunite.crystal.psi.CrystalArgument) {
        return resolveArgumentValueOf(expr)
    }
    return resolveWrapperLeafOf(expr)
}

/** True when the subtree holds a `.to_json` call (word-boundary DOT form; file-level: detekt budget). */
private fun hasToJsonCall(expr: PsiElement): Boolean {
    var child = expr.firstChild
    while (child != null) {
        if (child.node?.elementType == CrystalTypes.DOT) {
            var next = child.nextSibling
            while (next is PsiWhiteSpace) next = next.nextSibling
            val name = next?.text ?: ""
            if (name == "to_json" || name.startsWith("to_json(")) return true
        }
        if (child.firstChild != null && hasToJsonCall(child)) return true
        child = child.nextSibling
    }
    return false
}
