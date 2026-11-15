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
        // Suffixed literals autocast too: `3i32` fits `Float32`, `300i32`
        // does not fit `Int8` — here only the flag decides, not the value.
        assertTrue(compat("Int32", "Int8", literal = true))
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

    fun testUnboundTypeVariablesFitAnything() {
        // `Hash#merge` infers `Hash(K, V)`: placeholders fit concrete args.
        assertTrue(compat("Hash(K, V)", "Hash(String, String)"))
        assertTrue(compat("Hash(String, String)", "Hash(K, V)"))
        assertTrue(compat("Array(T)", "Array(Int32)"))
        // Concrete mismatches still mismatch
        assertFalse(compat("Hash(String, Bool)", "Hash(String, String)"))
    }

    fun testStdlibMergeUnionPlaceholdersFit() {
        // stdlib `Hash#merge : Hash(K | L, V | W)`: all-placeholder unions
        // fit concrete params. Naive split("|") cut this into `Hash(K` —
        // depth-aware split keeps the member whole first.
        assertTrue(compat("Hash(K | L, V | W)", "Hash(String, String)"))
        assertFalse(compat("Hash(K | L, V | W)", "Array(Int32)"))
    }

    fun testBareGenericFitsParameterizedSameBase() {
        // `Hash` (args unknown, e.g. unresolved `merge` inference) vs
        // `Hash(String, String)`: same base, no definite mismatch.
        // Different bases still mismatch.
        assertTrue(compat("Hash", "Hash(String, String)"))
        assertTrue(compat("Hash(String, String)", "Hash"))
        assertTrue(compat("Array", "Array(Int32)"))
        assertFalse(compat("Hash", "Array(Int32)"))
        // Bare non-generics still escape as unknown (pre-existing leniency)
        assertTrue(compat("Array", "String"))
    }

    fun testLeadingDoubleColonIsVoid() {
        // `::Set(String)` == `Set(String)`: leading `::` is semantically void.
        assertTrue(compat("::Set(String)", "Set(String)"))
        assertTrue(compat("Set(String)", "::Set(String)"))
        assertTrue(compat("::Hash(String, String)", "Hash(String, String)"))
    }

    fun testBytesAliasFitsSlice() {
        // `Bytes` is `Slice(UInt8)`: slice reads fit Bytes params.
        assertTrue(compat("Slice(UInt8)", "Bytes"))
        assertTrue(compat("Bytes", "Slice(UInt8)"))
        assertFalse(compat("Slice(String)", "Bytes"))
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

    fun testJsonableCollectionsFitString() {
        // `.to_json` always yields String: `hash.to_json` resolves as the
        // `to_json` call (String), not the bare hash. Bare collections
        // without `.to_json` still mismatch (see the literal tests in
        // CrystalTypeCheckLiteralsTest).
        assertTrue(compat("String", "String"))
        assertFalse(compat("Hash(String, String)", "String"))
        assertFalse(compat("Array(Int32)", "String"))
        assertFalse(compat("Int32", "String"))
    }

    fun testUnionArgumentFitsWhenAnyMemberFits() {
        // Overload-union arguments (`String | Hash(String, String)`) fit a
        // concrete param when any member fits — the call may take any
        // branch at runtime.
        assertTrue(compat("String | Hash(String, String)", "Hash(String, String)"))
        assertTrue(compat("Nil | String", "String"))
        assertFalse(compat("Int32 | Bool", "String"))
    }

    fun testEnumerableParameterAcceptsCollections() {
        // `Enumerable(T)` is a non-invariant module: any collection fits it.
        assertTrue(compat("Array(String)", "Enumerable(String)"))
        assertTrue(compat("Tuple(String)", "Enumerable(String)"))
        assertTrue(compat("Array(Int32)", "Enumerable"))
        // Element types are not compared by Array's invariance rule.
        assertTrue(compat("Array(Int32)", "Enumerable(String)"))
        // Nilable module param matches a collection too.
        assertTrue(compat("Array(String)", "Enumerable?"))
    }

    fun testUnderscoreParameterAcceptsAnything() {
        // `value : _` is a type wildcard.
        assertTrue(compat("Array(String)", "_"))
        assertTrue(compat("Bool", "_"))
    }

    fun testArrayInvarianceStillChecked() {
        // Enumerable leniency must not leak into concrete invariant generics.
        assertFalse(compat("Array(Int32)", "Array(String)"))
        assertFalse(compat("Hash(String, String)", "Array(String)"))
    }

    fun testTupleSugarMatchesTupleType() {
        assertTrue(compat("Tuple(Int32, Int32)", "{Int32, Int32}"))
        assertTrue(compat("Tuple(Int32, Int32)", "::Tuple(Int32, Int32)"))
    }

    fun testTupleElementMismatchStillDetected() {
        assertFalse(compat("Tuple(Int32, String)", "{String, String}"))
    }

    fun testSelfTypeIsLenientOnBothSides() {
        assertTrue(compat("self", "Array(String)"))
        assertTrue(compat("Array(String)", "self"))
        assertTrue(compat("self", "Set(String)"))
    }

    fun testSelfTypeDoesNotHideRealMismatch() {
        assertFalse(compat("Int32", "String"))
    }

    fun testIndexableParameterAcceptsTuple() {
        // `Indexable(T)` is a module: Tuple, Array and StaticArray all satisfy it.
        assertTrue(compat("Tuple(String)", "Indexable(String)"))
        assertTrue(compat("Tuple(String, String)", "Indexable(String)"))
        assertTrue(compat("Tuple(String)", "Indexable"))
        assertTrue(compat("Tuple(String)", "Indexable(String)?"))
    }

    fun testIndexableLeniencyDoesNotHideRealMismatch() {
        assertFalse(compat("Int32", "String"))
        assertFalse(compat("Hash(String, String)", "Array(String)"))
    }

    fun testSelfInsideGenericArgumentIsLenient() {
        // `self` as a generic element, not a whole argument: the resolver does
        // not track it, so it is never a definite mismatch.
        assertTrue(compat("Array(self)", "Array(ACON::Command) | Array(String)"))
        assertTrue(compat("Array(self)", "AVD::Constraint"))
        assertTrue(compat("Hash(Int32, self)", "Hash"))
        assertTrue(compat("Hash(String, self)", "JSON::PullParser | Int"))
        assertTrue(compat("Array(self)", "String"))
        assertTrue(compat("Hash(String, self)", "Hash(String, Int32)"))
    }

    fun testSelfInsideGenericDoesNotHideRealMismatch() {
        assertFalse(compat("Array(Int32)", "Array(String)"))
        assertFalse(compat("Hash(String, Int32)", "Hash(String, Bool)"))
    }

    // A nilable argument only has to fit through its non-Nil member: the `?`
    // must not stay glued to the base and turn a fine call into a mismatch.
    fun testNilableArgumentFitsNonNullableParameter() {
        assertTrue(compat("{String, String}?", "{String, String}"))
        assertTrue(compat("Tuple(String, String)?", "Tuple(String, String)"))
        assertTrue(compat("String?", "String"))
    }

    fun testNilableArgumentStillRejectsRealMismatch() {
        assertFalse(compat("Int32?", "String"))
        assertFalse(compat("Tuple(String, Bool)?", "Tuple(String, String)"))
    }

    // An overload parameter written as a parenthesised union is just a union.
    fun testParenthesisedUnionParameterMatchesMember() {
        assertTrue(compat("String", "(Char | String)"))
        assertTrue(compat("Char", "(Char | String)"))
        assertTrue(compat("Array(Int32)", "(Array(Int32))"))
    }

    fun testParenthesisedUnionStillRejectsNonMember() {
        assertFalse(compat("Bool", "(Char | String)"))
    }

    // Parens only wrap when they close at the very end: a generic's own parens
    // must not be stripped into a bogus union.
    fun testGenericParensAreNotTreatedAsWrappingUnion() {
        assertTrue(compat("Array(Int32)", "Array(Int32)"))
        assertFalse(compat("Array(String)", "Array(Int32)"))
    }

    // A parameterised type satisfies the bare class it specialises, and keeping
    // the type argument must not turn it into a name mismatch either.
    fun testParameterisedTypeFitsItsBareClass() {
        assertTrue(compat("Box(Int32)", "Box"))
        assertTrue(compat("Metadata(Int32)", "Metadata"))
        assertTrue(compat("Metadata(TestEnum)", "Metadata"))
        // ...but the type argument still has to match the parameter's own.
        assertTrue(compat("Metadata(Int32)", "Metadata(Int32)"))
        assertFalse(compat("Metadata(String)", "Metadata(Int32)"))
    }

    // A namespace on one side only is cosmetic: `App::Metadata` and
    // `Metadata(Int32)` are the same class.
    fun testQualifiedAndShortNamesAreTheSameClass() {
        assertTrue(compat("Metadata(Int32)", "App::Metadata"))
        assertTrue(compat("App::Metadata(Int32)", "Metadata"))
        assertTrue(compat("App::Metadata(Int32)", "App::Metadata"))
        assertTrue(compat("App::Metadata(Int32)", "App::Metadata(Int32)"))
    }

    // Two differently-qualified generics with the same short name are NOT
    // treated as the same class: the base is compared by name, so the namespace
    // still distinguishes them. (Their unknown *element* types stay lenient on
    // purpose, so only the base is asserted here.)
    fun testDifferentNamespaceWithGenericArgumentsIsNotTheSameClass() {
        assertFalse(compat("Other::Metadata(Int32)", "App::Metadata(Int32)"))
        assertFalse(compat("Other::Metadata(Int32)", "App::Metadata(String)"))
    }

    // A generic type in a parameter must never be reported as a truncated
    // fragment of its own text (e.g. `Token)` out of `Array(Path::Token)`).
    fun testGenericTypeIsNeverTruncated() {
        assertTrue(compat("Array(Foo::Token)", "Array(Foo::Token)"))
        assertFalse(compat("Foo::Token)", "Array(Foo::Token)"))
    }

    // An empty typed array literal is that array, not `Array(Nil)`.
    fun testTypedEmptyArrayLiteralFits() {
        assertTrue(compat("Array(String)", "Array(String)", literal = true))
        assertTrue(compat("Array(Int32)", "Array(Int32)", literal = true))
        assertFalse(compat("Array(Int32)", "Array(String)", literal = true))
    }

    // An unclosed generic in the parameter text degrades to its base name
    // rather than crashing or rejecting a matching argument.
    fun testUnbalancedParenInParameterDoesNotCrash() {
        assertTrue(compat("Array(Int32)", "Array(Int32)"))
        assertTrue(compat("String", "String)"))
    }
}
