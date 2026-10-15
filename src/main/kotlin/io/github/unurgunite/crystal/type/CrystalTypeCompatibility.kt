package io.github.unurgunite.crystal.type

/**
 * Determines whether an argument type is compatible with a parameter type,
 * considering Crystal's type system rules including union types, nilable types,
 * and numeric autocasting.
 */
object CrystalTypeCompatibility {
    /** All known built-in types where we can confidently detect mismatches. */
    private val KNOWN_BUILTINS =
        setOf(
            "Int8",
            "Int16",
            "Int32",
            "Int64",
            "Int128",
            "UInt8",
            "UInt16",
            "UInt32",
            "UInt64",
            "UInt128",
            "Float32",
            "Float64",
            "String",
            "Char",
            "Bool",
            "Nil",
            "Symbol",
            "Regex",
        )

    /** All numeric types (integers + floats). */
    private val NUMERIC_TYPES =
        setOf(
            "Int8",
            "Int16",
            "Int32",
            "Int64",
            "Int128",
            "UInt8",
            "UInt16",
            "UInt32",
            "UInt64",
            "UInt128",
            "Float32",
            "Float64",
        )

    /** All integer types. */
    private val INTEGER_TYPES =
        setOf(
            "Int8",
            "Int16",
            "Int32",
            "Int64",
            "Int128",
            "UInt8",
            "UInt16",
            "UInt32",
            "UInt64",
            "UInt128",
        )

    /**
     * Numeric autocast hierarchy: which suffixed types can be implicitly widened.
     * Crystal allows lossless widening (no precision loss).
     * Int → Float is NOT allowed (precision loss).
     */
    private val NUMERIC_AUTOCAST: Map<String, Set<String>> =
        mapOf(
            "Int8" to setOf("Int16", "Int32", "Int64", "Int128"),
            "Int16" to setOf("Int32", "Int64", "Int128"),
            "Int32" to setOf("Int64", "Int128"),
            "Int64" to setOf("Int128"),
            "UInt8" to setOf("UInt16", "UInt32", "UInt64", "UInt128", "Int16", "Int32", "Int64", "Int128"),
            "UInt16" to setOf("UInt32", "UInt64", "UInt128", "Int32", "Int64", "Int128"),
            "UInt32" to setOf("UInt64", "UInt128", "Int64", "Int128"),
            "UInt64" to setOf("UInt128", "Int128"),
            "Float32" to setOf("Float64"),
        )

    /**
     * Checks if an argument type is compatible with a parameter type.
     *
     * @param argType The resolved type of the argument
     * @param paramType The declared type of the parameter (from type annotation)
     * @param isUnsuffixedNumericLiteral If true, the argument is a numeric literal without suffix
     *        (Crystal autocasts these to any numeric type that can hold the value)
     * @return true if compatible, false if definite mismatch
     */
    fun isCompatible(
        argType: String,
        paramType: String,
        isUnsuffixedNumericLiteral: Boolean = false,
    ): Boolean {
        val normalizedArg = normalizeType(argType)
        val normalizedParam = normalizeType(paramType)

        return when {
            // Exact match
            normalizedArg == normalizedParam -> {
                true
            }

            // Union argument (`String | Hash(String, String)` from overload
            // union): fits when ANY member fits — the call may take any
            // branch at runtime. Checked before generic/nilable branches so
            // a fitting member wins over a mismatching first member.
            // Depth-aware split: naive split("|") would cut inside generic
            // parens (`Hash(K | L, V | W)` → `Hash(K`, `L, V`, `W)`).
            // Single-member result (all `|` nested) is not a union at all —
            // fall through to generic handling instead of self-recursing.
            isMultiMemberUnion(normalizedArg) -> {
                splitTopLevel(normalizedArg).any { isCompatible(it.trim(), normalizedParam, isUnsuffixedNumericLiteral) }
            }

            // Nilable "Type?" is sugar for "Type | Nil"
            normalizedParam.endsWith("?") -> {
                isCompatibleNilable(normalizedArg, normalizedParam, isUnsuffixedNumericLiteral)
            }

            // Union "Type1 | Type2 | ...": any member may match.
            // Same single-member guard as the argument side.
            isMultiMemberUnion(normalizedParam) -> {
                splitTopLevel(normalizedParam).any { isCompatible(normalizedArg, it.trim(), isUnsuffixedNumericLiteral) }
            }

            // Unsuffixed numeric literals autocast to ANY numeric type
            isUnsuffixedNumericLiteral && normalizedParam in NUMERIC_TYPES -> {
                true
            }

            // Suffixed numeric autocast (lossless widening)
            normalizedArg in NUMERIC_TYPES && normalizedParam in NUMERIC_TYPES -> {
                NUMERIC_AUTOCAST[normalizedArg]?.contains(normalizedParam) == true
            }

            // Generic shapes: both, or neither, must be generic
            normalizedParam.contains("(") || normalizedArg.contains("(") -> {
                isCompatibleGeneric(normalizedArg, normalizedParam, isUnsuffixedNumericLiteral)
            }

            // Unknown side: could be a superclass, alias or generic — never a definite mismatch
            normalizedParam !in KNOWN_BUILTINS || normalizedArg !in KNOWN_BUILTINS -> {
                true
            }

            // Both are known builtins and nothing matched → incompatible
            else -> {
                false
            }
        }
    }

    /**
     * Nilable check: `Nil` always fits; otherwise recurse against the base type
     * (so autocast rules still apply, e.g. `Int8` fits `Int32?`).
     */
    private fun isCompatibleNilable(
        argType: String,
        normalizedParam: String,
        isUnsuffixedNumericLiteral: Boolean,
    ): Boolean {
        val baseType = normalizedParam.dropLast(1).trim()
        if (argType == "Nil" || argType == baseType) return true
        return isCompatible(argType, baseType, isUnsuffixedNumericLiteral)
    }

    /**
     * Generic check: `Array(Int32)` vs `Array(Int32)`, `Hash(String, Int32)` etc.
     * Same base with pairwise-compatible args fits; mismatched shapes never fit.
     * Unbound type variables (`K`, `V`, `T` from `Hash(K, V)`, `Array(T)`)
     * fit anything — they are placeholders the definition never constrains,
     * not concrete types to mismatch against.
     */
    private fun isCompatibleGeneric(
        argType: String,
        normalizedParam: String,
        isUnsuffixedNumericLiteral: Boolean,
    ): Boolean {
        val paramHasArgs = normalizedParam.contains("(")
        val argHasArgs = argType.contains("(")
        // Bare generic against parameterized (`Hash` vs `Hash(String, String)`
        // with the same base): unparameterized means "args unknown", never a
        // definite mismatch — same leniency as unknown types elsewhere.
        // Alias-aware (`Bytes` vs `Slice(UInt8)`). A bare side that is a
        // KNOWN alias expands first (`Bytes` → `Slice(UInt8)`), so concrete
        // mismatches (`Slice(String)` vs `Bytes`) still fail; a truly
        // unknown bare side (`Hash`) stays lenient.
        if (paramHasArgs != argHasArgs) {
            return BareGenericCheck.check(argType, normalizedParam, isUnsuffixedNumericLiteral)
        }
        val paramBase = normalizedParam.substringBefore("(").trim()
        val argBase = argType.substringBefore("(").trim()
        if (!sameGenericBase(paramBase, argBase)) return false
        val paramInner = extractGenericTypeArgs(normalizedParam)
        val argInner = extractGenericTypeArgs(argType)
        if (paramInner.size != argInner.size) return false
        return paramInner.zip(argInner).all { (p, a) ->
            isUnboundTypeVariable(p) || isUnboundTypeVariable(a) || isCompatible(a, p, isUnsuffixedNumericLiteral)
        }
    }

    /**
     * Known generic alias bases (`Bytes` is `Slice(UInt8)` in stdlib):
     * `Slice(T)` fits `Bytes` when the slice arg fits `UInt8`.
     * Internal: shared with BareGenericCheck.
     */
    internal val GENERIC_ALIAS_BASES = mapOf("Bytes" to "Slice")

    /** True for equal bases modulo known aliases. Internal: shared with BareGenericCheck. */
    internal fun sameGenericBase(
        paramBase: String,
        argBase: String,
    ): Boolean {
        if (paramBase == argBase) return true
        if (GENERIC_ALIAS_BASES[paramBase] == argBase) return true
        if (GENERIC_ALIAS_BASES[argBase] == paramBase) return true
        return false
    }

    /**
     * True for unbound generic placeholders: single uppercase name (`K`,
     * `V`, `T`) or `Type`-suffixed variables. A union whose every member
     * is a placeholder (`K | L` from stdlib `Hash#merge : Hash(K | L, ...)`)
     * also qualifies — no concrete type constrains that slot.
     * Concrete types (`String`, `Int32`, `Hash(...)`) never qualify —
     * only bare variables do.
     */
    private fun isUnboundTypeVariable(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length == 1 && trimmed[0].isUpperCase()) return true
        if (trimmed.length == 2 && trimmed[0].isUpperCase() && trimmed[1].isDigit()) return true
        if (trimmed.contains("|")) {
            val members = splitTopLevel(trimmed)
            if (members.isNotEmpty() && members.all { isUnboundTypeVariable(it) }) return true
        }
        return false
    }

    /**
     * Normalizes a type string by trimming whitespace and handling common patterns.
     *
     * Crystal shorthand type aliases that expand to union types.
     */
    private val TYPE_ALIASES =
        mapOf(
            "Int" to "Int8 | Int16 | Int32 | Int64 | Int128",
            "UInt" to "UInt8 | UInt16 | UInt32 | UInt64 | UInt128",
            "Float" to "Float32 | Float64",
            "Number" to "Int8 | Int16 | Int32 | Int64 | Int128 | UInt8 | UInt16 | UInt32 | UInt64 | UInt128 | Float32 | Float64",
        )

    private fun normalizeType(type: String): String {
        // Leading `::` is semantically void (`::Set(String)` == `Set(String)`).
        val trimmed = type.trim().removePrefix("::")
        return TYPE_ALIASES[trimmed] ?: trimmed
    }

    /**
     * Extracts generic type arguments from a type like "Array(Int32)" → ["Int32"]
     * or "Hash(String, Int32)" → ["String", "Int32"].
     * Handles nested parens by tracking depth.
     */
    private fun extractGenericTypeArgs(type: String): List<String> {
        val openIdx = type.indexOf('(')
        val closeIdx = type.lastIndexOf(')')
        if (openIdx < 0 || closeIdx < 0 || closeIdx <= openIdx) return emptyList()
        val inner = type.substring(openIdx + 1, closeIdx)
        // Split by comma, respecting nested parens
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in inner.indices) {
            when (inner[i]) {
                '(' -> {
                    depth++
                }

                ')' -> {
                    depth--
                }

                ',' -> {
                    if (depth == 0) {
                        result.add(inner.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
        }
        result.add(inner.substring(start).trim())
        return result
    }

    /**
     * Top-level `|` split respecting generic parens (`Hash(K | L, V | W)`
     * stays one member). File-level shape keeps isCompatible under
     * detekt's ReturnCount budget.
     */
    private fun splitTopLevel(typeText: String): List<String> = CrystalMethodReturnTypeInference.splitTypeNames(typeText)

    /**
     * True for a real (multi-member) top-level union. Guards both union
     * branches: a `|` nested inside generic parens (`Hash(K | L, V | W)`)
     * is not a union split — without the guard the branch self-recurses
     * on the single unsplit member (StackOverflow). File-level: keeps
     * isCompatible under complexity budget.
     */
    private fun isMultiMemberUnion(typeText: String): Boolean {
        if (!typeText.contains("|")) return false
        return splitTopLevel(typeText).size > 1
    }

    /**
     * Returns the set of expected types for error messages.
     * For union types, returns all members.
     */
    fun describeExpectedType(paramType: String): String {
        val normalized = normalizeType(paramType)
        if (normalized.endsWith("?")) {
            return "${normalized.dropLast(1).trim()} | Nil"
        }
        return normalized
    }
}

/**
 * Bare generic against parameterized (`Hash` vs `Hash(String, String)`).
 * Separate object: keeps isCompatibleGeneric under ReturnCount budget.
 * Known aliases expand first (`Bytes` → `Slice(UInt8)`); otherwise
 * same-base means "args unknown" (lenient), different bases mismatch.
 */
internal object BareGenericCheck {
    fun check(
        argType: String,
        normalizedParam: String,
        isUnsuffixedNumericLiteral: Boolean,
    ): Boolean {
        expandKnownAlias(argType)?.let {
            return CrystalTypeCompatibility.isCompatible(it, normalizedParam, isUnsuffixedNumericLiteral)
        }
        expandKnownAlias(normalizedParam)?.let {
            return CrystalTypeCompatibility.isCompatible(argType, it, isUnsuffixedNumericLiteral)
        }
        val paramBase = normalizedParam.substringBefore("(").trim()
        val argBase = argType.substringBefore("(").trim()
        if (!CrystalTypeCompatibility.sameGenericBase(paramBase, argBase)) return false
        return paramBase.firstOrNull()?.isUpperCase() == true
    }

    private fun expandKnownAlias(typeText: String): String? {
        val trimmed = typeText.trim()
        if (trimmed.contains("(")) return null
        return GENERIC_ALIAS_EXPANSIONS[trimmed]
    }

    private val GENERIC_ALIAS_EXPANSIONS = mapOf("Bytes" to "Slice(UInt8)")
}
