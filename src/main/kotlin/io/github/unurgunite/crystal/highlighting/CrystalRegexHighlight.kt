package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Regex-literal highlighting and PCRE2 escape validation.
 * Uses IntelliJ's built-in RegExp colors so Crystal regexes match RubyMine/IDEA.
 */
internal object CrystalRegexHighlight {
    /**
     * Highlights regex sub-patterns inside a regex literal and marks invalid
     * PCRE2 escapes as errors.
     */
    fun annotateRegexLiteral(
        text: String,
        startOffset: Int,
        holder: AnnotationHolder,
    ) {
        // Validate invalid PCRE2 escapes before highlighting
        annotateInvalidRegexEscapes(text, startOffset, holder)

        for ((pattern, key) in REGEX_HIGHLIGHTS) {
            for (match in pattern.findAll(text)) {
                CrystalAnnotationEmit.applyRange(holder, startOffset, match.range, key)
            }
        }
    }

    /**
     * Validates a single STRING_ESCAPE token inside a regex expression.
     * The lexer splits regex escapes into separate STRING_ESCAPE tokens,
     * so they are validated individually here.
     */
    fun annotateInvalidRegexEscapeToken(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val text = element.text
        if (!isInvalidRegexEscape(text)) return
        holder
            .newAnnotation(HighlightSeverity.ERROR, invalidEscapeMessage(text))
            .range(element.textRange)
            .create()
    }

    /**
     * Marks invalid PCRE2 escape sequences inside regex literals as errors.
     * PCRE2 does not support: \u, \F, \L, \l, \N{name}, \U
     * For \u and \N{U+...}, suggests the equivalent \x{CODEPOINT} alternative.
     */
    private fun annotateInvalidRegexEscapes(
        text: String,
        startOffset: Int,
        holder: AnnotationHolder,
    ) {
        for (match in REGEX_INVALID_ESCAPE.findAll(text)) {
            val escapeText = match.value
            val range = TextRange(startOffset + match.range.first, startOffset + match.range.last + 1)
            holder
                .newAnnotation(HighlightSeverity.ERROR, invalidEscapeMessage(escapeText))
                .range(range)
                .create()
        }
    }

    /** Error message for an invalid PCRE2 escape, with a fix suggestion when known. */
    private fun invalidEscapeMessage(escapeText: String): String =
        when {
            escapeText.startsWith("\\u") -> unicodeEscapeMessage(escapeText)
            escapeText.startsWith("\\N{") -> namedCharEscapeMessage(escapeText)
            else -> "Invalid regex escape: PCRE2 does not support $escapeText"
        }

    /** `\uXXXX` / `\u{...}` → suggest the `\x{CODEPOINT}` equivalent. */
    private fun unicodeEscapeMessage(escapeText: String): String {
        val hexValue =
            if (escapeText.startsWith("\\u{")) {
                escapeText.removeSurrounding("\\u{", "}")
            } else {
                escapeText.removePrefix("\\u")
            }
        val codepoint = hexValue.toIntOrNull(16)
        val suggestion = if (codepoint != null) "\\x{${codepoint.toString(16).uppercase()}}" else "\\x{CODEPOINT}"
        return "Invalid regex escape: PCRE2 does not support \\u. Use $suggestion instead."
    }

    /** `\N{name}` is unsupported; `\N{U+...}` gets a `\x{CODEPOINT}` suggestion. */
    private fun namedCharEscapeMessage(escapeText: String): String {
        if (escapeText.startsWith("\\N{U+")) {
            val hexValue = escapeText.removeSurrounding("\\N{U+", "}")
            val codepoint = hexValue.toIntOrNull(16)
            val suggestion = if (codepoint != null) "\\x{${codepoint.toString(16).uppercase()}}" else "\\x{CODEPOINT}"
            return "Invalid regex escape: PCRE2 does not support \\N{name}. Use $suggestion instead."
        }
        return "Invalid regex escape: PCRE2 does not support \\N{name}. Use \\x{CODEPOINT} instead."
    }

    /** Check if a STRING_ESCAPE token text is an invalid PCRE2 regex escape. */
    private fun isInvalidRegexEscape(text: String): Boolean =
        text in setOf("\\F", "\\L", "\\l", "\\U") ||
            text.startsWith("\\u") ||
            (text.startsWith("\\N{") && !text.startsWith("\\N{U+"))

    // Regex sub-pattern matching
    private val REGEX_ESCAPE =
        Regex(
            """\\[dDwWsSbtnr0aAfv]|\\[xXu]\{[0-9a-fA-F]+\}|\\[xX][0-9a-fA-F]{1,2}""" +
                """|\\u[0-9a-fA-F]{4}|\\p\{[^}]+\}|\\P\{[^}]+\}|\\k<[^>]+>""" +
                """|\\k'[^']+'|\\[KNRX]|\\[QHhvV]""",
        )

    // Invalid PCRE2 escapes that Crystal's regex engine does not support
    private val REGEX_INVALID_ESCAPE =
        Regex("""\\u[0-9a-fA-F]{4}|\\u\{[0-9a-fA-F]+\}|\\F(?!\w)|\\L(?!\w)|\\l(?!\w)|\\N\{(?!U\+)[^}]*\}|\\U(?!\w)""")

    private val REGEX_QUANTIFIER =
        Regex("""[+\*?]\+[?+]?|[+\*?][?+]?|\{[0-9]+\}|\{[0-9]+,?\}|\{[0-9]+,[0-9]+\}|\{[0-9]+,\}|\{,?[0-9]+\}""")
    private val REGEX_CHAR_CLASS = Regex("""\[(?:\\.|[^\[\]])*\]""")
    private val REGEX_ALTERNATION = Regex("""\|""")
    private val REGEX_ANCHOR = Regex("""[\^$]|\\[AZzG]""")
    private val REGEX_GROUP_PUNCTUATION = Regex("""\(\?[:=!><]|\(\?<=\[!\]|\(\?[imsx]+\-?[imsx]*[:)]|\(\?[#)]|\(\?>""")
    private val REGEX_NAMED_GROUP_PREFIX = Regex("""\(\?<[a-zA-Z_]\w*>""")
    private val REGEX_SIMPLE_GROUP = Regex("""\((?!\?)|\)""")

    /** Sub-pattern → RegExp color, in application order. */
    private val REGEX_HIGHLIGHTS =
        listOf(
            REGEX_CHAR_CLASS to CrystalSyntaxHighlighter.REGEXP_CHAR_CLASS,
            REGEX_ESCAPE to CrystalSyntaxHighlighter.REGEXP_ESC_CHARACTER,
            REGEX_QUANTIFIER to CrystalSyntaxHighlighter.REGEXP_QUANTIFIER,
            REGEX_ALTERNATION to CrystalSyntaxHighlighter.REGEXP_UNION,
            REGEX_ANCHOR to CrystalSyntaxHighlighter.REGEXP_META,
            REGEX_GROUP_PUNCTUATION to CrystalSyntaxHighlighter.REGEXP_PARENTHS,
            REGEX_NAMED_GROUP_PREFIX to CrystalSyntaxHighlighter.REGEXP_PARENTHS,
            REGEX_SIMPLE_GROUP to CrystalSyntaxHighlighter.REGEXP_PARENTHS,
        )
}
