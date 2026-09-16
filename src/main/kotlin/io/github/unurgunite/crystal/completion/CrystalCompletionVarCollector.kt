package io.github.unurgunite.crystal.completion

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils

/**
 * File-level `@instance` / `@@class` variable collection for completion.
 *
 * Walks file-level PSI children and recurses into everything, but stops at
 * nested class/module/struct/enum boundaries to prevent variable leakage.
 *
 * Intentionally file-level, not class-level — the bare `@` parse error can cause
 * the class PSI node to be truncated, leaving methods defined after the caret as
 * loose file-level tokens. A class-scoped search would miss their variables.
 */
internal object CrystalCompletionVarCollector {
    fun collectClassVariables(
        file: PsiElement,
        add: (name: String, typeText: String) -> Unit,
    ) {
        val state = VisitState()
        for (child in file.children) state.visit(child, add)
    }

    private class VisitState(
        var enteredEnclosing: Boolean = false,
    ) {
        fun visit(
            element: PsiElement,
            add: (name: String, typeText: String) -> Unit,
        ) {
            if (enteredEnclosing && CrystalPsiUtils.isTypeDefinition(element)) {
                return
            }
            when (element) {
                is CrystalInstanceVarAccess -> {
                    val name = element.name ?: return
                    add(name, "instance variable")
                }

                is CrystalClassVarAccess -> {
                    val name = element.name ?: return
                    add(name, "class variable")
                }
            }
            // Handle raw INSTANCE_VAR/CLASS_VAR tokens (e.g. after a parse error
            // where the parser didn't wrap them in CrystalInstanceVarAccess composites)
            addRawVarToken(element, add)
            if (!enteredEnclosing && CrystalPsiUtils.isTypeDefinition(element)) {
                enteredEnclosing = true
            }
            for (child in element.children) visit(child, add)
        }

        private fun addRawVarToken(
            element: PsiElement,
            add: (name: String, typeText: String) -> Unit,
        ) {
            val tokenType = element.node?.elementType
            if (tokenType == CrystalTypes.INSTANCE_VAR) {
                add(element.text, "instance variable")
            } else if (tokenType == CrystalTypes.CLASS_VAR) {
                add(element.text, "class variable")
            }
        }
    }
}
