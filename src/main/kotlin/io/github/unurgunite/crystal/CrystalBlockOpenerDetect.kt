package io.github.unurgunite.crystal

/**
 * Block-opener detection for Enter handling: does a line end with a
 * block-opening construct (`def`/`if`/`do`/...) that needs a matching `end`?
 * Split out of `CrystalEnterBlocks` (which exceeded the function budget).
 */
internal object CrystalBlockOpenerDetect {
    // Keywords that open a block requiring 'end'
    private val BLOCK_OPENERS =
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
        )

    private val DEFINITION_KEYWORDS = setOf("def", "class", "module", "struct", "enum", "macro", "lib", "fun", "annotation")
    private val CONTROL_KEYWORDS = setOf("if", "unless", "while", "until", "case", "begin", "for")

    internal val ASSIGN_KEYWORD_PATTERN = Regex("""=\s+(if|unless|while|until|case|begin)\b""")

    /**
     * Check if a line (trimmed) ends with a block-opening construct.
     */
    fun endsWithBlockOpener(trimmed: String): Boolean {
        val words = trimmed.split(Regex("\\s+"))
        return endsWithOpenerWord(words.lastOrNull()) ||
            startsWithDefinitionKeyword(trimmed) ||
            startsWithControlKeyword(trimmed) ||
            hasAssignBlockKeyword(trimmed) ||
            hasStandaloneDo(trimmed)
    }

    /** Direct keyword at end of line: "do", "begin", etc. */
    private fun endsWithOpenerWord(lastWord: String?): Boolean = lastWord != null && lastWord in BLOCK_OPENERS

    /**
     * Lines starting with block keywords: "def foo", "def foo(x)", "class Foo < Bar".
     * The line may end with ")" or identifier, but starts with the keyword.
     */
    private fun startsWithDefinitionKeyword(trimmed: String): Boolean {
        val firstWord = trimmed.trimStart().split(Regex("\\s+")).firstOrNull()
        return firstWord in DEFINITION_KEYWORDS
    }

    /**
     * "if expr", "unless expr", "while expr", "until expr", "case expr".
     * But NOT suffix-if: "return x if condition" — suffix-if has something before "if".
     */
    private fun startsWithControlKeyword(trimmed: String): Boolean {
        val firstWord = trimmed.trimStart().split(Regex("\\s+")).firstOrNull()
        return firstWord in CONTROL_KEYWORDS
    }

    /**
     * "var = if expr", "result = case x", "var = begin".
     * Assignment to a block keyword — NOT suffix-if (which has no = before the keyword).
     */
    private fun hasAssignBlockKeyword(trimmed: String): Boolean = ASSIGN_KEYWORD_PATTERN.containsMatchIn(trimmed)

    /**
     * "do" appearing as a standalone word in the middle of a line:
     * 3.times do |i|, .each do, loop do, etc.
     * This must NOT match "do" inside identifiers like "undo" or "method_do".
     */
    private fun hasStandaloneDo(trimmed: String): Boolean = Regex("""\bdo\b""").containsMatchIn(trimmed)
}
