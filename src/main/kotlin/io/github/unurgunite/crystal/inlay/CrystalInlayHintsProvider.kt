package io.github.unurgunite.crystal.inlay

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayOptionInfo
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.type.CrystalAssignmentTarget
import io.github.unurgunite.crystal.type.CrystalTypeInference

/**
 * Inlay hints provider for Crystal: shows inferred variable types as
 * `: Type` annotations after local variable assignments without an
 * explicit type declaration (`x = 1` → `: Int32`).
 *
 * Uses the declarative (fast, stateless) API via [SharedBypassCollector]:
 * one [CrystalTypeHintsCollector] pass per element, no settings UI —
 * a single on/off option backed by [CrystalTypeHintsCollector] logic.
 * Enabled by default; toggle in Settings → Editor → Inlay Hints → Crystal.
 */
class CrystalInlayHintsProvider :
    InlayHintsProvider,
    com.intellij.codeInsight.hints.declarative.SharedBypassCollector {
    companion object {
        const val PROVIDER_ID = "crystal.type.hints"
        const val PROVIDER_NAME = "Type hints"
        const val SHOW_LOCALS_OPTION = "crystal.type.hints.locals"
    }

    override fun createCollector(
        file: PsiFile,
        editor: Editor,
    ): InlayHintsCollector = this

    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink,
    ) {
        CrystalTypeHint.compute(element)?.let { hint ->
            sink.addPresentation(
                InlineInlayPosition(hint.offset, relatedToPrevious = false),
                payloads = null,
                tooltip = hint.typeText,
                hintFormat = HintFormat.default,
            ) {
                text(": ${hint.typeText}")
            }
        }
    }
}

/** One computed hint: text offset (end of the name token) plus display text. */
internal data class CrystalTypeHint(
    val offset: Int,
    val typeText: String,
) {
    companion object {
        /**
         * Computes the hint for a PSI [element], or null when no hint applies.
         * Applies to local-variable assignments (`x = expr`) without an
         * explicit type annotation and with a non-trivial inferred type
         * (`Nil` is skipped).
         */
        fun compute(element: PsiElement): CrystalTypeHint? {
            val assignment = element as? CrystalAssignment ?: return null
            return hintForAssignment(assignment)
        }

        /** Null unless the assignment deserves a hint; pure shape checks first. */
        private fun hintForAssignment(assignment: CrystalAssignment): CrystalTypeHint? {
            val name = CrystalAssignmentTarget.targetText(assignment) ?: return null
            if (name.startsWith("@") || hasExplicitTypeAnnotation(assignment)) return null
            val typeText = inferLocalType(assignment, name)?.takeUnless { it == "Nil" } ?: return null
            val offset = nameEndOffset(assignment, name) ?: return null
            return CrystalTypeHint(offset, typeText)
        }

        /** True when the LHS already declares a type (`x : Int32 = ...`). */
        private fun hasExplicitTypeAnnotation(assignment: CrystalAssignment): Boolean {
            var node = assignment.node.firstChildNode
            while (node != null) {
                val type = node.elementType
                if (type == CrystalTypes.COLON) return true
                if (type == CrystalTypes.ASSIGN) return false
                node = node.treeNext
            }
            return false
        }

        /** Inferred type of the assigned variable, resolved at the assignment site. */
        private fun inferLocalType(
            assignment: CrystalAssignment,
            name: String,
        ): String? {
            val project = assignment.project
            val rhs = CrystalAssignmentTarget.rhs(assignment)
            // Resolve from the RHS context so chained assignments and locals
            // defined earlier in the same scope are visible to inference.
            val context: PsiElement = rhs ?: assignment
            return CrystalTypeInference.inferType(name, context, project)
        }

        /** End offset of the LHS name token (hint renders right after it). */
        private fun nameEndOffset(
            assignment: CrystalAssignment,
            name: String,
        ): Int? {
            val base = assignment.textRange.startOffset
            var node = assignment.node.firstChildNode
            while (node != null) {
                val type = node.elementType
                if (type == CrystalTypes.ASSIGN) return null
                if (type == CrystalTypes.IDENTIFIER && node.text == name) {
                    return base + node.textRange.endOffset - assignment.node.textRange.startOffset
                }
                node = node.treeNext
            }
            return null
        }
    }
}

/** Factory wiring the provider into `com.intellij.codeInsight.declarativeInlayProviderFactory`. */
class CrystalInlayHintsProviderFactory : com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory {
    private val provider = CrystalInlayHintsProvider()

    override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> {
        if (language != CrystalLanguage) return emptyList()
        return listOf(
            InlayProviderInfo(
                provider,
                CrystalInlayHintsProvider.PROVIDER_ID,
                setOf(
                    InlayOptionInfo(
                        CrystalInlayHintsProvider.SHOW_LOCALS_OPTION,
                        true,
                        "Show inferred types of local variables",
                    ),
                ),
                true,
                CrystalInlayHintsProvider.PROVIDER_NAME,
            ),
        )
    }

    override fun getSupportedLanguages(): Set<Language> = setOf(CrystalLanguage)

    override fun getProviderInfo(
        language: Language,
        providerId: String,
    ): InlayProviderInfo? = getProvidersForLanguage(language).firstOrNull { it.providerId == providerId }
}
