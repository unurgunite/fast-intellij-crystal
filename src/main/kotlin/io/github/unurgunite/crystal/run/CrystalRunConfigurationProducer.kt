package io.github.unurgunite.crystal.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalStringExpression
import io.github.unurgunite.crystal.psi.CrystalTypes
import java.io.File

class CrystalRunConfigurationProducer : LazyRunConfigurationProducer<CrystalRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        val type = CrystalRunConfigurationType()
        return CrystalSpecFactory(type)
    }

    override fun isConfigurationFromContext(
        configuration: CrystalRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val element = context.psiLocation ?: return false

        // Directory context
        if (element is PsiDirectory) {
            return matchesDirectoryConfig(configuration, element)
        }

        return matchesFileConfig(configuration, context)
    }

    /** File context: same `.cr` path, plus spec-line match for spec runs. */
    private fun matchesFileConfig(
        configuration: CrystalRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (file.extension != "cr" || configuration.filePath != file.path) return false
        // For spec files, also check line match
        if (configuration.command != CrystalCommand.SPEC) return true
        return configuration.specLine == findSpecLine(context)
    }

    private fun matchesDirectoryConfig(
        configuration: CrystalRunConfiguration,
        element: PsiDirectory,
    ): Boolean {
        val dir = element.virtualFile
        return configuration.filePath == dir.path && configuration.command == CrystalCommand.SPEC
    }

    override fun setupConfigurationFromContext(
        configuration: CrystalRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val element = context.psiLocation ?: return false

        // Directory context — run all specs in directory
        if (element is PsiDirectory) {
            val dir = element.virtualFile
            configuration.filePath = dir.path
            configuration.workingDirectory = context.project.basePath ?: ""
            configuration.command = CrystalCommand.SPEC
            configuration.specLine = 0
            configuration.name = "spec: ${dir.name}"
            return true
        }

        // File context
        val file = context.location?.virtualFile ?: return false
        if (file.extension != "cr") return false

        configuration.filePath = file.path
        configuration.workingDirectory = context.project.basePath ?: ""

        // Detect if it's a spec file
        if (file.path.contains("/spec/") || file.name.endsWith("_spec.cr")) {
            configuration.command = CrystalCommand.SPEC
            val specLine = findSpecLine(context)
            configuration.specLine = specLine

            if (specLine > 0) {
                val testName = findSpecName(context) ?: "line $specLine"
                configuration.name = "spec: $testName"
            } else {
                configuration.name = "spec: ${file.name}"
            }
        } else {
            configuration.command = CrystalCommand.RUN
            configuration.name = "run: ${file.name}"
        }

        return true
    }

    /**
     * Find the line number of the enclosing `it` or `describe` block at the cursor position.
     * Returns 0 if not inside a spec block (runs all specs in file).
     */
    private fun findSpecLine(context: ConfigurationContext): Int {
        val psiElement = context.psiLocation ?: return 0
        val file = context.location?.virtualFile ?: return 0
        if (!file.name.endsWith("_spec.cr")) return 0

        // Walk up to find enclosing `it` or `describe` call
        var element: PsiElement? = psiElement
        while (element != null) {
            val firstChild = element.firstChild
            if (firstChild != null && firstChild.node?.elementType == CrystalTypes.IDENTIFIER) {
                val name = firstChild.text
                if (name == "it" || name == "describe" || name == "context") {
                    // Return the line number (1-based)
                    val document =
                        com.intellij.psi.PsiDocumentManager
                            .getInstance(context.project)
                            .getDocument(psiElement.containingFile)
                    if (document != null) {
                        return document.getLineNumber(element.textOffset) + 1
                    }
                }
            }
            element = element.parent
        }
        return 0
    }

    /**
     * Find the name of the spec (the string argument to `it` or `describe`).
     */
    private fun findSpecName(context: ConfigurationContext): String? {
        var element: PsiElement? = context.psiLocation ?: return null
        while (element != null) {
            findSpecNameIn(element)?.let { return it }
            element = element.parent
        }
        return null
    }

    /**
     * String argument of an `it`/`describe`/`context` call rooted at [element], if any.
     * Strings parse as a STRING_EXPRESSION composite (quote + content + quote leaves),
     * never as a bare STRING_LITERAL sibling.
     */
    private fun findSpecNameIn(element: PsiElement): String? {
        val firstChild = element.firstChild ?: return null
        if (firstChild.node?.elementType != CrystalTypes.IDENTIFIER) return null
        val name = firstChild.text
        if (name != "it" && name != "describe" && name != "context") return null
        var sibling = firstChild.nextSibling
        while (sibling != null) {
            extractStringArgument(sibling)?.let { return it }
            sibling = sibling.nextSibling
        }
        return null
    }

    private fun extractStringArgument(sibling: PsiElement): String? {
        val stringExpr =
            PsiTreeUtil.findChildOfType(
                sibling,
                CrystalStringExpression::class.java,
                false,
            ) ?: (sibling as? CrystalStringExpression)
        if (stringExpr != null) {
            return stringExpr.text.removeSurrounding("\"")
        }
        val directString = PsiTreeUtil.findChildOfType(sibling, PsiElement::class.java)
        if (directString?.node?.elementType == CrystalTypes.STRING_LITERAL) {
            return directString.text.removeSurrounding("\"")
        }
        return null
    }
}
