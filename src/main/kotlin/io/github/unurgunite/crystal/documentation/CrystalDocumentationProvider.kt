package io.github.unurgunite.crystal.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import io.github.unurgunite.crystal.documentation.CrystalDocHtml.escapeHtml
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.stubs.CrystalClassIndex

/**
 * Provides Quick Documentation (Ctrl+Q / F1) and hover documentation for Crystal elements.
 * Shows the element signature (syntax-highlighted) and doc comments rendered as Markdown/HTML.
 *
 * Documentation links: type names, class names, and superclass names inside the rendered
 * documentation popup are hyperlinked via `psi_element://class:<name>` URLs. Clicking them
 * resolves via [CrystalClassIndex] and replaces the popup content with the target element's
 * documentation (handled by [getDocumentationElementForLink]).
 *
 * Target resolution lives in [CrystalDocTargetResolve], signature rendering in
 * [CrystalDocSignatures], doc comments in [CrystalDocComments], HTML in [CrystalDocHtml].
 */
class CrystalDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(
        element: PsiElement?,
        originalElement: PsiElement?,
    ): String? {
        val target = CrystalDocTargetResolve.resolveTarget(element) ?: return null

        // Variable hover: show inferred type
        if (CrystalDocTargetResolve.isVariableIdentifier(target)) {
            return buildVariableDocumentation(target)
        }

        return buildDocumentation(target)
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        if (contextElement == null) return null
        // 1. Unwrap argument wrappers to find the actual expression inside
        val unwrapped = CrystalDocTargetResolve.unwrapArgument(contextElement)
        return CrystalDocTargetResolve.resolveViaReference(unwrapped)
            // 3. Fallback for DOT-call identifiers (no PsiReference) via the GotoDeclarationHandler
            ?: CrystalDocTargetResolve.resolveViaDotCall(unwrapped, targetOffset, editor)
            // 4. Definition/parameter walk-up: hovering over the definition name itself
            ?: CrystalDocTargetResolve.walkUpFromSelf(unwrapped)
            // 5. Variable identifier: hovering over a variable name (not a definition)
            ?: unwrapped.takeIf { CrystalDocTargetResolve.isVariableIdentifier(it) }
            // 6. Last resort: direct StubIndex lookup for classes/methods
            ?: CrystalDocTargetResolve.lookupByName(unwrapped)
    }

    override fun getDocumentationElementForLink(
        psiManager: PsiManager,
        link: String,
        originalElement: PsiElement?,
    ): PsiElement? {
        if (!link.startsWith("class:")) return null
        val className = link.removePrefix("class:")
        val project = originalElement?.project ?: return null
        val elements =
            StubIndex.getElements(
                CrystalClassIndex.KEY,
                className,
                project,
                GlobalSearchScope.allScope(project),
                CrystalNamedElement::class.java,
            )
        return elements.firstOrNull()
    }

    private fun buildDocumentation(target: PsiElement): String {
        val sb = StringBuilder()
        sb.append("<div class='definition'><pre>")
        sb.append(CrystalDocSignatures.renderSignature(target))
        sb.append("</pre></div>")

        // Auto-generated doc for untyped parameters
        if (target is CrystalParameter && target.typeReference == null) {
            sb.append("<div class='content'>")
            sb.append("<p>The type of this parameter is not specified and will be determined at runtime.</p>")
            sb.append("</div>")
        } else {
            val docComment = CrystalDocComments.collectDocComment(target)
            if (docComment != null) {
                sb.append("<div class='content'>")
                sb.append(CrystalDocComments.renderMarkdown(docComment, target))
                sb.append("</div>")
            }
        }

        return sb.toString()
    }

    private fun buildVariableDocumentation(target: PsiElement): String {
        val name = CrystalDocVariables.variableNameOf(target) ?: return ""

        val sb = StringBuilder()
        sb.append("<div class='definition'><pre>")
        sb.append(CrystalDocVariables.buildVariableSignatureHtml(target, name, target.project))
        sb.append("</pre></div>")

        return sb.toString()
    }
}
