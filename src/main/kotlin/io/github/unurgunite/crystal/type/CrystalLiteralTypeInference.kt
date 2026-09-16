package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.psi.CrystalExpression
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver

/**
 * Type inference for Crystal literal shapes: scalar literals (strings, chars,
 * symbols, booleans, nil, numbers) and collection literals (arrays, hashes, tuples).
 */
object CrystalLiteralTypeInference {
    // Shortest quoted char literal: `'x'` (quote + char + quote).
    private const val MIN_QUOTED_CHAR_LENGTH = 3

    /** Scalar literal type name, or null when [expr] is not a literal. */
    fun inferFromLiteral(expr: PsiElement): String? {
        val text = expr.text.trim()
        inferQuotedLiteralType(text)?.let { return it }
        inferKeywordLiteralType(text)?.let { return it }
        return inferNumericLiteralType(text)
    }

    /** Array/hash/tuple literal shapes, via PSI first and text fallback second. */
    fun inferFromCollectionShape(expr: PsiElement): List<String>? {
        val text = expr.text.trim()

        // Array literal
        if (text.startsWith("[")) return listOf("Array")

        // Hash / tuple literal: extract from CrystalExpression wrapper
        if (expr is CrystalExpression) {
            inferFromCrystalExpressionShape(expr)?.let { return it }
        }

        return inferFromBracedShape(text)
    }

    /** String, char and symbol literals (quote-prefixed shapes). */
    private fun inferQuotedLiteralType(text: String): String? =
        when {
            text.startsWith("\"") -> "String"
            text.startsWith("'") && text.endsWith("'") && text.length >= MIN_QUOTED_CHAR_LENGTH -> "Char"
            text.startsWith(":") && !text.startsWith("::") -> "Symbol"
            else -> null
        }

    /** Boolean and nil keyword literals. */
    private fun inferKeywordLiteralType(text: String): String? =
        when (text) {
            "true", "false" -> "Bool"
            "nil" -> "Nil"
            else -> null
        }

    /** Integer and float literals, including `_suffix` variants. */
    private fun inferNumericLiteralType(text: String): String? =
        when {
            text.contains("_f32") || text.contains("_f64") -> resolveFloatLiteralType(text)
            text.matches(Regex("""-?\d[\d_]*\.\d[\d_]*([eE][+-]?\d+)?""")) -> resolveFloatLiteralType(text)
            text.matches(Regex("""-?\d[\d_]*(?:_?[iu](?:8|16|32|64|128))?""")) -> resolveIntegerLiteralType(text)
            else -> null
        }

    /** Hash/tuple shapes from a `CrystalExpression` wrapper's literal lists. */
    private fun inferFromCrystalExpressionShape(expr: CrystalExpression): List<String>? {
        val hashLiterals = expr.hashLiteralList
        if (hashLiterals.isNotEmpty()) {
            val resolved = CrystalExpressionTypeResolver.resolveType(hashLiterals[0])
            if (resolved != null) return listOf(resolved.typeName)
            return listOf("Hash")
        }
        val tupleLiterals = expr.tupleLiteralList
        if (tupleLiterals.isNotEmpty()) {
            val resolved = CrystalExpressionTypeResolver.resolveType(tupleLiterals[0])
            if (resolved != null) return listOf(resolved.typeName)
            return listOf("Tuple")
        }
        return null
    }

    /** Fallback text-based detection for `{...}` hash/tuple shapes. */
    private fun inferFromBracedShape(text: String): List<String>? {
        if (!text.startsWith("{")) return null
        if (text.contains("=>") || text.contains(Regex("\\w+:"))) return listOf("Hash")
        return listOf("Tuple")
    }

    private fun resolveIntegerLiteralType(text: String): String {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("i8") -> "Int8"
            lower.endsWith("i16") -> "Int16"
            lower.endsWith("i32") -> "Int32"
            lower.endsWith("i64") -> "Int64"
            lower.endsWith("i128") -> "Int128"
            lower.endsWith("u8") -> "UInt8"
            lower.endsWith("u16") -> "UInt16"
            lower.endsWith("u32") -> "UInt32"
            lower.endsWith("u64") -> "UInt64"
            lower.endsWith("u128") -> "UInt128"
            else -> "Int32"
        }
    }

    private fun resolveFloatLiteralType(text: String): String {
        val lower = text.lowercase().replace("_", "")
        return when {
            lower.endsWith("f32") -> "Float32"
            lower.endsWith("f64") -> "Float64"
            else -> "Float64"
        }
    }
}
