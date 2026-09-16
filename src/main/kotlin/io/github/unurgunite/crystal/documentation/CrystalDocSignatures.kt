package io.github.unurgunite.crystal.documentation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.escapeHtml
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.highlightCrystalCode
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.linkToClass
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.wrapTypeLinks
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.CrystalVariableReference

/**
 * Signature rendering for documentation popups (syntax-highlighted HTML).
 * Split out of `CrystalDocumentationProvider` (which exceeded the function budget).
 */
internal object CrystalDocSignatures {
    fun renderSignature(target: PsiElement): String {
        val project = target.project
        return when (target) {
            is CrystalMethodDefinition -> {
                buildMethodSignatureHtml(target, project)
            }

            is CrystalClassDefinition -> {
                buildClassSignatureHtml(target, project)
            }

            is CrystalModuleDefinition -> {
                buildModuleSignatureHtml(target)
            }

            is CrystalStructDefinition -> {
                buildStructSignatureHtml(target, project)
            }

            is CrystalEnumDefinition -> {
                buildEnumSignatureHtml(target)
            }

            is CrystalParameter -> {
                buildParameterSignatureHtml(target, project)
            }

            else -> {
                highlightCrystalCode(target.text.lines().first(), target)
                    ?: escapeHtml(target.text.lines().first())
            }
        }
    }

    private fun buildMethodSignatureHtml(
        method: CrystalMethodDefinition,
        project: Project,
    ): String {
        val sb = StringBuilder()

        // Line 1: enclosing class name (linked) — top-level methods show "Object" (Crystal's universal base)
        val displayClassName = enclosingClassName(method) ?: "Object"
        sb.append(linkToClass(displayClassName, project) ?: escapeHtml(displayClassName))
        sb.append("\n")

        // Line 2: method signature (no "def " prefix)
        val methodLine = methodSignatureLine(method)
        val highlighted = highlightCrystalCode(methodLine, method) ?: escapeHtml(methodLine)

        // Wrap type names with links
        sb.append(wrapTypeLinks(highlighted, project))

        return sb.toString()
    }

    /** Enclosing class/module name of a method, if any. */
    private fun enclosingClassName(method: CrystalMethodDefinition): String? {
        val enclosingClass =
            PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
                ?: PsiTreeUtil.getParentOfType(method, CrystalModuleDefinition::class.java)
        return when (enclosingClass) {
            is CrystalClassDefinition -> enclosingClass.name
            is CrystalModuleDefinition -> enclosingClass.name
            else -> null
        }
    }

    /** `name(params) : ReturnType` line (without the `def` prefix). */
    private fun methodSignatureLine(method: CrystalMethodDefinition): String {
        val methodNameText = method.name ?: "unknown"
        val paramList = method.parameterList
        val paramsText =
            if (paramList != null) {
                paramList.parameterList.joinToString(", ") { it.text.trim() }
            } else {
                ""
            }
        val retTypeText = method.typeReference?.let { " : ${it.text}" } ?: ""
        return "$methodNameText($paramsText)$retTypeText"
    }

    private fun buildClassSignatureHtml(
        classDef: CrystalClassDefinition,
        project: Project,
    ): String =
        buildNamedTypeSignature(
            keyword = "class ",
            name = classDef.name,
            context = classDef,
            superclassText = classDef.superclassClause?.typeReference?.text,
            project = project,
        )

    /** ` < Super` suffix with a class link, when a superclass is present. */
    private fun appendSuperclass(
        sb: StringBuilder,
        superName: String?,
        project: Project,
    ) {
        if (superName == null) return
        val trimmed = superName.trim()
        sb.append(" < ")
        sb.append(linkToClass(trimmed, project) ?: escapeHtml(trimmed))
    }

    /**
     * `keyword Name < Super` signature. The type's own name is plain (no
     * self-link — the popup IS the type's documentation); the superclass links.
     */
    private fun buildNamedTypeSignature(
        keyword: String,
        name: String?,
        context: PsiElement,
        project: Project,
        superclassText: String? = null,
    ): String {
        val sb = StringBuilder()
        sb.append(highlightCrystalCode(keyword, context) ?: escapeHtml(keyword))
        sb.append(escapeHtml(name ?: "Unknown"))
        appendSuperclass(sb, superclassText, project)
        return sb.toString()
    }

    private fun buildModuleSignatureHtml(moduleDef: CrystalModuleDefinition): String =
        buildNamedTypeSignature(
            keyword = "module ",
            name = moduleDef.name,
            context = moduleDef,
            project = moduleDef.project,
        )

    private fun buildStructSignatureHtml(
        structDef: CrystalStructDefinition,
        project: Project,
    ): String =
        buildNamedTypeSignature(
            keyword = "struct ",
            name = structDef.name,
            context = structDef,
            superclassText = structDef.superclassClause?.typeReference?.text,
            project = project,
        )

    private fun buildEnumSignatureHtml(enumDef: CrystalEnumDefinition): String =
        buildNamedTypeSignature(
            keyword = "enum ",
            name = enumDef.name,
            context = enumDef,
            project = enumDef.project,
        )

    private fun buildParameterSignatureHtml(
        param: CrystalParameter,
        project: Project,
    ): String {
        val sb = StringBuilder()

        // Line 1: type name (linked if resolvable, "Any" if untyped) + muted "(Parameter)"
        val typeRef = param.typeReference
        if (typeRef != null) {
            // Use wrapTypeLinks to handle union types like "String | Int32"
            val typeText = typeRef.text.trim()
            val highlighted = highlightCrystalCode(typeText, param) ?: escapeHtml(typeText)
            sb.append(wrapTypeLinks(highlighted, project))
        } else {
            sb.append(escapeHtml("Any"))
        }
        sb.append(" <span style='color:gray'>(Parameter)</span>")
        sb.append("\n")

        // Line 2: parameter name
        val paramName = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: "unknown"
        sb.append(escapeHtml(paramName))

        return sb.toString()
    }
}
