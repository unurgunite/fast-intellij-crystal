package io.github.unurgunite.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
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
     *
     * A single combined scan replaces the per-pattern `findAll` loop: nine
     * full passes over a generated route-table regex (25 KB, ~4 200 matches)
     * made the annotator quadratic and froze the IDE. Emission is also capped
     * — past [MAX_ANNOTATIONS] a literal is left plain-highlighted, since no
     * human reads the sub-pattern colors of a machine-generated table anyway.
     */
    fun annotateRegexLiteral(
        text: String,
        startOffset: Int,
        holder: AnnotationHolder,
    ) {
        // Validate invalid PCRE2 escapes before highlighting (single cheap pass)
        annotateInvalidRegexEscapes(text, startOffset, holder)

        // shortcut: monotonic single scan with a hard emission cap; upgrade to
        // a real incremental lexer state only if multi-MB literals must be colored.
        for ((match, key) in REGEX_COMBINED.findAll(text).mapNotNull { colorOf(it) }.take(MAX_ANNOTATIONS)) {
            CrystalAnnotationEmit.applyRange(holder, startOffset, match.range, key)
        }
    }

    /**
     * The color of the first named group that matched, in [REGEX_COMBINED_KEYS]
     * order — the alternation lists patterns in the old first-wins precedence.
     */
    private fun colorOf(match: MatchResult): Pair<MatchResult, TextAttributesKey>? =
        REGEX_COMBINED_KEYS.firstNotNullOfOrNull { (groupName, key) ->
            match.groups[groupName]?.let { match to key }
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

    // Invalid PCRE2 escapes that Crystal's regex engine does not support
    private val REGEX_INVALID_ESCAPE =
        Regex("""\\u[0-9a-fA-F]{4}|\\u\{[0-9a-fA-F]+\}|\\F(?!\w)|\\L(?!\w)|\\l(?!\w)|\\N\{(?!U\+)[^}]*\}|\\U(?!\w)""")

    // Regex sub-pattern matching. Order inside REGEX_COMBINED duplicates the
    // old first-wins application order: at a given offset the first alternative
    // that matches wins, exactly as the earlier sequential passes did.
    private const val CHAR_CLASS = "\\[(?:\\.|[^\\[\\]])*\\]"
    private const val ESCAPE =
        "\\\\[dDwWsSbtnr0aAfv]|\\\\[xXu]\\{[0-9a-fA-F]+\\}|\\\\[xX][0-9a-fA-F]{1,2}" +
            "|\\\\u[0-9a-fA-F]{4}|\\\\p\\{[^}]+\\}|\\\\P\\{[^}]+\\}|\\\\k<[^>]+>" +
            "|\\\\k'[^']+'|\\\\[KNRX]|\\\\[QHhvV]"
    private const val QUANTIFIER =
        "[+\\*?]\\+[?+]?|[+\\*?][?+]?|\\{[0-9]+\\}|\\{[0-9]+,?\\}|\\{[0-9]+,[0-9]+\\}|\\{[0-9]+,\\}|\\{,?[0-9]+\\}"
    private const val ANCHOR = "[\\^$]|\\\\[AZzG]"
    private const val GROUP_PUNCTUATION = "\\(\\?[:=!><]|\\(\\?<=\\[!\\]|\\(\\?[imsx]+\\-?[imsx]*[:)]|\\(\\?[#)]|\\(\\?>"
    private const val NAMED_GROUP_PREFIX = "\\(\\?<[a-zA-Z_]\\w*>"
    private const val SIMPLE_GROUP = "\\((?!\\?)|\\)"

    /**
     * One alternation covering every colored sub-pattern. Each alternative is a
     * named group so the match reveals which color to apply.
     */
    private val REGEX_COMBINED =
        Regex(
            "(?<charClass>$CHAR_CLASS)" +
                "|(?<escape>$ESCAPE)" +
                "|(?<quantifier>$QUANTIFIER)" +
                "|(?<alternation>\\|)" +
                "|(?<anchor>$ANCHOR)" +
                "|(?<groupPunctuation>$GROUP_PUNCTUATION)" +
                "|(?<namedGroupPrefix>$NAMED_GROUP_PREFIX)" +
                "|(?<simpleGroup>$SIMPLE_GROUP)",
        )

    /** Group name → RegExp color, in the order [REGEX_COMBINED] lists them. */
    private val REGEX_COMBINED_KEYS =
        listOf(
            "charClass" to CrystalSyntaxHighlighter.REGEXP_CHAR_CLASS,
            "escape" to CrystalSyntaxHighlighter.REGEXP_ESC_CHARACTER,
            "quantifier" to CrystalSyntaxHighlighter.REGEXP_QUANTIFIER,
            "alternation" to CrystalSyntaxHighlighter.REGEXP_UNION,
            "anchor" to CrystalSyntaxHighlighter.REGEXP_META,
            "groupPunctuation" to CrystalSyntaxHighlighter.REGEXP_PARENTHS,
            "namedGroupPrefix" to CrystalSyntaxHighlighter.REGEXP_PARENTHS,
            "simpleGroup" to CrystalSyntaxHighlighter.REGEXP_PARENTHS,
        )

    /**
     * Upper bound on annotations emitted for one literal. A generated route
     * table produced ~4 200 group matches on a single 25 KB line; coloring past
     * this point costs seconds and nobody reads it.
     */
    private const val MAX_ANNOTATIONS = 2_000
}
