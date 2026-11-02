package io.github.unurgunite.crystal.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.stubs.CrystalMacroIndex

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
                GlobalSearchScope.allScope(project),
                CrystalMacroDefinition::class.java,
            ).isNotEmpty()
}
