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
        // Resolved chain first (namespace-aware): short-name index hits mix
        // same-named classes (`Job` = Mosquito::Job + Benchmark::IPS::Job),
        // so candidates are filtered by qualified enclosing per chain link.
        val chain = CrystalTypeHierarchy.resolveChain(typeName, project, currentFile)
        if (chain.isNotEmpty()) {
            val scoped = scopedInitializers(chain, project)
            if (scoped.isNotEmpty()) return scoped
        }
        // Unresolved type: legacy direct lookup (may mix same-named classes,
        // but guessing beats silence when the type itself is unknown).
        val scope = GlobalSearchScope.allScope(project)
        return StubIndex
            .getElements(
                CrystalMethodByClassIndex.KEY,
                typeName,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            ).filter { it.name == "initialize" }
    }

    /**
     * `initialize` overloads owned by the resolved chain links (qualified
     * enclosing match per link, top-level ownerless methods kept). File-level
     * shape keeps getInitializeMethods under complexity budget.
     */
    private fun scopedInitializers(
        chain: List<CrystalTypeHierarchy.TypeLookupResult>,
        project: Project,
    ): List<CrystalMethodDefinition> {
        val scope = GlobalSearchScope.allScope(project)
        val out = mutableListOf<CrystalMethodDefinition>()
        for (link in chain) {
            val short = link.element.name ?: continue
            val qualified = qualifiedNameOf(link.element)
            val hits =
                StubIndex
                    .getElements(
                        CrystalMethodByClassIndex.KEY,
                        short,
                        project,
                        scope,
                        CrystalMethodDefinition::class.java,
                    ).filter { it.name == "initialize" }
            for (method in hits) {
                // Ownerless (top-level) methods only match top-level links:
                // a top-level `Job#initialize` must not attach to the
                // `Mosquito::Job` link of another class's chain.
                val owner = methodOwnerQualified(method)
                if (owner == null && qualified != null && qualified.contains("::")) continue
                if (owner == null || owner == qualified) {
                    out.add(method)
                }
            }
        }
        return out.distinctBy { getParameterSignature(it) }
    }

    /**
     * Qualified name of a type element (`Mosquito::Job`), top-level short
     * name when unnested. File-level: used by the scoped lookup above.
     */
    private fun qualifiedNameOf(element: io.github.unurgunite.crystal.psi.CrystalNamedElement): String? =
        io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
            .buildQualifiedName(element)
            ?: element.name

    /**
     * Qualified enclosing type of a method from its stub tree (never touches
     * the AST — stub/AST mismatch safe). Null for top-level methods.
     * Walks from the stub's PARENT (the method stub itself is named and
     * would pollute the path with `initialize`). Stub-less elements
     * (in-memory test PSI) fall back to the PSI parent chain.
     */
    private fun methodOwnerQualified(method: CrystalMethodDefinition): String? {
        val stub = (method as? com.intellij.psi.StubBasedPsiElement<*>)?.stub
        if (stub != null) {
            val parent = stub.parentStub ?: return null
            return io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
                .buildQualifiedNameFromStub(parent)
        }
        var current: com.intellij.psi.PsiElement? = method.parent
        while (current != null) {
            val qualified =
                io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
                    .buildQualifiedName(current)
            if (qualified != null) return qualified
            current = current.parent
        }
        return null
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
