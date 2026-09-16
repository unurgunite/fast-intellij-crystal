package io.github.unurgunite.crystal.inspections

import io.github.unurgunite.crystal.type.CrystalTypeCompatibility
import junit.framework.TestCase

class CrystalTypeCompatibilityTest : TestCase() {
    private fun compat(
        arg: String,
        param: String,
        literal: Boolean = false,
    ) = CrystalTypeCompatibility.isCompatible(arg, param, literal)

    fun testExactMatch() {
        assertTrue(compat("Int32", "Int32"))
        assertTrue(compat("String", "String"))
        assertTrue(compat("Foo", "Foo"))
    }

    fun testDefiniteMismatch() {
        assertFalse(compat("String", "Int32"))
        assertFalse(compat("Int32", "String"))
        assertFalse(compat("Bool", "Char"))
    }

    fun testNilable() {
        assertTrue(compat("Nil", "Int32?"))
        assertTrue(compat("Int32", "Int32?"))
        assertFalse(compat("String", "Int32?"))
        assertEquals("Int32 | Nil", CrystalTypeCompatibility.describeExpectedType("Int32?"))
    }

    fun testUnions() {
        assertTrue(compat("Int32", "Int32 | Nil"))
        assertTrue(compat("Nil", "Int32 | Nil"))
        assertTrue(compat("String", "Int32 | String"))
        assertFalse(compat("Bool", "Int32 | String"))
    }

    fun testTypeAliasesExpandBeforeCheck() {
        // `Int`/`UInt`/`Float` are unions in `normalizeType` and must expand,
        // never escape as unknown (see `CrystalTypeCompatibility`).
        assertTrue(compat("Int8", "Int"))
        assertTrue(compat("Int128", "Int"))
        // Lossless widening is deliberately allowed (UInt8 fits Int16 member):
        // the inspection prefers false negatives over false positives here.
        assertTrue(compat("UInt8", "Int"))
        assertFalse(compat("String", "Int"))
        assertTrue(compat("UInt64", "UInt"))
        assertTrue(compat("Float32", "Float"))
        assertTrue(compat("Float64", "Float"))
        assertFalse(compat("Int32", "Float"))
        assertTrue(compat("Int32", "Number"))
        assertTrue(compat("Float64", "Number"))
        assertTrue(compat("Nil", "Int?"))
    }

    fun testUnsuffixedLiteralAutocast() {
        assertTrue(compat("Int32", "Float64", literal = true))
        assertTrue(compat("Int32", "Int8", literal = true))
        assertFalse(compat("Int32", "String", literal = true))
        // Suffixed values do NOT get the literal treatment
        assertFalse(compat("Int32", "Int8", literal = false))
    }

    fun testLosslessWidening() {
        assertTrue(compat("Int8", "Int32"))
        assertTrue(compat("Int32", "Int128"))
        assertTrue(compat("UInt8", "Int32"))
        assertTrue(compat("Float32", "Float64"))
        // Narrowing and Int->Float lose precision → incompatible
        assertFalse(compat("Int64", "Int32"))
        assertFalse(compat("Int32", "Float64"))
        assertFalse(compat("Float64", "Float32"))
    }

    fun testGenerics() {
        assertTrue(compat("Array(Int32)", "Array(Int32)"))
        assertTrue(compat("Hash(String, Int32)", "Hash(String, Int32)"))
        assertFalse(compat("Array(String)", "Array(Int32)"))
        assertFalse(compat("Array(Int32)", "Hash(String, Int32)"))
        assertFalse(compat("Array(Int32)", "String"))
        assertFalse(compat("String", "Array(Int32)"))
        // Nested generics respect depth
        assertTrue(compat("Array(Array(Int32))", "Array(Array(Int32))"))
        assertFalse(compat("Array(Array(String))", "Array(Array(Int32))"))
        // Covariant-ish inner check via the same rules
        assertTrue(compat("Array(Int8)", "Array(Int32)"))
    }

    fun testUnknownTypesEscape() {
        // Unknown param or arg types must not produce false positives
        assertTrue(compat("MyClass", "Int32"))
        assertTrue(compat("Int32", "MyClass"))
        assertTrue(compat("Foo", "Bar"))
    }

    fun testDescribeExpectedType() {
        assertEquals("Int32", CrystalTypeCompatibility.describeExpectedType("Int32"))
        assertEquals("Int32 | String", CrystalTypeCompatibility.describeExpectedType("Int32 | String"))
    }
}
