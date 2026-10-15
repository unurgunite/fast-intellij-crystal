package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.extractParameterName
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex

/**
 * Method-shape and index queries over the type system: constructor lookup,
 * enclosing-type names, parameter/return signatures. Leaf of the dependency
 * graph — depends only on PSI, stubs and `psi.util`, never on completion or
 * inspections. Split out of `CrystalCompletionHelper` / `CrystalLookupBuilders`.
 */
object CrystalMethodLookup {
    /**
     * First `initialize` overload of a class/struct (compat shim — new code
     * prefers [getInitializeMethods]). Guarded by hierarchy membership: the
     * class index keys by SHORT name, so `Error` collides `Redis::Error`
     * with `OpenSSL::Error` — a foreign `initialize(ssl, return_code)`
     * must not shadow the real constructor.
     */
    fun getInitializeMethod(
        typeName: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): CrystalMethodDefinition? {
        val all = getInitializeMethods(typeName, project, currentFile)
        if (all.size <= 1) return all.firstOrNull()
        // Several same-named classes (`Error` in redis/openssl): keep only
        // overloads from the resolved type's own hierarchy.
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile) ?: return all.firstOrNull()
        val hierarchySignatures =
            CrystalTypeHierarchy
                .getMethodsFromType(result)
                .filter { it.name == "initialize" }
                .map { getParameterSignature(it) }
                .toSet()
        return all.firstOrNull { getParameterSignature(it) in hierarchySignatures } ?: all.firstOrNull()
    }

    /**
     * All `initialize` overloads of a class/struct visible from the project:
     * direct definitions plus inherited ones. Plural: matching `.new` against
     * only the FIRST overload mistyped `Array(String).new(initial_capacity:)`
     * against empty `Array#initialize` while `initialize(initial_capacity)`
     * sits right below it. Callers best-match across the list.
     */
    fun getInitializeMethods(
        typeName: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): List<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.allScope(project)
        val direct =
            StubIndex
                .getElements(
                    CrystalMethodByClassIndex.KEY,
                    typeName,
                    project,
                    scope,
                    CrystalMethodDefinition::class.java,
                ).filter { it.name == "initialize" }
        if (direct.size > 1) return direct
        // Zero or one direct hit: fall back to hierarchy (inherited overloads).
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile) ?: return direct
        val inherited = CrystalTypeHierarchy.getMethodsFromType(result).filter { it.name == "initialize" }
        return (direct + inherited).distinctBy { getParameterSignature(it) }
    }

    /**
     * True when [method] belongs to [typeName]'s own hierarchy (the type
     * itself or one of its ancestors). File-level shape keeps
     * getInitializeMethod under complexity budget. Compared by signature
     * (index hits may be distinct PSI instances of one definition).
     */
    private fun isInHierarchyOf(
        method: CrystalMethodDefinition,
        typeName: String,
        project: Project,
        currentFile: PsiFile?,
    ): Boolean {
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile) ?: return true
        val hierarchyInits = CrystalTypeHierarchy.getMethodsFromType(result).filter { it.name == "initialize" }
        if (hierarchyInits.isEmpty()) return true
        val signature = getParameterSignature(method)
        return hierarchyInits.any { getParameterSignature(it) == signature }
    }

    /** `initialize` defined directly on [typeName], without hierarchy traversal. */
    private fun directInitialize(
        typeName: String,
        project: Project,
    ): CrystalMethodDefinition? {
        val scope = GlobalSearchScope.allScope(project)
        return StubIndex
            .getElements(
                CrystalMethodByClassIndex.KEY,
                typeName,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            ).firstOrNull { it.name == "initialize" }
    }

    /**
     * Returns the enclosing type name of a method, or null if top-level.
     *
     * NOTE: never touches the AST. This runs on StubIndex hits whose stubs may
     * disagree with a freshly parsed tree (UpToDateStubIndexMismatch): any
     * getParentOfType/getNode call here forces stub↔AST reconciliation and
     * throws AssertionError out of inspections. Stub-only walk instead.
     */
    fun getEnclosingClassName(method: CrystalMethodDefinition): String? {
        val stub = (method as? com.intellij.psi.StubBasedPsiElement<*>)?.stub
        if (stub != null) return enclosingNameFromStub(stub)
        return enclosingNameFromPsi(method)
    }

    /** Stub-tree walk: enclosing named stub (class/module/struct/enum). */
    private fun enclosingNameFromStub(stub: com.intellij.psi.stubs.StubElement<*>): String? {
        var parent = stub.parentStub
        while (parent != null) {
            val named = parent as? io.github.unurgunite.crystal.stubs.CrystalNamedStub
            if (named?.name != null) return named.name
            parent = parent.parentStub
        }
        return null
    }

    /** PSI fallback for non-stub elements (tests, in-memory files). */
    private fun enclosingNameFromPsi(method: CrystalMethodDefinition): String? {
        val classDef = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
        if (classDef != null) return classDef.name
        val moduleDef = PsiTreeUtil.getParentOfType(method, CrystalModuleDefinition::class.java)
        if (moduleDef != null) return moduleDef.name
        val structDef = PsiTreeUtil.getParentOfType(method, CrystalStructDefinition::class.java)
        if (structDef != null) return structDef.name
        val enumDef = PsiTreeUtil.getParentOfType(method, CrystalEnumDefinition::class.java)
        if (enumDef != null) return enumDef.name
        return null
    }

    /**
     * Formats the parameter list of a method as a string like "(a, b, c)".
     */
    fun getParameterSignature(method: CrystalMethodDefinition): String {
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

    /**
     * Returns the return type annotation of a method, or null.
     */
    fun getReturnType(method: CrystalMethodDefinition): String? = method.typeReference?.text

    /**
     * Checks whether a method is a class method (def self.xxx).
     */
    fun isStaticMethod(method: CrystalMethodDefinition): Boolean = method.node.findChildByType(CrystalTypes.SELF) != null
}
