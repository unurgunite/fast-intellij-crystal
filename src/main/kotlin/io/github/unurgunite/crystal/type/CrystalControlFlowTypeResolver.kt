package io.github.unurgunite.crystal.type

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import io.github.unurgunite.crystal.psi.CrystalCaseStatement
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalIfStatement
import io.github.unurgunite.crystal.psi.CrystalStatement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver.ResolvedType
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver.resolveType

/**
 * Types of control-flow expressions: ternaries, binary operators,
 * `if`/`else` chains and `case`/`when` branches (unions across branches).
 */
object CrystalControlFlowTypeResolver {
    // A binary operator needs at least 3 children: left operand, operator, right operand.
    private const val MIN_BINARY_OPERATOR_CHILDREN = 3

    /** Ternary first (QUESTION always means ternary), then operators, then first child. */
    fun resolveCompoundExpression(expr: CrystalExpression): ResolvedType? {
        val astChildren = expr.node.getChildren(null)

        // Index access `d["phone"]` / `d["phone"]?`: yields the collection's
        // value type (plus Nil for `[]?`), never the receiver itself.
        // Checked before operator/first-child descent, which would take the
        // bare receiver (`d` → Hash) and lose the access.
        resolveIndexAccessOf(astChildren)?.let { return it }

        // Ternary expression: or_expression QUESTION expression COLON expression
        // Check FIRST — QUESTION always indicates ternary, takes priority over operators
        resolveTernary(astChildren)?.let { return it }

        // Operator detection — binary operators like ==, +, etc.
        val opResult = resolveOperatorType(astChildren)
        if (opResult != null) return opResult

        val firstChild = expr.firstChild
        if (firstChild != null) return resolveType(firstChild)
        return null
    }

    private fun resolveTernary(astChildren: Array<ASTNode>): ResolvedType? {
        val (trueExpr, falseExpr) = findTernaryBranches(astChildren) ?: return null
        val trueType = trueExpr?.let { resolveType(it) }
        val falseType = falseExpr?.let { resolveType(it) }
        return mergeBranchPair(trueType, falseType)
    }

    /** The true/false branch elements around `?` and `:`, or null when not a ternary. */
    private fun findTernaryBranches(astChildren: Array<ASTNode>): Pair<PsiElement?, PsiElement?>? {
        val questionIdx = astChildren.indexOfFirst { it.elementType == CrystalTypes.QUESTION }
        if (questionIdx < 0) return null
        val colonIdx = astChildren.indexOfFirst { it.elementType == CrystalTypes.COLON }
        if (colonIdx <= questionIdx) return null
        val trueExpr = astChildren.drop(questionIdx + 1).firstOrNull { it.psi !is PsiWhiteSpace }?.psi
        val falseExpr = astChildren.drop(colonIdx + 1).firstOrNull { it.psi !is PsiWhiteSpace }?.psi
        return trueExpr to falseExpr
    }

    /** Union of two branch types (either side may be unresolvable). */
    private fun mergeBranchPair(
        first: ResolvedType?,
        second: ResolvedType?,
    ): ResolvedType? {
        if (first != null && second != null) {
            if (first.typeName == second.typeName) return first
            return ResolvedType("${first.typeName} | ${second.typeName}")
        }
        if (first != null) return first
        return second
    }

    fun resolveIfExpression(expr: CrystalIfStatement): ResolvedType? {
        val branches = mutableListOf<ResolvedType>()

        val thenStatements = expr.statementList?.statementList
        if (!thenStatements.isNullOrEmpty()) {
            val lastThen = thenStatements.lastOrNull()
            if (lastThen != null) {
                val thenType = resolveType(lastThen)
                if (thenType != null) branches.add(thenType)
            }
        }

        val elseClause = expr.elseClause
        if (elseClause != null) {
            resolveLastStatement(elseClause.statementList.statementList)?.let { branches.add(it) }
        } else {
            branches.add(ResolvedType("Nil"))
        }

        return mergeBranches(branches)
    }

    fun resolveCaseExpression(expr: CrystalCaseStatement): ResolvedType? {
        val branches = mutableListOf<ResolvedType>()

        for (whenClause in expr.whenClauseList) {
            resolveLastStatement(whenClause.statementList.statementList)?.let { branches.add(it) }
        }

        val elseClause = expr.elseClause
        if (elseClause != null) {
            resolveLastStatement(elseClause.statementList.statementList)?.let { branches.add(it) }
        }

        return mergeBranches(branches)
    }

    /** Type of the last statement in a branch, if resolvable. */
    private fun resolveLastStatement(statements: List<CrystalStatement>): ResolvedType? {
        val last = statements.lastOrNull() ?: return null
        return resolveType(last)
    }

    /** Union across branch types: empty → unknown, one → itself, many → `A | B`. */
    private fun mergeBranches(branches: List<ResolvedType>): ResolvedType? {
        if (branches.isEmpty()) return null
        if (branches.size == 1) return branches.first()
        val typeList = branches.joinToString(" | ") { it.typeName }
        return ResolvedType(typeList)
    }

    private fun resolveOperatorType(astChildren: Array<ASTNode>): ResolvedType? {
        val nonWhitespace = astChildren.filter { it.psi !is PsiWhiteSpace }
        if (nonWhitespace.size < MIN_BINARY_OPERATOR_CHILDREN) return null

        val opType = nonWhitespace[1].elementType

        return when (opType) {
            CrystalTypes.EQ, CrystalTypes.NEQ,
            CrystalTypes.LT, CrystalTypes.LTE,
            CrystalTypes.GT, CrystalTypes.GTE,
            CrystalTypes.SPACESHIP, CrystalTypes.CASE_EQ,
            CrystalTypes.MATCH_OP,
            -> {
                ResolvedType("Bool")
            }

            CrystalTypes.AND_AND, CrystalTypes.OR_OR -> {
                // `&&`/`||` yield an operand, never Bool (`mode? || "test"`
                // is String, not Bool): union both sides. Over-wide, never
                // narrow — a union suppresses false mismatches, a wrong
                // Bool creates them.
                resolveLogicalType(nonWhitespace[0].psi, nonWhitespace[2].psi)
            }

            CrystalTypes.PLUS, CrystalTypes.MINUS,
            CrystalTypes.STAR, CrystalTypes.SLASH,
            CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT,
            CrystalTypes.DOUBLE_STAR,
            -> {
                resolveArithmeticType(nonWhitespace[0].psi, nonWhitespace[2].psi)
            }

            else -> {
                null
            }
        }
    }

    /** Arithmetic keeps the operand type only when both sides agree. */
    private fun resolveArithmeticType(
        left: PsiElement,
        right: PsiElement,
    ): ResolvedType? {
        val leftType = resolveType(left)
        val rightType = resolveType(right)
        if (leftType != null && leftType.typeName == rightType?.typeName) return leftType
        return null
    }

    /** `&&`/`||` operand union (file-level companion: detekt budget). */
    private fun resolveLogicalType(
        left: PsiElement,
        right: PsiElement,
    ): ResolvedType? {
        val leftType = resolveType(left)?.typeName
        val rightType = resolveType(right)?.typeName
        if (leftType == null && rightType == null) return null
        val parts = listOfNotNull(leftType, rightType).distinct()
        return ResolvedType(parts.joinToString(" | "))
    }
}

/**
 * Index-access element type (`d["phone"]?` → `String | Nil`).
 * File-level: the resolver object is at detekt's function budget.
 * Single return: every miss funnels to one null through takeIf chains.
 */
private fun resolveIndexAccessOf(astChildren: Array<ASTNode>): ResolvedType? {
    val bracketIdx = astChildren.indexOfFirst { it.elementType == CrystalTypes.LBRACKET }.takeIf { it > 0 } ?: return null
    if (astChildren.none { it.elementType == CrystalTypes.RBRACKET }) return null
    val receiverType = astChildren.getOrNull(bracketIdx - 1)?.psi?.let { resolveType(it)?.typeName }
    val valueType = receiverType?.let { indexValueTypeOf(it) }
    if (valueType == null) return null
    val hasQuestion = astChildren.any { it.elementType == CrystalTypes.QUESTION }
    return if (hasQuestion) ResolvedType("$valueType | Nil") else ResolvedType(valueType)
}

/** Value type of a generic collection (`Hash(K, V)` → V, `Array(T)` → T), else null. */
private fun indexValueTypeOf(receiverType: String): String? {
    val base = receiverType.substringBefore("(").trim()
    val args = splitGenericArgsOf(receiverType)
    return when {
        base == "Hash" && args.size >= 2 -> args[1]
        base == "Array" && args.size >= 1 -> args[0]
        else -> null
    }
}

/** Generic arguments of `Base(A, B)` respecting nested parens, empty otherwise. */
private fun splitGenericArgsOf(typeName: String): List<String> {
    val openIdx = typeName.indexOf('(')
    val closeIdx = typeName.lastIndexOf(')')
    if (openIdx < 0 || closeIdx <= openIdx) return emptyList()
    val inner = typeName.substring(openIdx + 1, closeIdx)
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    for (i in inner.indices) {
        when (inner[i]) {
            '(' -> {
                depth++
            }

            ')' -> {
                depth--
            }

            ',' -> {
                if (depth == 0) {
                    result.add(inner.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
    }
    result.add(inner.substring(start).trim())
    return result
}
