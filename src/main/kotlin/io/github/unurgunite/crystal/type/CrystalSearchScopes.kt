package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectAndLibrariesScope

/**
 * Scope for Crystal stub-index lookups.
 *
 * `allScope` includes every content root of the project. Installed shards used
 * to live there and are library roots now, so `ProjectAndLibrariesScope` finds
 * the same definitions while leaving out anything a user excluded from the
 * project — excluded directories are not part of the search set.
 */
object CrystalSearchScopes {
    fun project(project: Project): GlobalSearchScope = ProjectAndLibrariesScope(project)
}
