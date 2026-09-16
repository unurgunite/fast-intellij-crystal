package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.inspections.CrystalCallArguments.DotCallInfo
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * DOT-call shape scanning (`Receiver.method(args)` where the args holder is not
 * wrapped in a call expression). Shared by the argument-count and type-check
 * inspections, which previously carried identical copies.
 */
internal object CrystalDotCallScan {
    /**
     * Detects if a `CrystalCallArgs`/`CrystalBareArgumentList` is part of a DOT-call pattern.
     * Returns null when the parent is already a call expression (handled elsewhere).
     */
    fun detectDotCall(argsElement: PsiElement): DotCallInfo? {
        val methodNameNode = findDetachedMethodName(argsElement) ?: return null
        val receiver = findDotCallReceiver(methodNameNode) ?: return null
        val receiverName = receiver.text ?: return null
        return DotCallInfo(receiverName, methodNameNode.text, methodNameNode)
    }

    /**
     * Method-name sibling before a detached args holder, or null when the holder
     * sits inside a regular call expression or no name precedes it.
     */
    fun findDetachedMethodName(argsElement: PsiElement): PsiElement? {
        // Skip if inside a method_call_expression (already handled)
        val parent = argsElement.parent
        if (CrystalPsiUtils.isCallExpression(parent)) {
            return null
        }

        val methodNameNode = CrystalPsiUtils.prevMeaningfulSibling(argsElement) ?: return null
        val methodType = methodNameNode.node?.elementType
        if (methodType != CrystalTypes.IDENTIFIER && methodType != CrystalTypes.CONSTANT) return null
        return methodNameNode
    }

    /** Receiver before `DOT methodName`, or null when the shape does not match. */
    fun findDotCallReceiver(methodNameNode: PsiElement): PsiElement? {
        val dot = CrystalPsiUtils.prevMeaningfulSibling(methodNameNode) ?: return null
        if (dot.node?.elementType != CrystalTypes.DOT) return null
        return CrystalPsiUtils.receiverBeforeDot(methodNameNode, dot)
    }
}
