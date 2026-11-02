package io.github.unurgunite.crystal.type

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalArgument
import io.github.unurgunite.crystal.psi.CrystalBareArgument
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBareCommandSpaceFirst
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalDotCallAccess
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * Call-shape model shared by the argument-count and type-check inspections.
 * Both inspections previously carried their own copy of this extraction logic;
 * the copies drifted (splat resolution, `out` handling), so it lives here now.
 * Splat value resolution lives in [CrystalSplatResolver].
 */
internal object CrystalCallArguments {
    /**
     * One call argument: the wrapper element, its named label, shape flags and
     * the value expression for type resolution (defaults to the element itself).
     */
    data class ArgumentInfo(
        val element: PsiElement,
        val name: String? = null,
        val isSplat: Boolean = false,
        val isDoubleSplat: Boolean = false,
        val isBlockPass: Boolean = false,
        /** `{}`/`do` block body: never a positional argument in Crystal */
        val isBlock: Boolean = false,
        /** For splat args resolved to tuple literals: the element count */
        val resolvedSplatCount: Int? = null,
        /** For double-splat args resolved to named tuple literals: the key names */
        val resolvedDoubleSplatKeys: Set<String>? = null,
        /** Value expression for type resolution (unwrapped from the element when known). */
        val expression: PsiElement = element,
    )

    /** Receiver + method name of a DOT-call args holder (`Foo.bar(...)` shapes). */
    data class DotCallInfo(
        val receiverName: String,
        val methodName: String,
        val methodNameElement: PsiElement,
    )

    /**
     * Arguments of a structured call expression (parenthesized, bare command or
     * bare method call). Returns empty for anything else.
     */
    fun extractArguments(callExpr: PsiElement): List<ArgumentInfo> {
        val result = mutableListOf<ArgumentInfo>()
        when (callExpr) {
            is CrystalMethodCallExpression -> {
                val callArgs = callExpr.callArgs
                if (callArgs != null) {
                    val argList = callArgs.argumentList
                    if (argList != null) {
                        for (arg in argList.argumentList) {
                            result.add(extractShapedArgInfo(arg))
                        }
                    }
                    appendBlockArg(callExpr.block, result)
                    return result
                }
            }

            is CrystalBareCommandExpression -> {
                extractFromBareArgList(callExpr.bareArgumentList, result)
                appendBlockArg(callExpr.block, result)
            }

            is CrystalBareCommandSpaceFirst -> {
                extractFromBareArgList(callExpr.bareArgumentList, result)
                appendBlockArg(callExpr.block, result)
            }

            is CrystalBareMethodCallExpression -> {
                val argList = callExpr.callArgs?.argumentList
                if (argList != null) {
                    for (arg in argList.argumentList) {
                        result.add(extractShapedArgInfo(arg))
                    }
                }
            }

            else -> {
                // The visitor hands us the real call shape already
                // (METHOD_CALL_EXPRESSION, never an EXPRESSION shell — the
                // visitor dispatches on exact PSI classes). Anything else
                // here means a new shape arrived: return empty, don't guess.
            }
        }
        return result
    }

    /**
     * Arguments of a bare args holder (`CrystalCallArgs` / `CrystalBareArgumentList`),
     * e.g. inside DOT-call handling.
     */
    fun extractArgumentsFromArgsElement(argsElement: PsiElement): List<ArgumentInfo> {
        val result = mutableListOf<ArgumentInfo>()
        when (argsElement) {
            is CrystalCallArgs -> {
                val argList = argsElement.argumentList
                if (argList != null) {
                    for (arg in argList.argumentList) {
                        result.add(extractShapedArgInfo(arg))
                    }
                }
            }

            is CrystalBareArgumentList -> {
                for (bareArg in argsElement.bareArgumentList) {
                    result.add(extractShapedArgInfo(bareArg))
                }
            }
        }
        return result
    }

    /**
     * For DOT-calls via args element (e.g. Foo.new(...)) — finds the class name.
     */
    fun findClassNameBeforeNewFromArgs(argsElement: PsiElement): String? {
        val methodNameNode = CrystalDotCallScan.findDetachedMethodName(argsElement) ?: return null
        if (methodNameNode.text != "new") return null
        val receiver = CrystalDotCallScan.findDotCallReceiver(methodNameNode) ?: return null
        return classNameFromElement(receiver)
    }

    /** Method name of a call expression: after DOT when present, else the leading name. */
    fun extractMethodName(callExpr: PsiElement): String? {
        var child = callExpr.firstChild
        var lastNameBeforeDot: String? = null
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child.text
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameBeforeDot = child.text
            }
            child = child.nextSibling
        }
        return lastNameBeforeDot
    }

    /**
     * For "ClassName.new(...)" — finds the class name (CONSTANT) before ".new".
     * Works for CrystalMethodCallExpression / CrystalBareMethodCallExpression.
     */
    fun findClassNameBeforeNew(callExpr: PsiElement): String? {
        // `Source.new(...)` parses as EXPRESSION > VARIABLE_REFERENCE 'Source' +
        // DOT_CALL_ACCESS, so the receiver is a separate sibling of the dot-hold
        // rather than a child of the call: resolve it from there.
        val receiverFirst = callExpr.firstChild
        if (receiverFirst != null && receiverFirst.node?.elementType == CrystalTypes.VARIABLE_REFERENCE) {
            receiverClassNameAfterDotCallAccess(receiverFirst)?.let { return it }
        }
        var child = callExpr.firstChild
        var foundDot = false
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && type == CrystalTypes.IDENTIFIER && child.text == "new") {
                // Found ".new", now look before the DOT for CONSTANT
                var beforeDot = child.prevSibling
                while (beforeDot is com.intellij.psi.PsiWhiteSpace) beforeDot = beforeDot.prevSibling
                return classNameFromElement(beforeDot) ?: classNameFromElement(receiverFirst)
            }
            child = child.nextSibling
        }
        return null
    }

    /** `.new` inside the DOT_CALL_ACCESS sibling of [receiver]: the class name. */
    private fun receiverClassNameAfterDotCallAccess(receiver: PsiElement): String? {
        var sibling = receiver.nextSibling
        while (sibling != null) {
            val node = sibling.node
            if (node != null && node.elementType == CrystalTypes.DOT_CALL_ACCESS) {
                val dot = node.findChildByType(CrystalTypes.DOT) ?: return null
                val name = dot.treeNext
                if (name != null && name.elementType == CrystalTypes.IDENTIFIER && name.text == "new") {
                    return classNameFromElement(receiver)
                }
            }
            sibling = sibling.nextSibling
        }
        return null
    }

    /**
     * Best PSI element to highlight for an argument problem.
     * Unwraps wrapper elements (CrystalBareArgument, CrystalArgument) to the literal/expression.
     */
    fun highlightTarget(element: PsiElement): PsiElement = highlightTargetOf(element)
}

/**
 * Prefix-token shape of one argument's children.
 */
private data class ArgShapeFlags(
    val isSplat: Boolean = false,
    val isDoubleSplat: Boolean = false,
    val isBlockPass: Boolean = false,
) {
    /** Resolved tuple size for splat shapes, null otherwise. */
    fun resolveSplatCount(arg: PsiElement): Int? = if (isSplat) CrystalSplatResolver.resolveSplatCount(arg) else null

    /** Resolved key set for double-splat shapes, null otherwise. */
    fun resolveDoubleSplatKeys(arg: PsiElement): Set<String>? =
        if (isDoubleSplat) CrystalSplatResolver.resolveDoubleSplatKeys(arg) else null
}

/**
 * CONSTANT text of an element: direct token or a wrapper
 * (CrystalVariableReferenceImpl, CrystalTypePathImpl) holding one.
 */
private fun classNameFromElement(element: PsiElement?): String? = firstConstantText(element?.node)

/**
 * First CONSTANT token in [node]'s subtree. Nested shapes matter: a generic
 * receiver like `Hash(String, Int32)` is TYPE_RECEIVER_EXPRESSION > TYPE_PATH >
 * CONSTANT, two levels below the call, so a shallow child scan misses it.
 */
private fun firstConstantText(node: com.intellij.lang.ASTNode?): String? {
    if (node == null) return null
    if (node.elementType == CrystalTypes.CONSTANT) return node.text
    // Do not descend into type arguments: `Hash(String, ...)` keeps its own name.
    var child = node.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.TYPE_ARGUMENTS) break
        firstConstantText(child)?.let { return it }
        child = child.treeNext
    }
    return null
}

/** Named label for plain arguments (never for splat/block-pass shapes). */
private fun namedLabelIfPlain(
    arg: PsiElement,
    shaped: ArgShapeFlags,
): String? {
    if (shaped.isSplat || shaped.isDoubleSplat || shaped.isBlockPass) return null
    return findNamedLabel(arg.node.getChildren(null))
}

/**
 * Declared `expression` child of an argument wrapper, else the wrapper
 * itself with transparent EXPRESSION shells peeled (file-level: detekt budget).
 * NOTE: declared child returned WHOLE (no peeling inside): it already holds
 * the full value shape (`HASH DOT_CALL` siblings for `hash.to_json`).
 */
private fun unwrapArgumentValue(arg: PsiElement): PsiElement {
    (arg as? CrystalArgument)?.expression?.let { return it }
    var current: PsiElement = arg
    while (current is CrystalExpression) {
        val inner = CrystalPsiUtils.firstSignificantChild(current) ?: break
        if (inner is CrystalDotCallAccess) return inner
        current = inner
    }
    if (current is CrystalDotCallAccess) return current
    var sibling = current.nextSibling
    while (sibling != null) {
        if (sibling is CrystalDotCallAccess) return sibling
        sibling = sibling.nextSibling
    }
    return current
}

/** `IDENTIFIER COLON` label inside an argument's children, if present. */
private fun findNamedLabel(children: Array<ASTNode>): String? {
    for (i in children.indices) {
        if (children[i].elementType == CrystalTypes.COLON && i > 0 &&
            children[i - 1].elementType == CrystalTypes.IDENTIFIER
        ) {
            return children[i - 1].text
        }
    }
    return null
}

/**
 * Highlight target for an argument problem: first meaningful child of
 * wrapper elements, else the element itself (file-level: detekt budget).
 */
private fun highlightTargetOf(element: PsiElement): PsiElement {
    if (element is CrystalBareArgument || element is CrystalArgument) {
        return CrystalPsiUtils.firstSignificantChild(element) ?: element
    }
    return element
}

/** Block body as zero-count argument marker (file-level: detekt budget). */
private fun appendBlockArg(
    block: PsiElement?,
    result: MutableList<CrystalCallArguments.ArgumentInfo>,
) {
    if (block != null) result.add(CrystalCallArguments.ArgumentInfo(element = block, isBlock = true))
}

/** Shape flags + named label + value expression of bare arg lists (file-level: detekt budget). */
private fun extractFromBareArgList(
    bareArgList: CrystalBareArgumentList?,
    result: MutableList<CrystalCallArguments.ArgumentInfo>,
) {
    if (bareArgList != null) {
        for (bareArg in bareArgList.bareArgumentList) {
            result.add(extractShapedArgInfo(bareArg))
        }
    }
}

/** Single argument wrapper shaped into ArgumentInfo (file-level: detekt budget). */
private fun extractShapedArgInfo(arg: PsiElement): CrystalCallArguments.ArgumentInfo {
    // `{}` block parsed as a bare tuple-shaped argument (`at_exit { d.close }`):
    // GrammarKit reads `{ d.close }` as TUPLE_LITERAL (no BLOCK node — the
    // bare tower has no block alternative in this position). A brace group
    // whose content is a bare command (callee + args, not a value) is a
    // block body, never a positional argument in Crystal — mark it so arity
    // math skips it (same marker as structured-call block bodies).
    if (isBareBlockTuple(arg)) {
        return CrystalCallArguments.ArgumentInfo(element = arg, isBlock = true)
    }
    val shaped = shapeFlags(arg.node.getChildren(null))
    // Unwrap single-child wrappers to the value expression: ARGUMENT holds
    // the value directly for paren calls, but some shapes nest one level
    // (e.g. a DOT-call fragment inside the argument). Without unwrapping,
    // resolveType sees the ARGUMENT shell and misses the call shape.
    val valueExpression = unwrapArgumentValue(arg)

    return CrystalCallArguments.ArgumentInfo(
        element = arg,
        name = namedLabelIfPlain(arg, shaped),
        isSplat = shaped.isSplat,
        isDoubleSplat = shaped.isDoubleSplat,
        isBlockPass = shaped.isBlockPass,
        resolvedSplatCount = shaped.resolveSplatCount(arg),
        resolvedDoubleSplatKeys = shaped.resolveDoubleSplatKeys(arg),
        expression = valueExpression,
    )
}

/** Prefix-token shape of one argument's children (file-level: detekt budget). */
private fun shapeFlags(children: Array<com.intellij.lang.ASTNode>): ArgShapeFlags {
    when (children.firstOrNull()?.elementType) {
        CrystalTypes.STAR -> return ArgShapeFlags(isSplat = true)
        CrystalTypes.DOUBLE_STAR -> return ArgShapeFlags(isDoubleSplat = true)
        CrystalTypes.AMPERSAND -> return ArgShapeFlags(isBlockPass = true)
    }
    return ArgShapeFlags()
}
