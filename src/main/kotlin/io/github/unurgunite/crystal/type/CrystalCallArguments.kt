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
                    return result
                }
            }

            is CrystalBareCommandExpression -> {
                extractFromBareArgList(callExpr.bareArgumentList, result)
            }

            is CrystalBareCommandSpaceFirst -> {
                extractFromBareArgList(callExpr.bareArgumentList, result)
            }

            is CrystalBareMethodCallExpression -> {
                val argList = callExpr.callArgs?.argumentList
                if (argList != null) {
                    for (arg in argList.argumentList) {
                        result.add(extractShapedArgInfo(arg))
                    }
                }
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
     * Shape flags + named label + value expression of one argument wrapper
     * (`CrystalArgument` or `CrystalBareArgument`).
     */
    private fun extractFromBareArgList(
        bareArgList: CrystalBareArgumentList?,
        result: MutableList<ArgumentInfo>,
    ) {
        if (bareArgList != null) {
            for (bareArg in bareArgList.bareArgumentList) {
                result.add(extractShapedArgInfo(bareArg))
            }
        }
    }

    private fun extractShapedArgInfo(arg: PsiElement): ArgumentInfo {
        val children = arg.node.getChildren(null)
        var isSplat = false
        var isDoubleSplat = false
        var isBlockPass = false

        when (children.firstOrNull()?.elementType) {
            CrystalTypes.STAR -> isSplat = true
            CrystalTypes.DOUBLE_STAR -> isDoubleSplat = true
            CrystalTypes.AMPERSAND -> isBlockPass = true
        }
        val namedLabel =
            if (!isSplat && !isDoubleSplat && !isBlockPass) findNamedLabel(children) else null

        val resolvedSplatCount = if (isSplat) CrystalSplatResolver.resolveSplatCount(arg) else null
        val resolvedDoubleSplatKeys = if (isDoubleSplat) CrystalSplatResolver.resolveDoubleSplatKeys(arg) else null
        val valueExpression = (arg as? CrystalArgument)?.expression ?: arg

        return ArgumentInfo(
            arg,
            namedLabel,
            isSplat,
            isDoubleSplat,
            isBlockPass,
            resolvedSplatCount,
            resolvedDoubleSplatKeys,
            valueExpression,
        )
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
     * For DOT-calls via args element (e.g. Foo.new(...)) — finds the class name.
     */
    fun findClassNameBeforeNewFromArgs(argsElement: PsiElement): String? {
        val methodNameNode = CrystalDotCallScan.findDetachedMethodName(argsElement) ?: return null
        if (methodNameNode.text != "new") return null
        val receiver = CrystalDotCallScan.findDotCallReceiver(methodNameNode) ?: return null
        return extractClassNameFromElement(receiver)
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
                return extractClassNameFromElement(beforeDot)
            }
            child = child.nextSibling
        }
        return null
    }

    /**
     * Extracts a class name (CONSTANT text) from a PSI element.
     * Handles both raw CONSTANT tokens and CrystalVariableReferenceImpl/TypePathImpl wrappers.
     */
    private fun extractClassNameFromElement(element: PsiElement?): String? {
        if (element == null) return null
        // Direct CONSTANT token
        if (element.node?.elementType == CrystalTypes.CONSTANT) {
            return element.text
        }
        // Wrapper (e.g. CrystalVariableReferenceImpl, CrystalTypePathImpl) containing a CONSTANT child
        val constantChild = element.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) {
            return constantChild.text
        }
        return null
    }

    /**
     * Best PSI element to highlight for an argument problem.
     * Unwraps wrapper elements (CrystalBareArgument, CrystalArgument) to the literal/expression.
     */
    fun highlightTarget(element: PsiElement): PsiElement {
        if (element is CrystalBareArgument || element is CrystalArgument) {
            return CrystalPsiUtils.firstSignificantChild(element) ?: element
        }
        return element
    }
}
