package io.github.unurgunite.crystal.psi.util

import com.intellij.psi.StubBasedPsiElement
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalMethodDefinitionStub

/**
 * Formats the parameter list of a method as a string like "(a : Int32, b)".
 *
 * Shared by the stub builder and the type lookups: the stub stores the string
 * at index time so a StubIndex hit never loads the method's AST just to read
 * its parameters.
 */
fun extractParameterSignature(method: CrystalMethodDefinition): String {
    val paramList = method.parameterList ?: return "()"
    val params = paramList.parameterList
    if (params.isEmpty()) return "()"

    val paramStrings =
        params.map { param ->
            val name = extractParameterName(param) ?: "?"
            val typeRef = param.typeReference
            if (typeRef != null) "$name : ${typeRef.text}" else name
        }
    return "(${paramStrings.joinToString(", ")})"
}

/** `def self.name` — the method is defined on the type, not on an instance. */
fun hasSelfReceiver(method: CrystalMethodDefinition): Boolean = method.node.findChildByType(CrystalTypes.SELF) != null

/** The method's stub when it came from the index, null for AST-backed PSI. */
val CrystalMethodDefinition.methodStub: CrystalMethodDefinitionStub?
    get() = (this as? StubBasedPsiElement<*>)?.stub as? CrystalMethodDefinitionStub
