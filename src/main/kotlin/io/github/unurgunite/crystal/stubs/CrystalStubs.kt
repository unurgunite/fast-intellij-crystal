package io.github.unurgunite.crystal.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalConstantAssignment
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalStructDefinition

// ==================== Stub Interfaces ====================

interface CrystalNamedStub {
    val name: String?
}

// ==================== Class Stub ====================

class CrystalClassDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `class Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null,
) : StubBase<CrystalClassDefinition>(parent, elementType),
    CrystalNamedStub

// ==================== Module Stub ====================

class CrystalModuleDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `module Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null,
) : StubBase<CrystalModuleDefinition>(parent, elementType),
    CrystalNamedStub

// ==================== Struct Stub ====================

class CrystalStructDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `struct Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null,
) : StubBase<CrystalStructDefinition>(parent, elementType),
    CrystalNamedStub

// ==================== Enum Stub ====================

class CrystalEnumDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** For qualified names like `enum Foo::Bar`, stores the namespace prefix ("Foo"). */
    val enclosingNamespace: String? = null,
) : StubBase<CrystalEnumDefinition>(parent, elementType),
    CrystalNamedStub

// ==================== Method Stub ====================

/**
 * The method's header facts are stubbed, not just its name: a StubIndex hit
 * must answer `getEnclosingClassName` / `isStaticMethod` / signatures without
 * loading the AST. Touching the AST of a stub-backed method throws
 * `UpToDateStubIndexMismatch` whenever the stored stub tree disagrees with the
 * current text (stale index), which is exactly when analysis runs.
 */
class CrystalMethodDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** Header type path of a reopening (`Nil` for `def Nil.new`), null for a plain method. */
    val typePath: String? = null,
    /** `def self.name` */
    val isStatic: Boolean = false,
    /** Parameter names and types, e.g. "(a : Int32, b)". */
    val parameterSignature: String? = null,
    /** Return type annotation text, null when absent. */
    val returnType: String? = null,
) : StubBase<CrystalMethodDefinition>(parent, elementType),
    CrystalNamedStub

// ==================== Macro Stub ====================

class CrystalMacroDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
) : StubBase<CrystalMacroDefinition>(parent, elementType),
    CrystalNamedStub

// ==================== Constant Stub ====================

class CrystalConstantAssignmentStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
) : StubBase<CrystalConstantAssignment>(parent, elementType),
    CrystalNamedStub

// ==================== Alias Stub ====================

class CrystalAliasDefinitionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
) : StubBase<io.github.unurgunite.crystal.psi.CrystalAliasDefinition>(parent, elementType),
    CrystalNamedStub
