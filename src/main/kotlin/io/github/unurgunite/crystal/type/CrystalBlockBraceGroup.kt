package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareCommandSpaceFirst
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalDotCallAccess
import io.github.unurgunite.crystal.psi.CrystalHashLiteral
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalTupleLiteral
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * Block-body detection for brace arguments. Crystal has no brace *value*
 * literal for a bare command: `sync { delete(1) }` and `sync { }` pass a
 * block, while `{1, 2}` is a real tuple argument. GrammarKit parses every
 * brace group as TUPLE_LITERAL (HASH_LITERAL when empty), so the shape of
 * the contents decides. Split into its own file: keeps CrystalCallArguments.kt
 * under detekt's TooManyFunctions budget.
 */
internal fun isBareBlockTuple(arg: PsiElement): Boolean {
    // Splats (`foo(*args)`) are never brace groups — never a block body.
    if (arg.node.findChildByType(CrystalTypes.STAR) != null) return false
    // A brace group is a TUPLE_LITERAL (`{...}`) or an empty HASH_LITERAL.
    // A parenthesised argument (`raise(Foo.new(x))`) has neither, so it is a
    // real positional argument, never a block body.
    if (CrystalPsiUtils.firstSignificantChild(arg)?.let(::holdsBraceNode) != true) return false
    val child = CrystalPsiUtils.firstSignificantChild(arg) ?: return false
    val group = listOfNotNull(child, CrystalPsiUtils.firstSignificantChild(child))
    return group.any { isBlockBraceGroup(it, group) }
}

/**
 * A brace group that is a block body rather than a value tuple:
 * a bare command (`{ delete(1) }`) or empty braces (`sync { }` — the
 * grammar has no empty TUPLE_LITERAL, so that arrives as HASH_LITERAL).
 */
internal fun isBlockBraceGroup(
    element: PsiElement,
    group: List<PsiElement>,
): Boolean =
    when (element) {
        is CrystalHashLiteral -> {
            element.text.replace(WHITESPACE, "") == "{}"
        }

        is CrystalTupleLiteral -> {
            element.expressionList.expressionList.let { it.size <= 1 && it.all(::holdsBlockBody) }
        }

        else -> {
            group.any(::holdsCallShape)
        }
    }

private val WHITESPACE = "\\s".toRegex()

/**
 * True when a brace-group element is a block body, not a tuple value: a call
 * (`{ delete(1) }`) or a lone expression that cannot be a one-element tuple
 * literal (`{ wait_for_available }`, `Log.notice { message }`). A bare literal
 * or constant (`{1}`, `{Foo::BAR}`) stays a real tuple value.
 */
private fun holdsBlockBody(expr: PsiElement): Boolean {
    if (holdsCallShape(expr)) return true
    val inner = CrystalPsiUtils.firstSignificantChild(expr) ?: return false
    return !isValueOnly(inner)
}

/** Literals and constant references — the only things a `{...}` value can hold. */
private fun isValueOnly(expr: PsiElement): Boolean =
    when (expr) {
        is CrystalVariableReference -> false
        is CrystalTupleLiteral, is CrystalHashLiteral -> true
        else -> expr.node?.elementType in VALUE_ELEMENTS
    }

private val VALUE_ELEMENTS =
    setOf(
        CrystalTypes.INTEGER_LITERAL,
        CrystalTypes.FLOAT_LITERAL,
        CrystalTypes.STRING_LITERAL,
        CrystalTypes.CHAR_LITERAL,
        CrystalTypes.SYMBOL_LITERAL,
        CrystalTypes.TRUE,
        CrystalTypes.FALSE,
        CrystalTypes.NIL,
        CrystalTypes.CONSTANT,
    )

/** True when [expr] is (or directly wraps) a brace group literal. */
private fun holdsBraceNode(expr: PsiElement): Boolean =
    expr is CrystalTupleLiteral || expr is CrystalHashLiteral ||
        PsiTreeUtil.findChildOfType(expr, CrystalTupleLiteral::class.java) != null ||
        PsiTreeUtil.findChildOfType(expr, CrystalHashLiteral::class.java) != null

/**
 * True when [expr] holds a call shape: a bare command, a DOT-call fragment,
 * or a wrapper around one. `d.close` parses as `EXPRESSION[VARIABLE_REFERENCE
 * + DOT_CALL_ACCESS]` siblings — the DOT-call is a child, not the first
 * child — so the check walks one level down. A literal/constant/reference
 * alone is a real tuple value, not a block body.
 */
internal fun holdsCallShape(expr: PsiElement): Boolean {
    val children = expr.children
    // `a + b` / `{1, 2}` hold no call in the tower: every meaningful child is
    // the callee itself or a literal, so require one non-callee-level call.
    if (children.any(::isCallShape)) return true
    return children.any { child -> child.children.any(::isCallShape) }
}

/**
 * True for a bare command, a method call (`delete(1)`) or a DOT-call
 * fragment (`user.save`) — the shapes a block body is made of.
 */
internal fun isCallShape(element: PsiElement): Boolean =
    element is CrystalBareCommandExpression ||
        element is CrystalBareCommandSpaceFirst ||
        element is CrystalMethodCallExpression ||
        element is CrystalBareMethodCallExpression ||
        element is CrystalDotCallAccess
