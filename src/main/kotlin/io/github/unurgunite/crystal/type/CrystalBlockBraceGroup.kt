package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareCommandSpaceFirst
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalDotCallAccess
import io.github.unurgunite.crystal.psi.CrystalHashLiteral
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalTupleLiteral
import io.github.unurgunite.crystal.psi.CrystalTypes
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
            element.expressionList.expressionList.let { it.size <= 1 && it.all(::holdsCallShape) }
        }

        else -> {
            group.any(::holdsCallShape)
        }
    }

private val WHITESPACE = "\\s".toRegex()

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
