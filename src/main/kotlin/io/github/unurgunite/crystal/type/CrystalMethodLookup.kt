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
     * Finds the `initialize` method of a class/struct (the Crystal constructor).
     */
    fun getInitializeMethod(
        typeName: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): CrystalMethodDefinition? {
        // Fast path: direct lookup by class name — avoids full hierarchy traversal
        directInitialize(typeName, project)?.let { return it }

        // Slow path: resolve type hierarchy (for inherited initialize from parent classes)
        val result = CrystalTypeHierarchy.findTypeByName(typeName, project, currentFile) ?: return null
        return CrystalTypeHierarchy.getMethodsFromType(result).firstOrNull { it.name == "initialize" }
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
     */
    fun getEnclosingClassName(method: CrystalMethodDefinition): String? {
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
