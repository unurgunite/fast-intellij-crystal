package io.github.unurgunite.crystal.type

import com.intellij.psi.PsiElement

/**
 * Return types of calls that do not depend on the receiver's own class body.
 *
 * Two shapes here come from the 1.2.2 triage:
 * - `Proc(A, B)#call` yields `B` — the last type parameter, not the Proc.
 *   The index has no `Proc#call` (it is defined on the struct), so the call
 *   fell back to the receiver's type and a `String` result read as a Proc.
 * - `size`/`count`/`length` yield `Int32` on every container, and `empty?`
 *   and friends yield `Bool`. `Enumerable#size` lives on a module, so a
 *   class-scoped lookup missed it and the receiver's `Enumerable(T)` leaked
 *   through.
 *
 * Anything not listed returns null: unknown stays unknown rather than
 * becoming a guess.
 */
internal object CrystalReceiverMemberTypes {
    private val INT32_MEMBERS = setOf("size", "count", "length")

    private val BOOL_MEMBERS = setOf("empty?", "any?", "all?", "none?", "includes?", "has_key?")

    private const val PROC_PREFIX = "Proc("

    fun resolve(
        methodName: String,
        receiver: PsiElement,
    ): String? {
        val receiverType = CrystalExpressionTypeResolver.resolveType(receiver)?.typeName ?: return null
        return when {
            methodName in INT32_MEMBERS -> "Int32".takeIf { isContainer(receiverType) }
            methodName in BOOL_MEMBERS -> "Bool".takeIf { isContainer(receiverType) }
            methodName == "call" && receiverType.startsWith(PROC_PREFIX) -> procReturnType(receiverType)
            else -> null
        }
    }

    /**
     * True when [typeName] names a container. A `File::Info` has `size : Int64`
     * and an `Int32#size` does not exist, so the fixed `Int32` claim is only
     * safe for the container families.
     */
    private fun isContainer(typeName: String): Boolean {
        val bare = typeName.removeSuffix("?").trim()
        if (bare.startsWith("Tuple(")) return true
        val head = bare.substringBefore('(').substringAfterLast("::")
        return head in CONTAINERS
    }

    /** `Proc(A, B)` → `B`: `#call` returns the block's last type parameter. */
    private fun procReturnType(procType: String): String? {
        val inner = procType.removePrefix(PROC_PREFIX).removeSuffix(")")
        val params =
            CrystalMethodReturnTypeInference
                .splitTypeNames(inner)
                .flatMap { splitTopLevelCommas(it) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return params.lastOrNull()
    }

    private fun splitTopLevelCommas(text: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in text) {
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
                        out.add(current.toString())
                        current.clear()
                    } else {
                        current.append(ch)
                    }
                }

                else -> {
                    current.append(ch)
                }
            }
        }
        current.toString().takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    private val CONTAINERS =
        setOf(
            "Array",
            "Set",
            "Slice",
            "Deque",
            "Hash",
            "NamedTuple",
            "Enumerable",
            "Iterator",
            "StaticArray",
            "String",
        )
}
