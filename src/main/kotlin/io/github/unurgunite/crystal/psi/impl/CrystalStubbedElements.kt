package io.github.unurgunite.crystal.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.createLeafFromText
import io.github.unurgunite.crystal.stubs.CrystalClassDefinitionStub
import io.github.unurgunite.crystal.stubs.CrystalConstantAssignmentStub
import io.github.unurgunite.crystal.stubs.CrystalEnumDefinitionStub
import io.github.unurgunite.crystal.stubs.CrystalMacroDefinitionStub
import io.github.unurgunite.crystal.stubs.CrystalMethodDefinitionStub
import io.github.unurgunite.crystal.stubs.CrystalModuleDefinitionStub
import io.github.unurgunite.crystal.stubs.CrystalStructDefinitionStub

// ==================== Helper ====================

// Tokens before the method name proper (`def self.foo`: DEF, SELF, DOT, whitespace).
private val METHOD_NAME_PREFIX_TOKENS =
    com.intellij.psi.tree.TokenSet.create(
        CrystalTypes.DEF,
        CrystalTypes.SELF,
        CrystalTypes.DOT,
    )

// Tokens ending the method name (parameter list, return type, `forall`, block end).
private val METHOD_NAME_END_TOKENS =
    com.intellij.psi.tree.TokenSet.create(
        CrystalTypes.LPAREN,
        CrystalTypes.COLON,
        CrystalTypes.FORALL,
        CrystalTypes.END,
        CrystalTypes.NEWLINE,
    )

private fun findNameIdentifierInTypeName(element: PsiElement): PsiElement? {
    // type_name is now private (inlined), CONSTANT tokens are direct children of the definition node.
    // We want the last CONSTANT token (the simple name, e.g. the last part of Foo::Bar)
    var lastConstant: PsiElement? = null
    var child = element.node.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.CONSTANT) {
            lastConstant = child.psi
        }
        child = child.treeNext
    }
    return lastConstant
}

private fun findNameIdentifierInMethodName(element: PsiElement): PsiElement? {
    // method_name is now private (inlined), IDENTIFIER/CONSTANT tokens are direct children of the definition node.
    // We want the first IDENTIFIER or CONSTANT token (the method name).
    // Skip SELF and DOT tokens for self.method definitions.
    var child = element.node.firstChildNode
    while (child != null) {
        if (child.elementType == CrystalTypes.IDENTIFIER || child.elementType == CrystalTypes.CONSTANT) {
            return child.psi
        }
        child = child.treeNext
    }
    return null
}

private fun getNameFromTypeName(element: PsiElement): String? = findNameIdentifierInTypeName(element)?.text

private fun getNameFromMethodName(element: PsiElement): String? {
    // Try to find IDENTIFIER or CONSTANT first (regular method names)
    val identifier = findNameIdentifierInMethodName(element)
    if (identifier != null) {
        // Setter methods (`def self.foo=(x)`) carry a trailing ASSIGN token in method_name;
        // include it so the method is uniquely named `foo=` and resolvable from `obj.foo = y`.
        val isSetter = element.node.getChildren(null).any { it.elementType == CrystalTypes.ASSIGN }
        return identifier.text + if (isSetter) "=" else ""
    }

    // Keyword / operator method names (e.g. `def self.next`, `def ==`, `def []`, `def {{m}}`):
    // method_name is inlined, so the name tokens are direct children of the definition node,
    // between the DEF/SELF/DOT prefix and the parameter list / return type. Collect only those
    // tokens; stop at the parameter list (LPAREN), return type (COLON), FORALL, END, or newline.
    val sb = StringBuilder()
    var child = element.node.firstChildNode
    while (child != null) {
        val type = child.elementType
        if (type in METHOD_NAME_END_TOKENS) {
            break
        }
        if (type !in METHOD_NAME_PREFIX_TOKENS && type != com.intellij.psi.TokenType.WHITE_SPACE &&
            type != CrystalTypes.NEWLINE
        ) {
            sb.append(child.psi.text)
        }
        child = child.treeNext
    }
    return sb.toString().takeIf { it.isNotEmpty() }
}

private fun setNameOnIdentifier(
    nameIdentifier: PsiElement?,
    name: String,
): PsiElement? {
    if (nameIdentifier == null) return null
    val tokenType = nameIdentifier.node.elementType
    val bareName = name.removePrefix("@").removePrefix("@")
    val fixedName =
        when (tokenType) {
            CrystalTypes.INSTANCE_VAR -> "@$bareName"
            CrystalTypes.CLASS_VAR -> "@@$bareName"
            else -> bareName
        }
    val newNode = createLeafFromText(nameIdentifier.project, fixedName, tokenType) ?: return null
    nameIdentifier.node.treeParent.replaceChild(nameIdentifier.node, newNode)
    return newNode.psi
}

// ==================== Type Definitions ====================

abstract class CrystalStubbedClassDefinitionImpl :
    StubBasedPsiElementBase<CrystalClassDefinitionStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalClassDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)

    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}

abstract class CrystalStubbedModuleDefinitionImpl :
    StubBasedPsiElementBase<CrystalModuleDefinitionStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalModuleDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)

    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}

abstract class CrystalStubbedStructDefinitionImpl :
    StubBasedPsiElementBase<CrystalStructDefinitionStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalStructDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)

    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}

abstract class CrystalStubbedEnumDefinitionImpl :
    StubBasedPsiElementBase<CrystalEnumDefinitionStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalEnumDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInTypeName(this)

    override fun getName(): String? = stub?.name ?: getNameFromTypeName(this)

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}

// ==================== Method / Macro ====================

abstract class CrystalStubbedMethodDefinitionImpl :
    StubBasedPsiElementBase<CrystalMethodDefinitionStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMethodDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInMethodName(this)

    override fun getName(): String? = stub?.name ?: getNameFromMethodName(this)

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}

abstract class CrystalStubbedMacroDefinitionImpl :
    StubBasedPsiElementBase<CrystalMacroDefinitionStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalMacroDefinitionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): PsiElement? = findNameIdentifierInMethodName(this)

    override fun getName(): String? = stub?.name ?: getNameFromMethodName(this)

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}

// ==================== Constant ====================

abstract class CrystalStubbedConstantAssignmentImpl :
    StubBasedPsiElementBase<CrystalConstantAssignmentStub>,
    CrystalNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: CrystalConstantAssignmentStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    // The defined constant is the LAST CONSTANT token (e.g. `PI` in `Math::PI`).
    override fun getNameIdentifier(): PsiElement? {
        var lastConstant: PsiElement? = null
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == CrystalTypes.CONSTANT) lastConstant = child.psi
            child = child.treeNext
        }
        return lastConstant
    }

    override fun getName(): String? = stub?.name ?: nameIdentifier?.text

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: node.startOffset

    override fun setName(name: String): PsiElement {
        setNameOnIdentifier(nameIdentifier, name)
        return this
    }
}
