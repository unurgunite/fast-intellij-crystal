package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalHashLiteral
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Resolves `*args` / `**options` against tuple/named-tuple literals assigned
 * in scope, so arity checks can count through splats.
 */
internal object CrystalSplatResolver {
    /**
     * For a splat argument (*expr), try to resolve the expression to a tuple literal
     * and return its element count. Returns null if not resolvable.
     */
    fun resolveSplatCount(argElement: PsiElement): Int? {
        val varName = findSplatVariableName(argElement) ?: return null
        val tupleLiteral = resolveVariableToLiteral(argElement, varName) ?: return null
        return countTupleElements(tupleLiteral)
    }

    /**
     * For a double-splat argument (**expr), try to resolve the expression to a named tuple
     * literal and return its key names. Returns null if not resolvable.
     */
    fun resolveDoubleSplatKeys(argElement: PsiElement): Set<String>? {
        val varName = findSplatVariableName(argElement) ?: return null
        val literal = resolveVariableToLiteral(argElement, varName) ?: return null
        return extractNamedTupleKeys(literal)
    }

    /**
     * Extract the variable name from a splat/double-splat argument.
     * For `*args` or `**options`, returns "args" or "options".
     */
    private fun findSplatVariableName(argElement: PsiElement): String? {
        // The argument node children include STAR/DOUBLE_STAR followed by EXPRESSION(VARIABLE_REFERENCE(IDENTIFIER))
        val children = argElement.node.getChildren(null)
        var foundSplat = false
        for (child in children) {
            val type = child.elementType
            if (type == CrystalTypes.STAR || type == CrystalTypes.DOUBLE_STAR) {
                foundSplat = true
            } else if (foundSplat && type == CrystalTypes.EXPRESSION) {
                return extractVariableName(child.psi)
            }
        }
        return null
    }

    /** Identifier inside `EXPRESSION(VARIABLE_REFERENCE(IDENTIFIER))` or a direct IDENTIFIER. */
    private fun extractVariableName(expression: PsiElement): String? {
        val varRef = expression.node.findChildByType(CrystalTypes.VARIABLE_REFERENCE)
        if (varRef != null) {
            val id = varRef.findChildByType(CrystalTypes.IDENTIFIER)
            return id?.text
        }
        // Direct IDENTIFIER
        val id = expression.node.findChildByType(CrystalTypes.IDENTIFIER)
        return id?.text
    }

    /**
     * Resolve a variable name to its assignment literal in the same scope.
     * Searches backwards from the usage site for `varName = <literal>`.
     * Returns the RHS expression element (tuple/hash literal) or null.
     */
    private fun resolveVariableToLiteral(
        usageSite: PsiElement,
        varName: String,
    ): PsiElement? {
        // Walk up to statement level
        var current: PsiElement? = usageSite
        while (current != null && !isStatementLevel(current)) {
            current = current.parent
        }
        if (current == null) return null

        // Search preceding siblings
        var sibling = current.prevSibling
        while (sibling != null) {
            val result = findAssignmentRhsForVar(sibling, varName)
            if (result != null) return result
            sibling = sibling.prevSibling
        }

        return null
    }

    /**
     * True when [element] sits at statement level: itself a STATEMENT, a direct
     * child of a STATEMENT_LIST, or a direct child of the file root. Used to stop
     * the walk-up in [resolveVariableToLiteral].
     */
    private fun isStatementLevel(element: PsiElement): Boolean {
        if (element.node.elementType == CrystalTypes.STATEMENT) return true
        if (element.parent?.node?.elementType == CrystalTypes.STATEMENT_LIST) return true
        return element.parent
            ?.node
            ?.elementType
            ?.toString() == "FILE"
    }

    /**
     * In an element subtree, find an assignment `varName = expr` and return the RHS expression.
     */
    private fun findAssignmentRhsForVar(
        element: PsiElement,
        varName: String,
    ): PsiElement? {
        if (element is CrystalAssignment) {
            // CrystalAssignment node children: IDENTIFIER, ASSIGN, expression
            val idNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            if (idNode != null && idNode.text == varName) {
                // Find the expression child (RHS)
                val exprNode = element.node.findChildByType(CrystalTypes.EXPRESSION)
                return exprNode?.psi
            }
        }

        // Recurse into children
        var child = element.firstChild
        while (child != null) {
            val result = findAssignmentRhsForVar(child, varName)
            if (result != null) return result
            child = child.nextSibling
        }
        return null
    }

    /** Count elements in a tuple literal: {a, b, c} -> 3. */
    private fun countTupleElements(literal: PsiElement): Int? {
        // Direct tuple literal
        if (literal.node.elementType == CrystalTypes.TUPLE_LITERAL) {
            return countTupleExpressions(literal)
        }
        // Hash literal used as named tuple {x: 1, y: 2}
        if (literal is CrystalHashLiteral) {
            val entryList = literal.hashEntryList ?: return null
            return entryList.hashEntryList.size
        }
        // Expression wrapper — unwrap
        if (literal.node.elementType == CrystalTypes.EXPRESSION) {
            val child = literal.firstChild
            if (child != null) return countTupleElements(child)
        }
        return null
    }

    /** EXPRESSION children of a tuple literal (via EXPRESSION_LIST when present). */
    private fun countTupleExpressions(literal: PsiElement): Int {
        val exprList = literal.node.findChildByType(CrystalTypes.EXPRESSION_LIST)
        if (exprList != null) {
            return exprList.getChildren(null).count { it.elementType == CrystalTypes.EXPRESSION }
        }
        // Or count top-level EXPRESSION children directly
        return literal.node.getChildren(null).count { it.elementType == CrystalTypes.EXPRESSION }
    }

    /**
     * Extract keys from a named tuple literal: {host: "x", port: 8080} -> {"host", "port"}
     */
    private fun extractNamedTupleKeys(literal: PsiElement): Set<String>? {
        if (literal is CrystalHashLiteral) {
            return collectHashKeys(literal)
        }
        // Expression wrapper — unwrap
        if (literal.node.elementType == CrystalTypes.EXPRESSION) {
            val child = literal.firstChild
            if (child != null) return extractNamedTupleKeys(child)
        }
        return null
    }

    /** Key identifiers of a hash/named-tuple literal, or null when empty. */
    private fun collectHashKeys(literal: CrystalHashLiteral): Set<String>? {
        val entryList = literal.hashEntryList ?: return null
        val keys = mutableSetOf<String>()
        for (entry in entryList.hashEntryList) {
            // Named tuple entry: EXPRESSION(VARIABLE_REFERENCE(IDENTIFIER)) COLON EXPRESSION
            val firstExpr = entry.expressionList.firstOrNull() ?: continue
            val varRef = firstExpr.firstChild
            if (varRef is io.github.unurgunite.crystal.psi.CrystalVariableReference) {
                val id = varRef.node.findChildByType(CrystalTypes.IDENTIFIER)
                if (id != null) keys.add(id.text)
            }
        }
        return if (keys.isNotEmpty()) keys else null
    }
}
