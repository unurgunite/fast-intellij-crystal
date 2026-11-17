package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Heredoc validation, mirroring what the compiler enforces: every `<<-IDENT`
 * needs an end delimiter, and every content line must be indented at least as
 * deep as that delimiter (the delimiter's own indentation is what `<<-`
 * strips, so a deeper delimiter would swallow content).
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
        val delimiter = extractHeredocDelimiter(element.text) ?: return
        val file = element.containingFile ?: return

        if (!heredocIndexOf(file).hasEndAfter(element.textOffset, delimiter)) {
            holder
                .newAnnotation(HighlightSeverity.ERROR, "Missing heredoc end delimiter '$delimiter'")
                .range(element)
                .create()
        }
    }

    /**
     * Validate that no content line of the heredoc is indented less than its
     * end delimiter, naming the first offender — the same line the compiler
     * rejects. The error sits on the delimiter because the platform only allows
     * a range inside the element being annotated.
     */
    fun validateHeredocEnd(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val file = element.containingFile ?: return
        val document = file.viewProvider.document ?: return
        val body = heredocIndexOf(file).bodies[element.textOffset] ?: return
        val shallow = body.lines.indexOfFirst { !it.blank && it.indent < body.endIndent }
        if (shallow < 0) return
        // Body lines are stored relative to the delimiter, so the reported line is
        // counted back from the delimiter instead of tracking offsets per token.
        val line = document.getLineNumber(element.textOffset) + 1 - (body.lines.size - 1 - shallow)

        holder
            .newAnnotation(
                HighlightSeverity.ERROR,
                "Heredoc line $line is indented less than its end delimiter '${body.delimiter}' " +
                    "(${body.endIndent} spaces expected)",
            ).range(element)
            .create()
    }

    /**
     * Heredoc facts of one file version: ends for the missing-end check, bodies
     * for the indent check.
     *
     * Built once per file version (dropped on any PSI change): the old code
     * walked the whole file on EVERY heredoc start the annotator visited,
     * freezing the IDE on files with many heredocs. Only offsets, delimiters and
     * line indents are cached (no PSI), so the cache can never go stale.
     */
    private fun heredocIndexOf(file: PsiFile): HeredocIndex =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result(buildHeredocIndex(file), PsiModificationTracker.MODIFICATION_COUNT)
        }

    /**
     * One pass over [file] pairing openers with ends in document order, the way
     * the lexer does: with `{<<-ONE, <<-TWO` both bodies follow the openers, so
     * the first end belongs to the first opener regardless of where the literals
     * sit in the tree.
     */
    private fun buildHeredocIndex(file: PsiFile): HeredocIndex {
        val ends = ArrayList<HeredocEnd>()
        val bodies = HashMap<Int, HeredocBody>()
        val pending = ArrayDeque<String>()
        val content = StringBuilder()

        file.accept(
            object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    when (element.node?.elementType) {
                        CrystalTypes.HEREDOC_START, CrystalTypes.HEREDOC_START_STACKED -> {
                            extractHeredocDelimiter(element.text)?.let(pending::addLast)
                            content.setLength(0)
                        }

                        CrystalTypes.HEREDOC_CONTENT -> {
                            content.append(element.text)
                        }

                        CrystalTypes.HEREDOC_END -> {
                            val delimiter = element.text.trim()
                            ends.add(HeredocEnd(element.textOffset, delimiter))
                            // A mismatched opener means an end delimiter is missing;
                            // that error already covers the pair, so skip the indent check.
                            if (pending.firstOrNull() == delimiter) {
                                pending.removeFirst()
                                bodies[element.textOffset] =
                                    HeredocBody(delimiter, indentOf(element.text), contentLines(content.toString()))
                            }
                            content.setLength(0)
                        }
                    }
                    super.visitElement(element)
                }
            },
        )
        return HeredocIndex(ends, bodies)
    }

    /** Indent of every content line; the last one is the empty tail after the final newline. */
    private fun contentLines(text: String): List<ContentLine> = text.split('\n').map { ContentLine(indentOf(it), it.isBlank()) }

    private fun indentOf(line: String): Int = line.takeWhile { it == ' ' || it == '\t' }.length

    /** Cached heredoc facts; ends are in document order. */
    private class HeredocIndex(
        private val ends: List<HeredocEnd>,
        val bodies: Map<Int, HeredocBody>,
    ) {
        /**
         * True when a HEREDOC_END with [delimiter] sits after [startOffset].
         * Ends before the start do not count — an end delimiter closes the
         * nearest preceding unmatched start.
         */
        fun hasEndAfter(
            startOffset: Int,
            delimiter: String,
        ): Boolean = ends.any { it.offset > startOffset && it.delimiter == delimiter }
    }

    /** One heredoc end delimiter: document offset plus delimiter text. */
    private data class HeredocEnd(
        val offset: Int,
        val delimiter: String,
    )

    /** One heredoc body: its delimiter, the delimiter's indent and the content lines. */
    private data class HeredocBody(
        val delimiter: String,
        val endIndent: Int,
        val lines: List<ContentLine>,
    )

    private data class ContentLine(
        val indent: Int,
        val blank: Boolean,
    )

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
