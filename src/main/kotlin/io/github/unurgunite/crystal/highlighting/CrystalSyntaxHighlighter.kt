package io.github.unurgunite.crystal.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import io.github.unurgunite.crystal.lexer.CrystalLexerAdapter
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalSyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        val KEYWORD = createTextAttributesKey("CRYSTAL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val NUMBER = createTextAttributesKey("CRYSTAL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING = createTextAttributesKey("CRYSTAL_STRING", DefaultLanguageHighlighterColors.STRING)
        val COMMENT = createTextAttributesKey("CRYSTAL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val IDENTIFIER = createTextAttributesKey("CRYSTAL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val CONSTANT = createTextAttributesKey("CRYSTAL_CONSTANT", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val INSTANCE_VAR = createTextAttributesKey("CRYSTAL_INSTANCE_VAR", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val CLASS_VAR = createTextAttributesKey("CRYSTAL_CLASS_VAR", DefaultLanguageHighlighterColors.STATIC_FIELD)
        val GLOBAL_VAR = createTextAttributesKey("CRYSTAL_GLOBAL_VAR", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE)
        val SYMBOL = createTextAttributesKey("CRYSTAL_SYMBOL", DefaultLanguageHighlighterColors.NUMBER)
        val OPERATOR = createTextAttributesKey("CRYSTAL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val COMMA = createTextAttributesKey("CRYSTAL_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val SEMICOLON = createTextAttributesKey("CRYSTAL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val COLON = createTextAttributesKey("CRYSTAL_COLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val DOT = createTextAttributesKey("CRYSTAL_DOT", DefaultLanguageHighlighterColors.DOT)
        val DOUBLE_COLON = createTextAttributesKey("CRYSTAL_DOUBLE_COLON", DefaultLanguageHighlighterColors.DOT)
        val PARENTHESES = createTextAttributesKey("CRYSTAL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS = createTextAttributesKey("CRYSTAL_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val BRACES = createTextAttributesKey("CRYSTAL_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val CHAR = createTextAttributesKey("CRYSTAL_CHAR", DefaultLanguageHighlighterColors.STRING)
        val REGEX = createTextAttributesKey("CRYSTAL_REGEX", DefaultLanguageHighlighterColors.STRING)
        val INTERPOLATION = createTextAttributesKey("CRYSTAL_INTERPOLATION", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val FUNCTION_DECLARATION = createTextAttributesKey("CRYSTAL_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.CONSTANT)
        val CLASS_DECLARATION = createTextAttributesKey("CRYSTAL_CLASS_DECLARATION", DefaultLanguageHighlighterColors.CONSTANT)
        val PARAMETER = createTextAttributesKey("CRYSTAL_PARAMETER_V2", DefaultLanguageHighlighterColors.PARAMETER)
        val BAD_CHARACTER = createTextAttributesKey("CRYSTAL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
        val MACRO_FRESH_VAR = createTextAttributesKey("CRYSTAL_MACRO_FRESH_VAR", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
        val STRING_ESCAPE = createTextAttributesKey("CRYSTAL_STRING_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val TODO_COMMENT = createTextAttributesKey("CRYSTAL_TODO_COMMENT", DefaultLanguageHighlighterColors.NUMBER)
        val HEREDOC_DELIMITER = createTextAttributesKey("CRYSTAL_HEREDOC_DELIMITER", DefaultLanguageHighlighterColors.PARAMETER)

        // Crystal's own RegExp sub-pattern colors. These deliberately live in the
        // CRYSTAL_REGEXP.* namespace (not REGEXP.*): the platform's RegExpHighlighter
        // registers the same REGEXP.* external names with different fallbacks, and
        // whoever initializes second crashes in TextAttributesKey.mergeKeys
        // (broke CI verifyPlugin's buildSearchableOptions, order-dependent).
        // Fallbacks mirror RubyMine's RegExp colors so sub-patterns look identical.
        val REGEXP_CHAR_CLASS = createTextAttributesKey("CRYSTAL_REGEXP.CHAR_CLASS", DefaultLanguageHighlighterColors.STRING)
        val REGEXP_ESC_CHARACTER =
            createTextAttributesKey("CRYSTAL_REGEXP.ESC_CHARACTER", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val REGEXP_QUANTIFIER = createTextAttributesKey("CRYSTAL_REGEXP.QUANTIFIER", DefaultLanguageHighlighterColors.NUMBER)
        val REGEXP_UNION = createTextAttributesKey("CRYSTAL_REGEXP.UNION", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val REGEXP_PARENTHS = createTextAttributesKey("CRYSTAL_REGEXP.PARENTHS", DefaultLanguageHighlighterColors.PARENTHESES)
        val REGEXP_META = createTextAttributesKey("CRYSTAL_REGEXP.META", DefaultLanguageHighlighterColors.KEYWORD)

        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val STRING_KEYS = arrayOf(STRING)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val CONSTANT_KEYS = arrayOf(CONSTANT)
        private val INSTANCE_VAR_KEYS = arrayOf(INSTANCE_VAR)
        private val CLASS_VAR_KEYS = arrayOf(CLASS_VAR)
        private val GLOBAL_VAR_KEYS = arrayOf(GLOBAL_VAR)
        private val SYMBOL_KEYS = arrayOf(SYMBOL)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val COMMA_KEYS = arrayOf(COMMA)
        private val SEMICOLON_KEYS = arrayOf(SEMICOLON)
        private val COLON_KEYS = arrayOf(COLON)
        private val DOT_KEYS = arrayOf(DOT)
        private val DOUBLE_COLON_KEYS = arrayOf(DOUBLE_COLON)
        private val PARENTHESES_KEYS = arrayOf(PARENTHESES)
        private val BRACKETS_KEYS = arrayOf(BRACKETS)
        private val BRACES_KEYS = arrayOf(BRACES)
        private val CHAR_KEYS = arrayOf(CHAR)
        private val REGEX_KEYS = arrayOf(REGEX)
        private val INTERPOLATION_KEYS = arrayOf(INTERPOLATION)
        private val STRING_ESCAPE_KEYS = arrayOf(STRING_ESCAPE)
        private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
        private val HEREDOC_DELIMITER_KEYS = arrayOf(HEREDOC_DELIMITER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = CrystalLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when {
            tokenType == null -> EMPTY_KEYS
            tokenType in CrystalTokenTypes.KEYWORDS -> KEYWORD_KEYS
            tokenType in CrystalTokenTypes.NUMBERS -> NUMBER_KEYS
            tokenType in singletonHighlights -> singletonHighlights.getValue(tokenType)
            tokenType in multiTokenHighlights -> multiTokenHighlights.getValue(tokenType)
            tokenType in CrystalTokenTypes.OPERATORS -> OPERATOR_KEYS
            else -> highlightsForPunctuation(tokenType)
        }

    /**
     * One token type → one key. Table-driven so adding a token is one map entry
     * instead of another `when` branch.
     */
    private val singletonHighlights: Map<IElementType, Array<TextAttributesKey>> by lazy {
        mapOf(
            CrystalTypes.STRING_ESCAPE to STRING_ESCAPE_KEYS,
            CrystalTypes.CHAR_LITERAL to CHAR_KEYS,
            CrystalTypes.SYMBOL_LITERAL to SYMBOL_KEYS,
            CrystalTypes.SYMBOL_COLON to SYMBOL_KEYS,
            CrystalTypes.MACRO_FRESH_VAR to arrayOf(MACRO_FRESH_VAR),
            CrystalTypes.LINE_COMMENT to COMMENT_KEYS,
            CrystalTypes.INSTANCE_VAR to INSTANCE_VAR_KEYS,
            CrystalTypes.CLASS_VAR to CLASS_VAR_KEYS,
            CrystalTypes.GLOBAL_VAR to GLOBAL_VAR_KEYS,
            CrystalTypes.COMMA to COMMA_KEYS,
            CrystalTypes.SEMICOLON to SEMICOLON_KEYS,
            CrystalTypes.COLON to COLON_KEYS,
            CrystalTypes.DOT to DOT_KEYS,
            CrystalTypes.DOUBLE_COLON to DOUBLE_COLON_KEYS,
            CrystalTypes.LPAREN to PARENTHESES_KEYS,
            CrystalTypes.RPAREN to PARENTHESES_KEYS,
            CrystalTypes.LBRACKET to BRACKETS_KEYS,
            CrystalTypes.RBRACKET to BRACKETS_KEYS,
            CrystalTypes.LBRACE to BRACES_KEYS,
            CrystalTypes.RBRACE to BRACES_KEYS,
        )
    }

    /**
     * Several token types share one key (string-ish, symbol-ish, interpolation
     * delimiters). Grouped by key so the mapping reads as what-it-highlights-as.
     */
    private val multiTokenHighlights: Map<IElementType, Array<TextAttributesKey>> by lazy {
        buildMap {
            val stringish =
                listOf(
                    CrystalTypes.STRING_LITERAL,
                    CrystalTypes.COMMAND_LITERAL,
                    CrystalTypes.HEREDOC_CONTENT,
                    CrystalTypes.PERCENT_LITERAL_BEGIN,
                    CrystalTypes.PERCENT_LITERAL_END,
                    CrystalTypes.MACRO_BODY_CONTENT,
                )
            for (type in stringish) put(type, STRING_KEYS)
            put(CrystalTypes.HEREDOC_START, HEREDOC_DELIMITER_KEYS)
            put(CrystalTypes.HEREDOC_END, HEREDOC_DELIMITER_KEYS)
            val symbolish =
                listOf(
                    CrystalTypes.PERCENT_SYMBOL_BEGIN,
                    CrystalTypes.PERCENT_SYMBOL_END,
                )
            for (type in symbolish) put(type, SYMBOL_KEYS)
            val interpolationish =
                listOf(
                    CrystalTypes.STRING_INTERPOLATION_BEGIN,
                    CrystalTypes.STRING_INTERPOLATION_END,
                    CrystalTypes.MACRO_INTERPOLATION_BEGIN,
                    CrystalTypes.MACRO_INTERPOLATION_END,
                    CrystalTypes.MACRO_CONTROL_BEGIN,
                    CrystalTypes.MACRO_CONTROL_END,
                )
            for (type in interpolationish) put(type, INTERPOLATION_KEYS)
        }
    }

    private fun highlightsForPunctuation(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            // IDENTIFIER and CONSTANT are handled by the Annotator (context-sensitive)
            CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT -> EMPTY_KEYS

            CrystalTypes.REGEX_LITERAL -> REGEX_KEYS

            CrystalTokenTypes.BAD_CHARACTER -> BAD_CHARACTER_KEYS

            else -> EMPTY_KEYS
        }
}
