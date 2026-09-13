package io.github.unurgunite.crystal.inspections

import com.intellij.psi.PsiElement
import io.github.unurgunite.crystal.inspections.CrystalExpressionTypeResolver.ResolvedType
import io.github.unurgunite.crystal.inspections.CrystalExpressionTypeResolver.resolveType
import io.github.unurgunite.crystal.psi.CrystalArrayLiteral
import io.github.unurgunite.crystal.psi.CrystalCommandExpression
import io.github.unurgunite.crystal.psi.CrystalHashLiteral
import io.github.unurgunite.crystal.psi.CrystalHeredocLiteral
import io.github.unurgunite.crystal.psi.CrystalInstanceSizeofExpression
import io.github.unurgunite.crystal.psi.CrystalOffsetofExpression
import io.github.unurgunite.crystal.psi.CrystalRegexExpression
import io.github.unurgunite.crystal.psi.CrystalSizeofExpression
import io.github.unurgunite.crystal.psi.CrystalSymbolStringExpression
import io.github.unurgunite.crystal.psi.CrystalTupleLiteral

/**
 * Fixed-shape and collection-literal types: regex/heredoc/sizeof have a type
 * regardless of content; array/hash/tuple types come from annotations or elements.
 */
object CrystalCollectionTypeResolver {
    /** Composites with a fixed type regardless of content, plus collection literals. */
    fun resolveCompositeLiteral(expr: PsiElement): ResolvedType? =
        when (expr) {
            is CrystalRegexExpression -> ResolvedType("Regex")
            is CrystalCommandExpression -> ResolvedType("String")
            is CrystalHeredocLiteral -> ResolvedType("String")
            is CrystalSymbolStringExpression -> ResolvedType("Symbol")
            is CrystalSizeofExpression -> ResolvedType("Int32")
            is CrystalInstanceSizeofExpression -> ResolvedType("Int32")
            is CrystalOffsetofExpression -> ResolvedType("Int32")
            is CrystalArrayLiteral -> resolveArrayLiteral(expr)
            is CrystalHashLiteral -> resolveHashLiteral(expr)
            is CrystalTupleLiteral -> resolveTupleLiteral(expr)
            else -> null
        }

    private fun resolveArrayLiteral(expr: CrystalArrayLiteral): ResolvedType? {
        // Check for "of Type" annotation
        val typeRef = expr.typeReference
        if (typeRef != null) return ResolvedType("Array(${firstUnionMember(typeRef.text)})")

        // Infer from elements
        val elements = expr.expressionList?.expressionList ?: emptyList()
        if (elements.isEmpty()) return null

        val elementTypes = elements.mapNotNull { resolveType(it) }
        if (elementTypes.size != elements.size) return null

        val firstType = elementTypes.first().typeName
        return if (elementTypes.all { it.typeName == firstType }) {
            ResolvedType("Array($firstType)")
        } else {
            val union = elementTypes.distinctBy { it.typeName }.joinToString(" | ") { it.typeName }
            ResolvedType("Array($union)")
        }
    }

    private fun resolveHashLiteral(expr: CrystalHashLiteral): ResolvedType? {
        val typeRefs = expr.typeReferenceList
        if (typeRefs.size >= 2) {
            val keyType = firstUnionMember(typeRefs[0].text)
            val valueType = firstUnionMember(typeRefs[1].text)
            return ResolvedType("Hash($keyType, $valueType)")
        }

        val entries = expr.hashEntryList?.hashEntryList ?: emptyList()
        if (entries.isEmpty()) return null

        val keyTypes = entries.mapNotNull { resolveKeyType(it.expressionList.getOrNull(0)) }
        val valueTypes = entries.mapNotNull { it.expressionList.getOrNull(1)?.let { e -> resolveType(e) } }
        if (keyTypes.size != entries.size || valueTypes.size != entries.size) return null

        val keyType = keyTypes.first().typeName
        val valueType = valueTypes.first().typeName
        return if (keyTypes.all { it.typeName == keyType } && valueTypes.all { it.typeName == valueType }) {
            ResolvedType("Hash($keyType, $valueType)")
        } else {
            val keyUnion = keyTypes.joinToString(" | ") { it.typeName }
            val valueUnion = valueTypes.joinToString(" | ") { it.typeName }
            ResolvedType("Hash($keyUnion, $valueUnion)")
        }
    }

    /** Hash key type: bare identifiers are symbols, anything else resolves normally. */
    private fun resolveKeyType(keyExpr: PsiElement?): ResolvedType? {
        if (keyExpr == null) return null
        val keyText = keyExpr.text.trim()
        if (keyText.matches(Regex("^[a-zA-Z_]\\w*[?!]?$"))) return ResolvedType("Symbol")
        return resolveType(keyExpr)
    }

    private fun resolveTupleLiteral(expr: CrystalTupleLiteral): ResolvedType? {
        val elements = expr.expressionList.expressionList
        if (elements.isEmpty()) return null

        val types = elements.mapNotNull { resolveType(it) }
        if (types.size != elements.size) return null

        val typeList = types.joinToString(", ") { it.typeName }
        return ResolvedType("Tuple($typeList)")
    }

    /** First union member of an annotation, with generic arguments stripped. */
    private fun firstUnionMember(annotation: String): String =
        annotation
            .trim()
            .split("|")
            .first()
            .trim()
            .replace(Regex("""\(.*\)"""), "")
            .trim()
}
