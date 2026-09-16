package io.github.unurgunite.crystal.psi

/**
 * Symbol-table key registration for [CrystalStdlibTextScan]. Split out of the
 * scan file (which hit detekt's TooManyFunctions budget): line handlers stay
 * there, key emission lives here.
 */
internal fun addSymbol(
    symbols: MutableMap<String, SymbolLoc>,
    hasCanonical: MutableSet<String>,
    relPath: String,
    offset: Int,
    name: String,
    qualified: String,
    isType: Boolean,
) {
    val loc = SymbolLoc(relPath, offset)
    symbols.putIfAbsent(qualified, loc)
    // Canonical: file base name matches the symbol (e.g. String -> string.cr), so
    // Ctrl+Click lands on the primary definition, not an arbitrary reopening.
    val canonical =
        isType &&
            java.io
                .File(relPath)
                .nameWithoutExtension
                .equals(name, ignoreCase = true)
    if (!symbols.containsKey(name) || (canonical && !hasCanonical.contains(name))) {
        symbols[name] = loc
        if (canonical) hasCanonical.add(name)
    }
}

// Register a `Class#method` (and bare `method` for top-level defs) symbol-table entry.
// Used by both `def`/`macro` lines and expanded `getter`/`setter`/`property` macros.
internal fun addMethodSymbol(
    symbols: MutableMap<String, SymbolLoc>,
    relPath: String,
    offset: Int,
    ns: String?,
    mname: String,
) {
    val loc = SymbolLoc(relPath, offset)
    val key = if (ns != null) "$ns#$mname" else mname
    if (ns == null) symbols.putIfAbsent(mname, loc)
    symbols.putIfAbsent(key, loc)
}

/**
 * True when the innermost open frame is a type body itself (not a method,
 * block, or anything nested below it). Shared by handleFieldLine (locals
 * inside methods are not fields) and handleEnumLine (defs inside enums are
 * methods, not members).
 */
internal fun directlyInType(state: ScanState): Boolean {
    if (state.openKinds.isEmpty()) return false
    val depthBelowType =
        state.openKinds.size -
            (state.openKinds.indexOfLast { it == "type" } + 1)
    return depthBelowType == 0
}
