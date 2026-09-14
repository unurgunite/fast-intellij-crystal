package io.github.unurgunite.crystal.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiRecursiveElementWalkingVisitor

/**
 * Precise per-file stdlib resolution: parses ONLY the file that conventionally
 * defines a class (`ClassName` → `class_name.cr`) and finds the member inside it.
 * Returns stable [SymbolLoc] values, never a wrong-class match.
 *
 * Split out of `CrystalReference` (whose companion exceeded the function budget).
 */
internal object CrystalStdlibFileResolve {
    /** Resolve "Class" or "Class::member" via the class's home file, returning a stable [SymbolLoc]. */
    fun resolveByName(
        project: Project,
        root: VirtualFile,
        name: String,
    ): SymbolLoc? {
        if (name.contains("::")) {
            val idx = name.lastIndexOf("::")
            val className = name.substring(0, idx)
            val member = name.substring(idx + 2)
            return resolveMemberInFile(project, root, className, member)
        }
        return resolveMemberInFile(project, root, name, null)
    }

    /**
     * Parses ONLY the stdlib file that conventionally defines [className] and finds
     * [memberName] (a method or constant) inside it, returning a stable [SymbolLoc].
     * If [memberName] is null, finds the class/constant definition itself. Returns null
     * (never a wrong-class match) when the home file or the member is not found.
     */
    fun resolveMemberInFile(
        project: Project,
        root: VirtualFile,
        className: String,
        memberName: String?,
    ): SymbolLoc? {
        val relPath = className.replace("::", "/").lowercase() + ".cr"
        val file =
            VfsUtilCore
                .findRelativeFile(relPath, root) ?: return null
        @Suppress("DEPRECATION")
        return ReadAction.compute<SymbolLoc?, Throwable> {
            val psi =
                PsiManager
                    .getInstance(project)
                    .findFile(file) ?: return@compute null
            val off = findOffsetInPsi(psi, className, memberName) ?: return@compute null
            SymbolLoc(relPath, off)
        }
    }

    private fun findOffsetInPsi(
        psi: PsiElement,
        className: String,
        memberName: String?,
    ): Int? {
        var fallback: Int? = null
        psi.accept(
            object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (memberName == null) {
                        // Looking for the class/constant definition itself.
                        val named = element as? CrystalNamedElement
                        if (named != null && named !is CrystalConstantAssignment) {
                            if (CrystalPsiUtils.buildQualifiedName(named) == className) {
                                fallback = nameOffset(named)
                                stopWalking()
                                return
                            }
                        }
                        val const = element as? CrystalConstantAssignment
                        if (const != null && const.name == className) {
                            fallback = nameOffset(const)
                            stopWalking()
                            return
                        }
                    } else {
                        val enclosing =
                            CrystalPsiUtils
                                .getEnclosingType(element)
                                ?.let { CrystalPsiUtils.buildQualifiedName(it) }
                        val method = element as? CrystalMethodDefinition
                        if (method != null && method.name == memberName) {
                            if (enclosing == className) {
                                fallback = nameOffset(method)
                                stopWalking()
                                return
                            }
                            if (fallback == null) fallback = nameOffset(method)
                        }
                        val const = element as? CrystalConstantAssignment
                        if (const != null && const.name == memberName && enclosing == className) {
                            fallback = nameOffset(const)
                            stopWalking()
                            return
                        }
                    }
                    super.visitElement(element)
                }
            },
        )
        return fallback
    }

    private fun nameOffset(el: PsiElement): Int {
        val owner = el as? PsiNameIdentifierOwner
        val ident = owner?.nameIdentifier
        return ident?.textOffset ?: el.textOffset
    }

    /** Materialize a fresh PsiElement for [loc] from the current VFS. Always returns a live
     * element (never stale). Promotes a bare identifier leaf to its named owner so the
     * rename framework activates. */
    fun materialize(
        project: Project,
        root: VirtualFile,
        loc: SymbolLoc,
    ): PsiElement? {
        val file =
            VfsUtilCore
                .findRelativeFile(loc.relPath, root) ?: return null
        @Suppress("DEPRECATION")
        return ReadAction.compute<PsiElement?, Throwable> {
            val psi =
                PsiManager
                    .getInstance(project)
                    .findFile(file) ?: return@compute null
            val leaf = psi.findElementAt(loc.offset) ?: return@compute null
            if (leaf !is PsiNameIdentifierOwner) {
                val p = leaf.parent
                if (p is PsiNameIdentifierOwner) return@compute p
            }
            leaf
        }
    }
}
