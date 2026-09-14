package io.github.unurgunite.crystal.navigation

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.isNameToken
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.prevMeaningfulLeaf
import io.github.unurgunite.crystal.navigation.CrystalBareCallScanner.prevMeaningfulSibling
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalDotCallAccess
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Receiver resolution for parameter info: the receiver of a DOT-call and the
 * class name of a `ClassName.new(...)` constructor call.
 */
internal object CrystalParameterInfoReceiver {
    /**
     * For dot-calls: find the receiver name (e.g., "ENV" in ENV.fetch(...)).
     */
    fun findReceiverNameFromSiblings(argsHolder: PsiElement): String? {
        val anchor = argsHolder as? CrystalParameterInfoAnchor
        val methodToken =
            if (anchor != null) {
                anchor.nameToken
            } else {
                prevMeaningfulSibling(argsHolder)?.takeIf { isNameToken(it) } ?: return null
            }
        val dot = prevMeaningfulSibling(methodToken)?.takeIf { it.node?.elementType == CrystalTypes.DOT } ?: return null
        return receiverNameBeforeDot(dot)
    }

    /**
     * Receiver expression before a DOT: a CONSTANT directly, a CONSTANT child of the
     * receiver node, or — after the BNF refactor (`dot_call_access` rule) — the sibling
     * before the DOT's composite parent in the flattened postfix sequence.
     */
    private fun receiverNameBeforeDot(dot: PsiElement): String? {
        val receiver = prevMeaningfulSibling(dot) ?: receiverBeforeDotCallAccess(dot) ?: return null
        if (receiver.node?.elementType == CrystalTypes.CONSTANT) return receiver.text
        return receiver.node?.findChildByType(CrystalTypes.CONSTANT)?.text
    }

    /** Receiver before the DOT's `dot_call_access` parent (flattened postfix sequence). */
    private fun receiverBeforeDotCallAccess(dot: PsiElement): PsiElement? {
        val dotParent = dot.parent
        if (dotParent !is CrystalDotCallAccess) return null
        return prevMeaningfulSibling(dotParent)
    }

    /**
     * For "ClassName.new(...)" — finds the class name (CONSTANT) before ".new".
     * Returns the class name string, or null if not a class constructor call.
     */
    fun findClassNameBeforeNew(argsHolder: PsiElement): String? {
        val newToken = findNewToken(argsHolder) ?: return null
        // Look backwards: newToken -> DOT -> CONSTANT
        val dot = prevMeaningfulLeaf(newToken)?.takeIf { it.node?.elementType == CrystalTypes.DOT } ?: return null
        val prev = prevMeaningfulLeaf(dot) ?: return null
        // Handle both raw CONSTANT tokens and wrapped elements (e.g. CrystalVariableReferenceImpl)
        if (prev.node?.elementType == CrystalTypes.CONSTANT) return prev.text
        return prev.node?.findChildByType(CrystalTypes.CONSTANT)?.text
    }

    /**
     * The `new` method-name token for a constructor call: the anchor's name token
     * directly, or the IDENTIFIER("new") sibling before structured args.
     */
    private fun findNewToken(argsHolder: PsiElement): PsiElement? {
        if (argsHolder is CrystalParameterInfoAnchor) return argsHolder.nameToken
        if (argsHolder !is CrystalCallArgs && argsHolder !is CrystalBareArgumentList) return null
        val sibling = prevMeaningfulSibling(argsHolder) ?: return null
        if (sibling.node?.elementType != CrystalTypes.IDENTIFIER || sibling.text != "new") return null
        return sibling
    }

    fun findEnclosingTypeName(method: CrystalMethodDefinition): String? = CrystalCompletionHelper.getEnclosingClassName(method)
}
