package io.github.unurgunite.crystal.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalBareCommandExpression
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.stubs.CrystalMacroIndex
import io.github.unurgunite.crystal.type.CrystalCallArguments
import io.github.unurgunite.crystal.type.CrystalSearchScopes

/**
 * A DSL setter has two declarations with the same name: a macro that takes the
 * value (`macro queue_name(name)`) and a zero-param getter it generates
 * (`def self.queue_name : String`). The getter wins name resolution, so the
 * setter call reads as a 0-arg method with one excess argument.
 *
 * Any same-named macro makes the arity unknowable, so the check bows out.
 * File-level to keep the inspection class under detekt's TooManyFunctions
 * budget.
 */
internal object CrystalMacroSetters {
    /** True when [methodName] is declared as a macro somewhere in [project]. */
    fun hasMacro(
        methodName: String,
        project: Project,
    ): Boolean =
        StubIndex
            .getElements(
                CrystalMacroIndex.KEY,
                methodName,
                project,
                CrystalSearchScopes.project(project),
                CrystalMacroDefinition::class.java,
            ).isNotEmpty()

    /**
     * True when [call] is a bare DSL entry inside the block of a macro-driven
     * configuration call (`properties do description "x" end`).
     *
     * The macro expands each entry into a method that shares nothing with the
     * source text, so the entry name resolves only by accident -- `line_number 1`
     * inside such a block matched `XML::Node#line_number : Int64?` from the
     * indexed stdlib and reported a bogus excess argument. An entry whose own
     * name has no macro is unknowable this way, so arity checking skips it.
     */
    fun isMacroBlockEntry(call: PsiElement): Boolean {
        // The entry sits in statement/expression wrappers inside the block, so
        // walk past them rather than assuming a direct parent.
        val block = PsiTreeUtil.getParentOfType(call, CrystalBlock::class.java) ?: return false
        val owner = macroCallOwning(block) ?: return false
        val ownerName = CrystalCallArguments.extractMethodName(owner) ?: return false
        return hasMacro(ownerName, call.project)
    }

    /** The call whose block this is: `properties do ... end` owns the block. */
    private fun macroCallOwning(block: CrystalBlock): PsiElement? =
        block.parent as? CrystalBareCommandExpression ?: block.parent as? CrystalMethodCallExpression
}
