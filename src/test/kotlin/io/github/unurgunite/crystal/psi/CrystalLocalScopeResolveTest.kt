package io.github.unurgunite.crystal.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Direct tests for [CrystalLocalScopeResolve] — the file-local half of bare
 * reference resolution. Previously covered only indirectly through rename and
 * reference tests. These pin the scope-boundary contract: no file escape, no
 * method-boundary leak, block params resolve, unknown names return null.
 */
class CrystalLocalScopeResolveTest : BasePlatformTestCase() {
    private fun leafBeforeCaret(): com.intellij.psi.PsiElement {
        // `name<caret>` leaves the caret on the NEWLINE after the identifier;
        // the identifier itself is the leaf immediately before the offset.
        val off = myFixture.caretOffset
        val prev = myFixture.file.findElementAt((off - 1).coerceAtLeast(0))!!
        if (prev.text.isNotBlank()) return prev
        // Fallback: walk leaves backwards to the first non-whitespace token.
        var leaf: com.intellij.psi.PsiElement? = prev
        while (leaf != null && leaf.text.isBlank()) {
            leaf =
                com.intellij.psi.util.PsiTreeUtil
                    .prevLeaf(leaf)
        }
        return leaf ?: prev
    }

    private fun resolveAtCaret(
        code: String,
        name: String,
    ): com.intellij.psi.PsiElement? {
        myFixture.configureByText("test.cr", code)
        return CrystalLocalScopeResolve.resolveLocal(leafBeforeCaret(), name)
    }

    fun testLocalAssignmentResolves() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                x = 1
                puts x
                """.trimIndent(),
            )
        val leaf = file.findElementAt(file.text.indexOf("puts x") + 5)!!
        val resolved = CrystalLocalScopeResolve.resolveLocal(leaf, "x")
        assertNotNull("Local assignment should resolve", resolved)
    }

    fun testDoesNotCrossMethodBoundary() {
        val resolved =
            resolveAtCaret(
                """
                def first
                  x = 1
                end
                def second
                  puts x<caret>
                end
                """.trimIndent(),
                "x",
            )
        assertNull("Must not resolve across method boundaries", resolved)
    }

    fun testMethodParameterResolves() {
        val resolved =
            resolveAtCaret(
                """
                def foo(bar)
                  puts bar<caret>
                end
                """.trimIndent(),
                "bar",
            )
        assertNotNull("Method parameter should resolve", resolved)
    }

    fun testBlockParameterResolves() {
        val resolved =
            resolveAtCaret(
                """
                [1, 2].each do |ola|
                  puts ola<caret>
                end
                """.trimIndent(),
                "ola",
            )
        assertNotNull("Block parameter should resolve", resolved)
    }

    fun testUnknownNameReturnsNull() {
        val resolved =
            resolveAtCaret(
                """
                x = 1
                puts y<caret>
                """.trimIndent(),
                "y",
            )
        assertNull("Unknown name must return null", resolved)
    }
}
