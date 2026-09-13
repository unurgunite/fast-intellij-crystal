package io.github.unurgunite.crystal.refactoring

import junit.framework.TestCase

class CrystalNamesValidatorTest : TestCase() {

    private val validator = CrystalNamesValidator()

    fun testKeywords() {
        for (keyword in listOf("def", "class", "module", "if", "end", "nil", "true", "self", "lib", "fun", "macro")) {
            assertTrue("'$keyword' should be a keyword", validator.isKeyword(keyword, null))
        }
    }

    fun testNonKeywords() {
        for (name in listOf("foo", "bar?", "Foo", "@ivar", "my_method", "")) {
            assertFalse("'$name' should not be a keyword", validator.isKeyword(name, null))
        }
    }

    fun testPlainIdentifiers() {
        for (name in listOf("foo", "_foo", "foo1", "Foo", "FOO", "is_a?", "responds_to?", "empty?", "save!", "name=")) {
            assertTrue("'$name' should be an identifier", validator.isIdentifier(name, null))
        }
    }

    fun testInvalidIdentifiers() {
        for (name in listOf("", "1foo", "foo bar", "foo-bar", "?", "!", "=", "foo??", "foo?!", "a?b", "a=b", "@", "@@", "@1", "@foo?", "@@foo!")) {
            assertFalse("'$name' should not be an identifier", validator.isIdentifier(name, null))
        }
    }

    fun testInstanceAndClassVariables() {
        for (name in listOf("@foo", "@_foo", "@foo1", "@Foo", "@@foo", "@@_bar1")) {
            assertTrue("'$name' should be an identifier", validator.isIdentifier(name, null))
        }
    }
}
