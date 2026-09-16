package io.github.unurgunite.crystal.psi.util

/**
 * String-shape helpers for Crystal names. Split out of [CrystalPsiUtils] (which
 * hit detekt's TooManyFunctions budget): tree-walking utilities stay there,
 * pure string transforms live here.
 */
object CrystalNameUtils {
    /**
     * Crystal's `String#underscore` (verified against 1.21.0: `DarkBlue` →
     * `dark_blue`, `IO` → `io`, `UInt128x` → `u_int128x`, `HTMLParser` →
     * `html_parser`). Used to derive an enum member's generated `member?`
     * predicate name from its CONSTANT (`DarkBlue` → `dark_blue?`).
     */
    fun crystalUnderscore(name: String): String = wordBoundaryRe.replace(acronymBoundaryRe.replace(name, "$1_$2"), "$1_$2").lowercase()
}

private val wordBoundaryRe = Regex("([a-z\\d])([A-Z])")
private val acronymBoundaryRe = Regex("([A-Z\\d]+)([A-Z][a-z])")
