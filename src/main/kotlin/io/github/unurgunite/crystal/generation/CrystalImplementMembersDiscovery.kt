package io.github.unurgunite.crystal.generation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAbstractMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.type.CrystalTypeHierarchy

/**
 * Finds `abstract def` methods a type must implement: walks the ancestor
 * chain nearest-first (superclass, then includes) and reports abstracts not
 * shadowed by a nearer concrete definition. Signature identity is name +
 * arity (Crystal overloads by arity).
 */
internal object CrystalImplementMembersDiscovery {
    fun collectUnimplemented(
        target: PsiElement,
        project: Project,
    ): List<CrystalAbstractMethodInfo> {
        val body = classBodyOf(target) ?: return emptyList()
        val seen = concreteSignatures(body, target).toMutableSet()
        val result = ArrayList<CrystalAbstractMethodInfo>()
        for (ancestor in ancestorElements(target, project)) {
            val ancestorBody = classBodyOf(ancestor) ?: continue
            collectAbstracts(ancestorBody, ancestor, seen, result)
            seen.addAll(concreteSignatures(ancestorBody, ancestor))
        }
        return result
    }

    /** Ancestor type elements nearest-first (superclass, then includes, transitively). */
    private fun ancestorElements(
        target: PsiElement,
        project: Project,
    ): List<PsiElement> {
        val simpleName = (target as? CrystalNamedElement)?.name?.substringAfterLast("::") ?: return emptyList()
        val lookup =
            CrystalTypeHierarchy.findTypeByName(simpleName, project, target.containingFile)
                ?: return emptyList()
        val targetQualified = CrystalPsiUtils.buildQualifiedName(target)
        return CrystalTypeHierarchy
            .collectFullHierarchy(lookup)
            .mapNotNull { ancestorName ->
                CrystalTypeHierarchy.findTypeByName(stripGenerics(ancestorName), project, target.containingFile)
            }.map { it.element }
            .filterNot { isSameType(it, target, targetQualified) }
    }

    /** `Array(T)` → `Array` (the index is keyed by the bare type name). */
    private fun stripGenerics(name: String): String = name.substringBefore("(").trim()

    /** Same type by qualified name in the same file (index may return fresh instances). */
    private fun isSameType(
        candidate: PsiElement,
        target: PsiElement,
        targetQualified: String?,
    ): Boolean =
        candidate.containingFile == target.containingFile &&
            CrystalPsiUtils.buildQualifiedName(candidate) == targetQualified

    /** Abstracts owned directly by [owner]'s body, skipping already-seen signatures. */
    private fun collectAbstracts(
        body: PsiElement,
        owner: PsiElement,
        seen: MutableSet<String>,
        result: MutableList<CrystalAbstractMethodInfo>,
    ) {
        PsiTreeUtil
            .collectElementsOfType(body, CrystalAbstractMethodDefinition::class.java)
            .filter { CrystalPsiUtils.getEnclosingType(it) == owner }
            .mapNotNull { CrystalAbstractMethodInfo.from(it) }
            .filter { seen.add(it.key) }
            .forEach { result.add(it) }
    }
}
