package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Heredoc validation: every `<<-IDENTIFIER` start needs a matching end
 * delimiter, and the end delimiter must not be indented deeper than the
 * least-indented content line.
 */
internal object CrystalHeredocValidation {
    /**
     * Validate that a heredoc start has a matching end delimiter.
     * If the end delimiter is missing, mark the start with a clear error.
     */
    fun validateHeredocStart(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val startText = element.text
        val delimiter = extractHeredocDelimiter(startText) ?: return

        val file = element.containingFile ?: return

        // Find the matching end delimiter
        if (!hasHeredocEndAfter(file, element.textOffset, delimiter)) {
            holder
                .newAnnotation(
                    com.intellij.lang.annotation.HighlightSeverity.ERROR,
                    "Missing heredoc end delimiter '$delimiter'",
                ).range(element)
                .create()
        }
    }

    /**
     * Validate that a heredoc end delimiter is not indented more than
     * the least-indented content line.
     */
    fun validateHeredocEnd(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val endText = element.text
        val delimiter = endText.trim()

        val parent = element.parent
        val minContentIndent = findMinContentIndent(parent, delimiter)

        if (minContentIndent != null) {
            val endIndent = endText.takeWhile { it == ' ' || it == '\t' }.length
            if (endIndent > minContentIndent) {
                holder
                    .newAnnotation(
                        com.intellij.lang.annotation.HighlightSeverity.ERROR,
                        "Heredoc end delimiter '$delimiter' is indented too deeply ($endIndent spaces). " +
                            "It must not exceed the minimum content line indent ($minContentIndent spaces).",
                    ).range(element)
                    .create()
            }
        }
    }

    /**
     * True when a HEREDOC_END with [delimiter] sits after [startOffset].
     *
     * End offsets are collected once per file version (dropped on any PSI
     * change): the old code built the whole-file element list on EVERY
     * heredoc start the annotator visited, freezing the IDE on files with
     * many heredocs. Only offsets + delimiters are cached (no PSI), so the
     * cache can never go stale. Ends before the start do not count — an end
     * delimiter closes the nearest preceding unmatched start.
     */
    private fun hasHeredocEndAfter(
        file: PsiFile,
        startOffset: Int,
        delimiter: String,
    ): Boolean {
        val ends =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result(collectHeredocEnds(file), PsiModificationTracker.MODIFICATION_COUNT)
            }
        return ends.any { it.offset > startOffset && it.delimiter == delimiter }
    }

    /** All HEREDOC_END tokens of [file] in document order (offsets only, no PSI). */
    private fun collectHeredocEnds(file: PsiFile): List<HeredocEnd> {
        val ends = ArrayList<HeredocEnd>()
        file.accept(
            object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element.node?.elementType == CrystalTypes.HEREDOC_END) {
                        ends.add(HeredocEnd(element.textOffset, element.text.trim()))
                    }
                    super.visitElement(element)
                }
            },
        )
        return ends
    }

    /** One heredoc end delimiter: document offset plus delimiter text. */
    private data class HeredocEnd(
        val offset: Int,
        val delimiter: String,
    )

    /**
     * Find the minimum indentation among content lines in the heredoc literal.
     * Uses the parent heredoc AST node and scans its children for HEREDOC_CONTENT tokens.
     * Returns the number of leading spaces/tabs, or null if no content found.
     *
     * All HEREDOC_CONTENT tokens are concatenated first before splitting into lines,
     * because interpolation splits a single logical line across multiple tokens.
     */
    private fun findMinContentIndent(
        parent: PsiElement,
        delimiter: String,
    ): Int? {
        val contentBuilder = collectHeredocContent(parent, delimiter)

        if (contentBuilder.isEmpty()) return null

        var minIndent: Int? = null
        for (line in contentBuilder.toString().lines()) {
            if (line.isNotBlank()) {
                val indent = line.takeWhile { it == ' ' || it == '\t' }.length
                if (minIndent == null || indent < minIndent) {
                    minIndent = indent
                }
            }
        }
        return minIndent
    }

    /** Concatenated HEREDOC_CONTENT up to the matching end delimiter. */
    private fun collectHeredocContent(
        parent: PsiElement,
        delimiter: String,
    ): StringBuilder {
        val nodeChildren = parent.node.getChildren(null)
        val contentBuilder = StringBuilder()

        for (node in nodeChildren) {
            if (node.elementType == CrystalTypes.HEREDOC_CONTENT) {
                contentBuilder.append(node.text)
            } else if (node.elementType == CrystalTypes.HEREDOC_END && node.text.trim() == delimiter) {
                break
            }
        }
        return contentBuilder
    }

    /**
     * Extract the heredoc delimiter name from the start token text.
     * Supports <<-IDENTIFIER and <<-'IDENTIFIER'.
     */
    private fun extractHeredocDelimiter(text: String): String? {
        val quotedMatch = Regex("""<<-'([A-Za-z_][A-Za-z0-9_]*)'""").find(text)
        if (quotedMatch != null) return quotedMatch.groupValues[1]
        val unquotedMatch = Regex("""<<-([A-Za-z_][A-Za-z0-9_]*)""").find(text)
        if (unquotedMatch != null) return unquotedMatch.groupValues[1]
        return null
    }
}
