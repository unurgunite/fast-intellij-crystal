package io.github.unurgunite.crystal.highlighting

import org.intellij.lang.regexp.RegExpLanguageHost
import org.intellij.lang.regexp.psi.RegExpChar
import org.intellij.lang.regexp.psi.RegExpElement
import org.intellij.lang.regexp.psi.RegExpGroup
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef

/**
 * Capability flags for Crystal's PCRE dialect (possessive quantifiers, named
 * groups, hex escapes...). Base of `CrystalRegExpLanguageHost` so neither class
 * exceeds the function budget.
 */
abstract class CrystalRegExpCapabilities : RegExpLanguageHost {
    override fun supportsPerl5EmbeddedComments(): Boolean = true

    override fun supportsPossessiveQuantifiers(element: RegExpElement): Boolean = true

    override fun supportsPythonConditionalRefs(): Boolean = false

    override fun supportsNamedGroupSyntax(group: RegExpGroup): Boolean = true

    override fun supportsNamedGroupRefSyntax(ref: RegExpNamedGroupRef): Boolean = true

    override fun supportsExtendedHexCharacter(ch: RegExpChar): Boolean = true

    override fun supportsLookbehind(group: RegExpGroup): RegExpLanguageHost.Lookbehind =
        RegExpLanguageHost.Lookbehind.FIXED_LENGTH_ALTERNATION
}

/**
 * RegExp language host for Crystal's PCRE-based regex dialect.
 *
 * Crystal uses the PCRE2 library for regex. This host tells IntelliJ's built-in
 * RegExp language which features the Crystal dialect supports so the platform
 * can provide syntax highlighting, inspections, and completion for regex literals.
 *
 * @see org.intellij.lang.regexp.RegExpLanguageHost
 */
class CrystalRegExpLanguageHost : CrystalRegExpCapabilities() {
    override fun getSupportedNamedGroupTypes(context: RegExpElement?): java.util.EnumSet<RegExpGroup.Type> =
        java.util.EnumSet.of(
            RegExpGroup.Type.NAMED_GROUP,
            RegExpGroup.Type.ATOMIC,
            RegExpGroup.Type.NON_CAPTURING,
            RegExpGroup.Type.POSITIVE_LOOKAHEAD,
            RegExpGroup.Type.NEGATIVE_LOOKAHEAD,
            RegExpGroup.Type.POSITIVE_LOOKBEHIND,
            RegExpGroup.Type.NEGATIVE_LOOKBEHIND,
        )

    override fun isValidCategory(category: String): Boolean = CrystalRegExpValidation.isValidCategory(category)

    override fun isValidPropertyName(name: String): Boolean = CrystalRegExpValidation.isValidPropertyName(name)

    override fun isValidPropertyValue(
        name: String,
        value: String,
    ): Boolean = CrystalRegExpValidation.isValidPropertyValue(name, value)

    override fun getAllKnownProperties(): Array<Array<String>> = CrystalRegExpProperties.allKnownProperties()

    override fun getPropertyDescription(name: String?): String? = CrystalRegExpProperties.propertyDescription(name)

    override fun getKnownCharacterClasses(): Array<Array<String>> = CrystalRegExpProperties.knownCharacterClasses()
}
