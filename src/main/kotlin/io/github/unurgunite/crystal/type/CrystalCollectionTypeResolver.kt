package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement
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
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver.ResolvedType
import io.github.unurgunite.crystal.type.CrystalExpressionTypeResolver.resolveType

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
        // Check for "of Type" annotation (possibly multi-type `[] of T1, T2, ...`).
        // NOTE: getTypeReference() is gone — the multi-type `[] of` tail made the
        // accessor a list; single `[] of T` still yields exactly one element.
        val typeRefs = expr.typeReferenceList
        if (typeRefs.isNotEmpty()) return ResolvedType("Array(${firstUnionMember(typeRefs.first().text)})")

        // Infer from elements
        val elements = expr.expressionList?.expressionList.orEmpty()
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

        val entries = expr.hashEntryList?.hashEntryList.orEmpty()
        if (entries.isEmpty()) return null

        val keyTypes = entries.mapNotNull { resolveKeyType(it, it.expressionList.getOrNull(0)) }
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

    /**
     * Hash key type: in the `key: value` sugar a bare identifier IS a symbol,
     * but in `key => value` it is an ordinary expression — `{key => ">"}`
     * (redis streaming_spec) hashes a String, not a Symbol.
     */
    private fun resolveKeyType(
        entry: PsiElement,
        keyExpr: PsiElement?,
    ): ResolvedType? {
        if (keyExpr == null) return null
        val isFatArrow = entry.node.findChildByType(CrystalTypes.DOUBLE_ARROW) != null
        val keyText = keyExpr.text.trim()
        if (!isFatArrow && keyText.matches(Regex("^[a-zA-Z_]\\w*[?!]?$"))) return ResolvedType("Symbol")
        return resolveType(keyExpr)
    }

    /**
     * Element type of `recv[index]` for a known [receiverType]: `Tuple(A, B)`
     * with a literal index picks that member; an `Array(T)`/`Enumerable(T)`
     * index yields `T`. Returns null when it cannot be decided, so the caller
     * keeps its own fallback instead of guessing.
     */
    fun indexedElementType(
        receiverType: String,
        indexText: String,
    ): ResolvedType? {
        if (receiverType.startsWith("Tuple(")) {
            val index = indexText.toIntOrNull() ?: return null
            val members = tupleMembers(receiverType)
            // Negative indexes count from the end, as in Crystal.
            val resolved = if (index < 0) members.getOrNull(members.size + index) else members.getOrNull(index)
            return resolved?.let { ResolvedType(it) }
        }
        val generic = receiverType.removeSuffix("?").substringBefore('(')
        if (generic !in INDEXABLE_GENERICS) return null
        return io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference
            .splitTypeNames(receiverType.removeSuffix("?").substringAfter('(', "").removeSuffix(")"))
            .firstOrNull()
            ?.trim()
            ?.let { ResolvedType(it) }
    }

    /** Containers whose `[]` yields their single element type. */
    private val INDEXABLE_GENERICS = setOf("Array", "Set", "Slice", "Deque", "Enumerable", "Iterator")

    /**
     * Members of `Tuple(A, B)`, split on top-level commas only — a member may
     * itself be generic (`Hash(String, Int32)`) or a union (`Int32 | Nil`).
     */
    private fun tupleMembers(tupleType: String): List<String> {
        val inner = tupleType.removePrefix("Tuple(").removeSuffix(")")
        val members = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (ch in inner) {
            when (ch) {
                '(', '[', '{' -> {
                    depth++
                    current.append(ch)
                }

                ')', ']', '}' -> {
                    depth = maxOf(0, depth - 1)
                    current.append(ch)
                }

                ',' -> {
                    if (depth == 0) {
                        members.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }

                else -> {
                    current.append(ch)
                }
            }
        }
        current
            .toString()
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { members.add(it) }
        return members
    }

    private fun resolveTupleLiteral(expr: CrystalTupleLiteral): ResolvedType? {
        val elements = expr.expressionList.expressionList
        if (elements.isEmpty()) return null

        val types = elements.mapNotNull { resolveType(it) }
        if (types.size != elements.size) return null

        val typeList = types.joinToString(", ") { it.typeName }
        return ResolvedType("Tuple($typeList)")
    }

    /** First union member of an annotation, generics kept (depth-aware split). */
    private fun firstUnionMember(annotation: String): String =
        io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference
            .splitTypeNames(annotation.trim())
            .firstOrNull()
            ?.trim() ?: annotation.trim()
}
