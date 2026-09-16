package io.github.unurgunite.crystal

/**
 * Whole-document balance check for Enter handling: true when block openers
 * and `end`s balance (no additional `end` needed). Split out of
 * `CrystalEnterBlocks` (which exceeded the function budget).
 */
internal object CrystalBalanceTokens {
    // All keywords that open a block (for balance counting)
    private val OPENER_KEYWORDS =
        setOf(
            "def",
            "class",
            "module",
            "struct",
            "enum",
            "if",
            "unless",
            "while",
            "until",
            "case",
            "begin",
            "do",
            "macro",
            "lib",
            "fun",
            "annotation",
            "for",
        )

    /**
     * Check if the entire document has balanced block openers and 'end's.
     * Returns true if openers == ends (balanced), meaning no additional 'end' is needed.
     */
    fun isDocumentBalanced(text: String): Boolean {
        var depth = 0
        for (word in tokenizeWords(text)) {
            when {
                word in OPENER_KEYWORDS -> depth++
                word == "end" -> depth--
            }
        }
        return depth <= 0
    }

    /**
     * Extract words from text, skipping comments and string contents.
     * Delegates per-character dispatch to [advanceTokenizer] to keep the main
     * loop flat.
     */
    private fun tokenizeWords(text: String): List<String> {
        val words = mutableListOf<String>()
        val state = TokenizerState(text)
        while (state.pos < state.len) {
            advanceTokenizer(state, words)
        }
        return words
    }

    /** Mutable cursor for [tokenizeWords]; [pos] advances past consumed characters. */
    private class TokenizerState(
        val text: String,
    ) {
        val len: Int = text.length
        var pos: Int = 0

        /** Consume a quoted span (`"..."` or `'...'`), honouring backslash escapes. */
        fun skipQuoted(quote: Char) {
            pos++ // opening quote
            while (pos < len && text[pos] != quote) {
                if (text[pos] == '\\') pos++
                pos++
            }
            if (pos < len) pos++ // closing quote
        }

        /** Consume a word starting at [pos] into [words]. */
        fun consumeWord(words: MutableList<String>) {
            val start = pos
            while (pos < len && isWordChar(text[pos])) pos++
            words.add(text.substring(start, pos))
        }
    }

    /** Advances [state] by one token: comment, quoted span, word, or single char. */
    private fun advanceTokenizer(
        state: TokenizerState,
        words: MutableList<String>,
    ) {
        val text = state.text
        when {
            text[state.pos] == '#' -> {
                // Skip to end of line (comment)
                while (state.pos < state.len && text[state.pos] != '\n') state.pos++
            }

            text[state.pos] == '"' || text[state.pos] == '\'' -> {
                // Skip string content / char literal
                state.skipQuoted(text[state.pos])
            }

            text[state.pos].isLetter() || text[state.pos] == '_' -> {
                state.consumeWord(words)
            }

            else -> {
                state.pos++
            }
        }
    }
}
