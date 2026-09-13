package io.github.unurgunite.crystal.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.psi.CrystalTypes
import junit.framework.TestCase

class CrystalSingleQuoteStringInspectionTest : BasePlatformTestCase() {

    private fun highlights(code: String): List<String> {
        myFixture.configureByText("test.cr", code)
        myFixture.enableInspections(CrystalSingleQuoteStringInspection::class.java)
        return myFixture.doHighlighting().filter { it.description != null }.map { it.description!! }
    }

    fun testValidCharLiteralIsClean() {
        assertTrue(highlights("x = 'a'\n").isEmpty())
    }

    fun testMultiCharSingleQuotesAreFlagged() {
        val problems = highlights("x = 'ab'\n")
        assertTrue("Expected single-quote problem, got: $problems", problems.any { "single quotes" in it })
    }

    fun testDoubleQuotedStringIsClean() {
        assertTrue(highlights("x = \"hello\"\n").isEmpty())
    }
}

class CrystalSyntaxHighlighterTest : TestCase() {

    private val highlighter = CrystalSyntaxHighlighter()

    private fun keys(type: com.intellij.psi.tree.IElementType) =
        highlighter.getTokenHighlights(type).toList()

    fun testCoreMappings() {
        assertEquals(listOf(CrystalSyntaxHighlighter.KEYWORD), keys(CrystalTypes.DEF))
        assertEquals(listOf(CrystalSyntaxHighlighter.KEYWORD), keys(CrystalTypes.CLASS))
        assertEquals(listOf(CrystalSyntaxHighlighter.STRING), keys(CrystalTypes.STRING_LITERAL))
        assertEquals(listOf(CrystalSyntaxHighlighter.COMMENT), keys(CrystalTypes.LINE_COMMENT))
        assertEquals(listOf(CrystalSyntaxHighlighter.SYMBOL), keys(CrystalTypes.SYMBOL_LITERAL))
        assertEquals(listOf(CrystalSyntaxHighlighter.REGEX), keys(CrystalTypes.REGEX_LITERAL))
        assertEquals(listOf(CrystalSyntaxHighlighter.CHAR), keys(CrystalTypes.CHAR_LITERAL))
        assertEquals(listOf(CrystalSyntaxHighlighter.INSTANCE_VAR), keys(CrystalTypes.INSTANCE_VAR))
        assertEquals(listOf(CrystalSyntaxHighlighter.CLASS_VAR), keys(CrystalTypes.CLASS_VAR))
        assertEquals(listOf(CrystalSyntaxHighlighter.COMMA), keys(CrystalTypes.COMMA))
    }

    fun testPlainIdentifiersAndConstantsAreEmpty() {
        // Semantic coloring comes from the Annotator, not the lexer highlighter
        assertTrue(keys(CrystalTypes.IDENTIFIER).isEmpty())
        assertTrue(keys(CrystalTypes.CONSTANT).isEmpty())
    }

    fun testInterpolationAndMacroDelimiters() {
        assertEquals(
            listOf(CrystalSyntaxHighlighter.INTERPOLATION),
            keys(CrystalTypes.STRING_INTERPOLATION_BEGIN)
        )
        assertEquals(
            listOf(CrystalSyntaxHighlighter.INTERPOLATION),
            keys(CrystalTypes.MACRO_CONTROL_BEGIN)
        )
    }
}

class CrystalRegExpLanguageHostTest : BasePlatformTestCase() {

    private val host = CrystalRegExpLanguageHost()

    private fun firstElementOf(pattern: String): org.intellij.lang.regexp.psi.RegExpElement {
        val file = com.intellij.psi.PsiFileFactory.getInstance(project)
            .createFileFromText(
                org.intellij.lang.regexp.RegExpLanguage.INSTANCE,
                pattern
            )
        val found: org.intellij.lang.regexp.psi.RegExpElement? =
            com.intellij.psi.util.PsiTreeUtil.findChildOfType(
                file, org.intellij.lang.regexp.psi.RegExpElement::class.java
            )
        assertNotNull("No RegExp element parsed from '$pattern'", found)
        return found!!
    }

    fun testPossessiveQuantifiersSupported() {
        val element = firstElementOf("a++")
        assertTrue(host.supportsPossessiveQuantifiers(element))
    }

    fun testPerlEmbeddedCommentsSupported() {
        assertTrue(host.supportsPerl5EmbeddedComments())
    }

    fun testPythonConditionalRefsNotSupported() {
        assertFalse(host.supportsPythonConditionalRefs())
    }

    fun testNamedGroupSyntaxSupported() {
        val group: org.intellij.lang.regexp.psi.RegExpGroup? =
            com.intellij.psi.util.PsiTreeUtil.findChildOfType(
                com.intellij.psi.PsiFileFactory.getInstance(project).createFileFromText(
                    org.intellij.lang.regexp.RegExpLanguage.INSTANCE,
                    "(?<year>\\d+)"
                ),
                org.intellij.lang.regexp.psi.RegExpGroup::class.java
            )
        assertNotNull("No group parsed", group)
        assertTrue(host.supportsNamedGroupSyntax(group!!))
    }
}
