package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter

/**
 * Lookup-element builders for completion: methods, classes, `new` constructors.
 * Split out of `CrystalCompletionHelper` (which exceeded the function budget).
 */
object CrystalLookupBuilders {
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
     * Builds a LookupElement for a method.
     */
    fun buildMethodLookup(
        method: CrystalMethodDefinition,
        priority: Double = 0.0,
    ): LookupElement {
        method.name ?: return LookupElementBuilder.create("")
        val signature = getParameterSignature(method)
        val className = CrystalCompletionHelper.getEnclosingClassName(method)
        val returnType = getReturnType(method)

        var builder =
            LookupElementBuilder
                .create(method)
                .withIcon(AllIcons.Nodes.Method)
                .withTailText(signature, true)

        builder = builderWithTypeText(builder, className, returnType)

        return if (priority != 0.0) {
            com.intellij.codeInsight.completion.PrioritizedLookupElement
                .withPriority(builder, priority)
        } else {
            builder
        }
    }

    /** Method lookup type text: enclosing class, return type, or both. */
    private fun builderWithTypeText(
        builder: LookupElementBuilder,
        className: String?,
        returnType: String?,
    ): LookupElementBuilder {
        if (className != null) {
            val typeText = if (returnType != null) "$className → $returnType" else className
            return builder.withTypeText(typeText, true)
        }
        if (returnType != null) {
            return builder.withTypeText(returnType, true)
        }
        return builder
    }

    /**
     * Builds a LookupElement for a class/module/struct/enum name.
     */
    fun buildClassLookup(name: String): LookupElementBuilder =
        LookupElementBuilder
            .create(name)
            .withIcon(AllIcons.Nodes.Class)

    /**
     * Builds a LookupElement for `new` with initialize parameters.
     */
    fun buildNewLookup(
        className: String,
        project: Project,
        currentFile: PsiFile? = null,
    ): LookupElementBuilder {
        val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, currentFile)
        val signature = if (initMethod != null) getParameterSignature(initMethod) else "()"
        val tailText = if (signature == "()") "" else signature

        return LookupElementBuilder
            .create("new")
            .withIcon(AllIcons.Nodes.Method)
            .withTailText(tailText, true)
            .withTypeText(className, true)
    }

    /**
     * Extracts the parameter name from a [CrystalParameter] node.
     * Handles both normal parameters (`radius`) and shorthand instance
     * variable assignment (`@radius`) — the `@` prefix is stripped.
     *
     * @return the parameter name, or `null` if the parameter is a splat/block prefix
     */
    fun extractParameterName(param: CrystalParameter): String? {
        // Normal case: IDENTIFIER child (e.g. `radius : Float64`)
        val identNode = param.node.findChildByType(io.github.unurgunite.crystal.psi.CrystalTypes.IDENTIFIER)
        if (identNode != null) return identNode.text

        // Shorthand: INSTANCE_VAR_ACCESS child (e.g. `@radius : Float64`)
        // Strip the `@` prefix so it's treated like a normal parameter name
        val instanceVarNode = param.node.findChildByType(io.github.unurgunite.crystal.psi.CrystalTypes.INSTANCE_VAR_ACCESS)
        if (instanceVarNode != null) {
            return stripAtPrefix(instanceVarNode.text)
        }

        return null
    }

    /** Shorthand `@name` parameter → `name`. */
    private fun stripAtPrefix(varText: String): String = if (varText.startsWith("@")) varText.substring(1) else varText
}
