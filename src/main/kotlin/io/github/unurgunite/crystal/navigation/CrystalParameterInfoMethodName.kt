package io.github.unurgunite.crystal.navigation

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.isNameToken
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.prevMeaningfulSibling
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Method-name resolution for parameter info: which method is being called at
 * an args-holder position. Split out of the former `CrystalParameterInfoLookup`
 * (receiver shapes live in [CrystalParameterInfoReceiver], cursor indexing in
 * [CrystalParameterInfoIndex]).
 */
internal object CrystalParameterInfoMethodName {
    /**
     * Resolves the method name for a given args holder by examining its PSI context.
     */
    fun findMethodNameForArgs(argsHolder: PsiElement): String? {
        // Case: synthetic anchor from bare-call or broken paren-call
        if (argsHolder is CrystalParameterInfoAnchor) {
            return argsHolder.nameToken.text
        }

        // Case: bare-call backtracking returned the IDENTIFIER/CONSTANT leaf as anchor (legacy)
        val holderType = argsHolder.node?.elementType
        if (holderType == CrystalTypes.IDENTIFIER || holderType == CrystalTypes.CONSTANT) {
            return argsHolder.text
        }

        return findMethodNameInCallContext(argsHolder)
    }

    /**
     * Method name for structured holders (`CrystalCallArgs`, `CrystalBareArgumentList`,
     * whole call expressions) and broken-PSI fallbacks. Order matters: the sibling
     * lookup runs before the self/parent fallbacks, matching the original dispatch.
     */
    private fun findMethodNameInCallContext(argsHolder: PsiElement): String? {
        val parent = argsHolder.parent
        if (isCallArgsInCallExpression(argsHolder, parent)) {
            return extractIdentifierFromCallExpression(parent!!)
        }
        return findMethodNameFromSiblings(argsHolder)
            ?: extractFromSelfOrParentCall(argsHolder, parent)
            ?: findIdentifierBeforeCallParen(argsHolder)
    }

    private fun isCallArgsInCallExpression(
        argsHolder: PsiElement,
        parent: PsiElement?,
    ): Boolean =
        (argsHolder is CrystalCallArgs || argsHolder is CrystalBareArgumentList) &&
            (parent is CrystalMethodCallExpression || parent is CrystalBareMethodCallExpression)

    /**
     * The holder (or its parent) IS a call expression — extract the callee name.
     * Runs after the sibling lookup, before the paren-scan fallback.
     */
    private fun extractFromSelfOrParentCall(
        argsHolder: PsiElement,
        parent: PsiElement?,
    ): String? {
        val target =
            when {
                argsHolder is CrystalMethodCallExpression -> argsHolder
                parent is CrystalMethodCallExpression || parent is CrystalBareMethodCallExpression -> parent
                else -> null
            } ?: return null
        return extractIdentifierFromCallExpression(target)
    }

    /**
     * Looks for an IDENTIFIER child before the first LPAREN (broken-PSI fallback).
     */
    private fun findIdentifierBeforeCallParen(argsHolder: PsiElement): String? {
        // Look for IDENTIFIER child before LPAREN
        var child = argsHolder.firstChild
        var lastIdentifier: String? = null
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
                lastIdentifier = child.text
            }
            if (type == CrystalTypes.LPAREN) {
                return lastIdentifier
            }
            child = child.nextSibling
        }
        return null
    }

    fun extractIdentifierFromCallExpression(expression: PsiElement): String? {
        // For dot-calls (Foo.bar, obj.method): return the IDENTIFIER after the last DOT
        var child = expression.firstChild
        var foundDot = false
        var lastNameBeforeDot: String? = null
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (isNameToken(child)) {
                if (foundDot) return child.text
                lastNameBeforeDot = child.text
            }
            child = child.nextSibling
        }
        return lastNameBeforeDot
    }

    /**
     * For dot-calls: look at siblings before the args holder to find DOT + IDENTIFIER.
     * A DOT before the name confirms a dot-call; without it the name still stands
     * (bare call whose args holder follows the method name directly) — either way
     * the sibling text is the method name.
     */
    fun findMethodNameFromSiblings(argsHolder: PsiElement): String? {
        val sibling = prevMeaningfulSibling(argsHolder)?.takeIf { isNameToken(it) } ?: return null
        return sibling.text
    }
}
