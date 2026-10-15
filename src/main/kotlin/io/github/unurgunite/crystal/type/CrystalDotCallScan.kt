package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.type.CrystalCallArguments.DotCallInfo

/**
 * DOT-call shape scanning (`Receiver.method(args)` where the args holder is not
 * wrapped in a call expression). Shared by the argument-count and type-check
 * inspections, which previously carried identical copies.
 */
internal object CrystalDotCallScan {
    /**
     * Detects if a `CrystalCallArgs`/`CrystalBareArgumentList` is part of a DOT-call pattern.
     * Returns null when the parent is already a call expression (handled elsewhere),
     * or when the shape is a binary-operator tail (`Time.utc - 1.second` parses
     * as DOT_CALL `Time.utc` + detached `- 1.second`: `-` is an operator, not a
     * method name — checking it as a 1-arg call of a 0-param method is a false
     * "Too many arguments").
     */
    fun detectDotCall(argsElement: PsiElement): DotCallInfo? {
        val methodNameNode = findDetachedMethodName(argsElement) ?: return null
        if (isOperatorTail(argsElement, methodNameNode)) return null
        val receiver = findDotCallReceiver(methodNameNode) ?: return null
        val receiverName = receiver.text ?: return null
        return DotCallInfo(receiverName, methodNameNode.text, methodNameNode)
    }

    /**
     * True when [methodNameNode] is a binary operator whose operand list is the
     * detached [argsElement] (`- 1.second`, `+ x`, `* 2`): the args holder is
     * the operator's RHS, not a call argument list. Operator calls are
     * resolved through the receiver type, never through arity checks.
     * File-level helper keeps detectDotCall under detekt's complexity budget.
     */
    private fun isOperatorTail(
        argsElement: PsiElement,
        methodNameNode: PsiElement,
    ): Boolean = isOperatorToken(methodNameNode) && followsOperator(argsElement, methodNameNode)

    /**
     * Method-name sibling before a detached args holder, or null when the holder
     * sits inside a regular call expression or no name precedes it.
     * Binary operators (`-` in `Time.utc - 1.second`) also qualify: the
     * operator is the "method name" and the holder is its RHS — the operator
     * guard in detectDotCall filters that shape downstream.
     */
    fun findDetachedMethodName(argsElement: PsiElement): PsiElement? {
        // Skip if inside a method_call_expression (already handled)
        val parent = argsElement.parent
        if (CrystalPsiUtils.isCallExpression(parent)) {
            return null
        }

        val methodNameNode = CrystalPsiUtils.prevMeaningfulSibling(argsElement) ?: return null
        val methodType = methodNameNode.node?.elementType
        if (methodType != CrystalTypes.IDENTIFIER && methodType != CrystalTypes.CONSTANT &&
            !isOperatorToken(methodNameNode)
        ) {
            return null
        }
        return methodNameNode
    }

    /** Receiver before `DOT methodName`, or null when the shape does not match. */
    fun findDotCallReceiver(methodNameNode: PsiElement): PsiElement? {
        val dot = CrystalPsiUtils.prevMeaningfulSibling(methodNameNode) ?: return null
        if (dot.node?.elementType != CrystalTypes.DOT) return null
        return CrystalPsiUtils.receiverBeforeDot(methodNameNode, dot)
    }
}

/** Binary-operator token types (never method names). */
private fun isOperatorToken(methodNameNode: PsiElement): Boolean {
    val type = methodNameNode.node?.elementType ?: return false
    return type == CrystalTypes.MINUS || type == CrystalTypes.PLUS ||
        type == CrystalTypes.STAR || type == CrystalTypes.SLASH ||
        type == CrystalTypes.PERCENT
}

/** True when [argsElement]'s sibling chain starts at [methodNameNode] (its RHS). */
private fun followsOperator(
    argsElement: PsiElement,
    methodNameNode: PsiElement,
): Boolean {
    // The holder must be the operator's RHS (bare list starting at/after it).
    var sibling: PsiElement? = argsElement
    while (sibling != null && sibling !== methodNameNode) {
        sibling = sibling.prevSibling
    }
    return sibling === methodNameNode
}
