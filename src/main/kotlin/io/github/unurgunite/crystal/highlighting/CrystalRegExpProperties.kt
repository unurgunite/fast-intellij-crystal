package io.github.unurgunite.crystal.highlighting

/**
 * Unicode property tables for Crystal's PCRE-based regex dialect:
 * known `\p{...}` property aliases, property descriptions, character classes
 * and general categories. Split out of `CrystalRegExpLanguageHost` (which
 * exceeded the function budget).
 */
internal object CrystalRegExpProperties {
    val KNOWN_CATEGORIES =
        setOf(
            "L",
            "Lu",
            "Ll",
            "Lt",
            "Lm",
            "Lo",
            "M",
            "Mn",
            "Mc",
            "Me",
            "N",
            "Nd",
            "Nl",
            "No",
            "P",
            "Pc",
            "Pd",
            "Ps",
            "Pe",
            "Pi",
            "Pf",
            "Po",
            "S",
            "Sm",
            "Sc",
            "Sk",
            "So",
            "Z",
            "Zs",
            "Zl",
            "Zp",
            "C",
            "Cc",
            "Cf",
            "Cs",
            "Co",
            "Cn",
        )

    val KNOWN_PROPERTY_NAMES =
        setOf(
            "General_Category",
            "Script",
            "Block",
            "Alpha",
            "ASCII",
            "Assigned",
            "Upper",
            "Lower",
            "Alnum",
            "XPosixPrint",
            "XPosixGraph",
            "XPosixPunct",
            "Any",
            "Cc",
            "Cf",
            "Cn",
            "Co",
            "Cs",
            "Ll",
            "Lm",
            "Lo",
            "Lt",
            "Lu",
            "Mc",
            "Me",
            "Mn",
            "Nd",
            "Nl",
            "No",
            "Pc",
            "Pd",
            "Pe",
            "Pf",
            "Pi",
            "Po",
            "Ps",
            "Sc",
            "Sk",
            "Sm",
            "So",
            "Zl",
            "Zp",
            "Zs",
            "Arabic",
            "Armenian",
            "Balinese",
            "Bengali",
            "Bopomofo",
            "Braille",
            "Buginese",
            "Buhid",
            "Canadian_Aboriginal",
            "Cherokee",
            "Common",
            "Coptic",
            "Cuneiform",
            "Cypriot",
            "Cyrillic",
            "Deseret",
            "Devanagari",
            "Ethiopic",
            "Georgian",
            "Glagolitic",
            "Gothic",
            "Greek",
            "Gujarati",
            "Gurmukhi",
            "Han",
            "Hangul",
            "Hanunoo",
            "Hebrew",
            "Hiragana",
            "Inherited",
            "Kannada",
            "Katakana",
            "Kharoshthi",
            "Khmer",
            "Lao",
            "Latin",
            "Limbu",
            "Linear_B",
            "Malayalam",
            "Mongolian",
            "Myanmar",
            "New_Tai_Lue",
            "Nko",
            "Ogham",
            "Old_Italic",
            "Old_Persian",
            "Oriya",
            "Osmanya",
            "Phags_Pa",
            "Phoenician",
            "Runic",
            "Shavian",
            "Sinhala",
            "Syloti_Nagri",
            "Syriac",
            "Tagalog",
            "Tagbanwa",
            "Tai_Le",
            "Tamil",
            "Telugu",
            "Thaana",
            "Thai",
            "Tibetan",
            "Tifinagh",
            "Ugaritic",
            "Yi",
            "CJK",
            "Hira",
            "Kana",
        )

    /** Multi-alias properties (`General_Category`/`gc`, ...), then singletons. */
    fun allKnownProperties(): Array<Array<String>> =
        ALIASED_PROPERTIES.map { it.toTypedArray() }.toTypedArray() +
            SINGLETON_PROPERTIES.map { arrayOf(it) }.toTypedArray()

    private val ALIASED_PROPERTIES =
        listOf(
            listOf("General_Category", "gc"),
            listOf("Script", "sc"),
            listOf("Block", "blk"),
        )

    private val SINGLETON_PROPERTIES =
        listOf(
            "Age",
            "Bidi_Class",
            "Bidi_Paired_Bracket_Type",
            "Case_Folding",
            "Decomposition_Type",
            "East_Asian_Width",
            "Grapheme_Cluster_Break",
            "Hangul_Syllable_Type",
            "Indic_Syllabic_Category",
            "Joining_Group",
            "Joining_Type",
            "Line_Break",
            "Numeric_Type",
            "Numeric_Value",
            "Sentence_Break",
            "Word_Break",
            "Emoji",
            "Emoji_Presentation",
            "Emoji_Modifier",
            "Emoji_Modifier_Base",
            "Extended_Pictographic",
            "ID_Continue",
            "ID_Start",
            "ASCII_Hex_Digit",
            "Alphabetic",
            "Bidi_Control",
            "Bidi_Mirrored",
            "Case_Ignorable",
            "Cased",
            "Changes_When_Casefolded",
            "Changes_When_Casemapped",
            "Changes_When_Lowercased",
            "Changes_When_NFKC_Casefolded",
            "Changes_When_Titlecased",
            "Changes_When_Uppercased",
            "Dash",
            "Default_Ignorable_Code_Point",
            "Deprecated",
            "Diacritic",
            "Extender",
            "Grapheme_Base",
            "Grapheme_Extend",
            "Grapheme_Link",
            "Hex_Digit",
            "Hyphen",
            "IDS_Binary_Operator",
            "IDS_Trinary_Operator",
            "Ideographic",
            "Join_Control",
            "Logical_Order_Exception",
            "Lowercase",
            "Math",
            "Noncharacter_Code_Point",
            "Pattern_Syntax",
            "Pattern_White_Space",
            "Quotation_Mark",
            "Radical",
            "Regional_Indicator",
            "Sentence_Terminal",
            "Soft_Dotted",
            "Terminal_Punctuation",
            "Unified_Ideograph",
            "Uppercase",
            "Variation_Selector",
            "White_Space",
            "XID_Continue",
            "XID_Start",
        )

    private val PROPERTY_DESCRIPTIONS =
        mapOf(
            "General_Category" to "General Category",
            "Script" to "Script",
            "Block" to "Unicode Block",
            "Age" to "Age of the character",
            "Emoji" to "Emoji property",
            "ASCII_Hex_Digit" to "ASCII Hex Digit",
            "White_Space" to "White Space",
        )

    /** Known description, or the name itself when undocumented. */
    fun propertyDescription(name: String?): String? {
        if (name == null) return null
        return PROPERTY_DESCRIPTIONS.getOrDefault(name, name)
    }

    fun knownCharacterClasses(): Array<Array<String>> = CHARACTER_CLASSES.map { it.toTypedArray() }.toTypedArray()

    private val CHARACTER_CLASSES =
        listOf(
            listOf("d", "digit [0-9]"),
            listOf("D", "non-digit [^0-9]"),
            listOf("w", "word character [a-zA-Z0-9_]"),
            listOf("W", "non-word character"),
            listOf("s", "whitespace [ \\t\\r\\n\\f]"),
            listOf("S", "non-whitespace"),
            listOf("h", "horizontal whitespace"),
            listOf("H", "non-horizontal whitespace"),
            listOf("v", "vertical whitespace"),
            listOf("V", "non-vertical whitespace"),
            listOf("R", "line break"),
            listOf("N", "non-line break"),
            listOf("K", "reset match start"),
            listOf("X", "extended Unicode grapheme cluster"),
            listOf("b", "word boundary"),
            listOf("B", "non-word boundary"),
            listOf("A", "start of string"),
            listOf("Z", "end of string, before final newline"),
            listOf("z", "absolute end of string"),
            listOf("G", "first matching position in subject"),
        )
}

/**
 * `\p{...}` name/value validation for the Crystal PCRE dialect.
 */
internal object CrystalRegExpValidation {
    // Shortest plausible Unicode script name ("Latn" is 4 chars; 3 keeps the
    // check permissive — it only rejects obvious garbage, not real scripts).
    private const val MIN_SCRIPT_NAME_LENGTH = 3

    fun isValidCategory(category: String): Boolean = category.length == 2 || category in CrystalRegExpProperties.KNOWN_CATEGORIES

    fun isValidPropertyName(name: String): Boolean =
        name.isNotEmpty() && (name in CrystalRegExpProperties.KNOWN_PROPERTY_NAMES || name[0].isUpperCase())

    fun isValidPropertyValue(
        name: String,
        value: String,
    ): Boolean {
        if (name == "General_Category" || name == "gc") return isValidCategory(value)
        if (name == "Script" || name == "sc") return value.length >= MIN_SCRIPT_NAME_LENGTH
        return true
    }
}
