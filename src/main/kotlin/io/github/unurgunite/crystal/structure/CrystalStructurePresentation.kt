package io.github.unurgunite.crystal.structure

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalAliasDefinition
import io.github.unurgunite.crystal.psi.CrystalAnnotationDefinition
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalConstantAssignment
import io.github.unurgunite.crystal.psi.CrystalEnumConstant
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalLibDefinition
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import javax.swing.Icon

/**
 * Presentation (name, icon, location) for structure-view elements.
 * Split out of `CrystalStructureViewElement` (which exceeded the function budget).
 */
internal object CrystalStructurePresentation {
    // Fallback preview length for element kinds without a dedicated presentation.
    private const val PREVIEW_MAX_LENGTH = 30

    fun getElementInfo(element: PsiElement): Triple<String, Icon?, String?> =
        typeInfo(element)
            ?: memberInfo(element)
            ?: Triple(element.text.take(PREVIEW_MAX_LENGTH), null, null)

    /** Class-like definitions: class, module, struct, enum, lib, annotation. */
    private fun typeInfo(element: PsiElement): Triple<String, Icon?, String?>? =
        when (element) {
            is CrystalClassDefinition -> {
                info(element.name, AllIcons.Nodes.Class, "class")
            }

            is CrystalModuleDefinition -> {
                info(element.name, AllIcons.Nodes.Module, "module")
            }

            is CrystalStructDefinition -> {
                info(element.name, AllIcons.Nodes.Record, "struct")
            }

            is CrystalEnumDefinition -> {
                info(element.name, AllIcons.Nodes.Enum, "enum")
            }

            is CrystalLibDefinition -> {
                libInfo(element)
            }

            is CrystalAnnotationDefinition -> {
                info(
                    element.node.findChildByType(CrystalTypes.CONSTANT)?.text,
                    AllIcons.Nodes.Annotationtype,
                    "annotation",
                )
            }

            else -> {
                null
            }
        }

    /** Member definitions: methods, macros, aliases, constants, enum constants. */
    private fun memberInfo(element: PsiElement): Triple<String, Icon?, String?>? =
        when (element) {
            is CrystalMethodDefinition -> {
                methodInfo(element)
            }

            is CrystalMacroDefinition -> {
                macroInfo(element)
            }

            is CrystalAliasDefinition -> {
                info(
                    element.node.findChildByType(CrystalTypes.CONSTANT)?.text,
                    AllIcons.Nodes.Type,
                    "alias",
                )
            }

            is CrystalConstantAssignment -> {
                info(element.firstChild?.text, AllIcons.Nodes.Constant, "constant")
            }

            is CrystalEnumConstant -> {
                Triple(
                    element.firstChild?.text ?: "<anonymous>",
                    AllIcons.Nodes.Constant,
                    null,
                )
            }

            else -> {
                null
            }
        }

    private fun libInfo(element: CrystalLibDefinition): Triple<String, Icon?, String?> =
        Triple(
            element.text
                .substringAfter("lib")
                .trim()
                .substringBefore("\n")
                .trim(),
            AllIcons.Nodes.PpLib,
            "lib",
        )

    private fun methodInfo(element: CrystalMethodDefinition): Triple<String, Icon?, String?> {
        val name = element.name ?: "<anonymous>"
        val params = element.parameterList?.text ?: ""
        val returnType = element.typeReference?.text?.let { " : $it" } ?: ""
        return Triple("$name($params)$returnType", AllIcons.Nodes.Method, "def")
    }

    private fun macroInfo(element: CrystalMacroDefinition): Triple<String, Icon?, String?> {
        val name = element.name ?: "<anonymous>"
        val params = element.parameterList?.text ?: ""
        return Triple("$name($params)", AllIcons.Nodes.Template, "macro")
    }

    /** Named presentation, defaulting a missing name to `<anonymous>`. */
    private fun info(
        name: String?,
        icon: Icon?,
        location: String?,
    ): Triple<String, Icon?, String?> = Triple(name ?: "<anonymous>", icon, location)
}
