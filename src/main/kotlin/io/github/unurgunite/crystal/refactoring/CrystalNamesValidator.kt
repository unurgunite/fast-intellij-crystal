package io.github.unurgunite.crystal.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

class CrystalNamesValidator : NamesValidator {
    private val keywords =
        setOf(
            "abstract",
            "alias",
            "annotation",
            "as",
            "as?",
            "asm",
            "begin",
            "break",
            "case",
            "class",
            "def",
            "do",
            "else",
            "elsif",
            "end",
            "ensure",
            "enum",
            "extend",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "include",
            "instance_sizeof",
            "is_a?",
            "lib",
            "macro",
            "module",
            "next",
            "nil",
            "nil?",
            "of",
            "offsetof",
            "out",
            "pointerof",
            "private",
            "protected",
            "require",
            "rescue",
            "responds_to?",
            "return",
            "select",
            "self",
            "sizeof",
            "struct",
            "super",
            "then",
            "true",
            "type",
            "typeof",
            "uninitialized",
            "union",
            "unless",
            "until",
            "verbatim",
            "when",
            "while",
            "with",
            "yield",
        )

    override fun isKeyword(
        name: String,
        project: Project?,
    ): Boolean = name in keywords

    override fun isIdentifier(
        name: String,
        project: Project?,
    ): Boolean {
        if (name.isEmpty()) return false
        // Crystal identifiers: start with letter/underscore, contain alphanumeric/underscore
        // Constants: start with uppercase
        // Instance variables: start with @, followed by letter/underscore
        // Class variables: start with @@, followed by letter/underscore
        val offset = sigilLength(name)
        if (offset > 0) return isVariableBody(name, offset)
        return isPlainBody(stripMethodSuffix(name) ?: return false)
    }

    /** Length of the `@`/`@@` sigil prefix, or 0 for plain names. */
    private fun sigilLength(name: String): Int =
        when {
            name.startsWith("@@") -> 2
            name.startsWith("@") -> 1
            else -> 0
        }

    /**
     * Variable body after `@`/`@@`: must start with letter/underscore, then only
     * letters/digits/underscores — no `?`/`!`/`=` suffixes on variables.
     */
    private fun isVariableBody(
        name: String,
        offset: Int,
    ): Boolean {
        if (name.length <= offset) return false
        val first = name[offset]
        if (!first.isLetter() && first != '_') return false
        return name.substring(offset + 1).all { it.isLetterOrDigit() || it == '_' }
    }

    /**
     * Plain name without a method suffix (`?`/`!`/`=`), or null when the name is
     * just a suffix or contains interior markers.
     */
    private fun stripMethodSuffix(name: String): String? {
        // Method names may carry a single trailing ?, ! or = (predicate/bang/setter)
        var body = name
        val last = body.last()
        if (last == '?' || last == '!' || last == '=') {
            body = body.dropLast(1)
            if (body.isEmpty()) return null
        }
        if (body.any { it == '?' || it == '!' || it == '=' }) return null
        return body
    }

    private fun isPlainBody(body: String): Boolean {
        val first = body[0]
        if (!first.isLetter() && first != '_') return false
        return body.all { it.isLetterOrDigit() || it == '_' }
    }
}
