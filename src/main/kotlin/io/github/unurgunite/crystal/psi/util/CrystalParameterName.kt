package io.github.unurgunite.crystal.psi.util

import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Extracts the parameter name from a [CrystalParameter] node.
 * Handles both normal parameters (`radius`) and shorthand instance
 * variable assignment (`@radius`) — the `@` prefix is stripped.
 *
 * @return the parameter name, or `null` if the parameter is a splat/block prefix
 */
fun extractParameterName(param: CrystalParameter): String? {
    // Normal case: IDENTIFIER child (e.g. `radius : Float64`)
    val identNode = param.node.findChildByType(CrystalTypes.IDENTIFIER)
    if (identNode != null) return identNode.text

    // Shorthand: INSTANCE_VAR_ACCESS child (e.g. `@radius : Float64`)
    // Strip the `@` prefix so it's treated like a normal parameter name
    val instanceVarNode = param.node.findChildByType(CrystalTypes.INSTANCE_VAR_ACCESS)
    if (instanceVarNode != null) {
        return stripAtPrefix(instanceVarNode.text)
    }

    return null
}

/** Shorthand `@name` parameter → `name`. */
private fun stripAtPrefix(varText: String): String = if (varText.startsWith("@")) varText.substring(1) else varText
