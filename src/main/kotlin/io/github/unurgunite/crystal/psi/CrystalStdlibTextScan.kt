package io.github.unurgunite.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import java.util.ArrayDeque

/**
 * Bounded (stdlib-only) text scan, used as a fallback for bare lowercase method names
 * and constants that have no home-file convention. Built lazily once per project.
 * Stores stable (relPath, offset) locations — never PsiElements — so it can never go
 * stale and never triggers a multi-second reparse of the whole stdlib. A text scan of
 * ~2154 files runs in well under a second, versus the 70s the old PSI-walk required.
 *
 * Split out of `CrystalReference` (whose companion exceeded the function budget).
 */
internal object CrystalStdlibTextScan {
    /** Bounded stdlib text-symbol table (name → stable [SymbolLoc]). */
    internal data class StdlibData(
        val symbols: Map<String, SymbolLoc>,
    )

    private val globalStdlibCaches = java.util.concurrent.ConcurrentHashMap<Project, Pair<VirtualFile, StdlibData>>()

    fun globalStdlibData(
        project: Project,
        root: VirtualFile,
    ): StdlibData {
        globalStdlibCaches[project]?.let { (cachedRoot, cached) ->
            if (cachedRoot == root) return cached
        }
        val data = buildStdlibData(root)
        globalStdlibCaches[project] = root to data
        return data
    }

    private fun buildStdlibData(root: VirtualFile): StdlibData {
        val symbols = HashMap<String, SymbolLoc>()
        // Names whose canonical-file definition (file base name == symbol name) is
        // already stored. Lets us upgrade a first-seen arbitrary definition to the
        // canonical one when we later encounter it during the VFS walk.
        val hasCanonical = HashSet<String>()
        // Bare names currently held by a `fun` declaration (see handleFunLine).
        // A later `def`/`macro` with the same bare name upgrades the key —
        // method definitions beat C bindings regardless of VFS walk order
        // (e.g. `sleep`: concurrent.cr `def` vs wasm32-wasi `fun`).
        val funBareKeys = HashSet<String>()
        VfsUtilCore.visitChildrenRecursively(
            root,
            object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) return true
                    if (file.extension != "cr") return true
                    val relPath =
                        VfsUtilCore
                            .getRelativePath(file, root) ?: return true
                    scanFileText(file, relPath, symbols, hasCanonical, funBareKeys)
                    return true
                }
            },
        )
        return StdlibData(symbols)
    }

    /**
     * Text-based symbol discovery for a single stdlib file. Finds top-level
     * `alias`, `class`/`struct`/`module`/`enum`/`lib`/`annotation`, SCREAMING_SNAKE constant,
     * and `def`/`macro` definitions by regex over the raw text, tracking the enclosing
     * namespace via a stack so namespaced keys (`Foo::Bar`, `Foo#baz`) are produced. This
     * covers symbols the grammar failed to parse into nodes because of an unrelated parse
     * error elsewhere in the same file — without ever parsing PSI.
     */
    fun scanFileText(
        file: VirtualFile,
        relPath: String,
        symbols: MutableMap<String, SymbolLoc>,
        hasCanonical: MutableSet<String>,
        funBareKeys: MutableSet<String> = HashSet(),
    ) {
        val text =
            try {
                String(file.contentsToByteArray(), Charsets.UTF_8)
            } catch (_: Throwable) {
                return
            }
        scanText(relPath, text, symbols, hasCanonical, funBareKeys)
    }

    /** Pure text scan (no VFS) — unit-testable without the IDE. */
    fun scanText(
        relPath: String,
        text: String,
        symbols: MutableMap<String, SymbolLoc>,
        hasCanonical: MutableSet<String>,
        funBareKeys: MutableSet<String> = HashSet(),
    ) {
        val state = ScanState(relPath, symbols, hasCanonical, funBareKeys)
        var pos = 0
        for (raw in text.lines()) {
            val lineStart = pos
            pos += raw.length + 1 // +1 for the newline separator
            if (!handleLine(raw, lineStart, state)) {
                balanceOtherLine(raw, state)
            }
        }
    }

    /** One line: type/alias/const/def/fun/macro-generator/field shapes first, generic balancing last. */
    private fun handleLine(
        raw: String,
        lineStart: Int,
        state: ScanState,
    ): Boolean =
        handleTypeLine(raw, lineStart, state) ||
            handleAliasLine(raw, lineStart, state) ||
            handleConstLine(raw, lineStart, state) ||
            handleDefLine(raw, lineStart, state) ||
            handleFunLine(raw, lineStart, state) ||
            handleGenLine(raw, lineStart, state) ||
            handleEnumLine(raw, lineStart, state) ||
            handleFieldLine(raw, lineStart, state)
}

/** Mutable per-file scan state: namespace stack plus block-kind stack. */
internal class ScanState(
    val relPath: String,
    val symbols: MutableMap<String, SymbolLoc>,
    val hasCanonical: MutableSet<String>,
    // Bare names claimed by a `fun` declaration (cross-file set, see
    // buildStdlibData). Lets a later `def`/`macro` upgrade the bare key.
    val funBareKeys: MutableSet<String>,
) {
    // Qualified-namespace stack: each entry is the FULL qualified name of the enclosing
    // type (e.g. "File", "File::Info"), so a nested definition keys as
    // "<qualified>::<name>" and a nested member as "<qualified>#<method>".
    val stack = ArrayDeque<String>()

    // Parallel stack tracking WHY each frame was opened: "type" for a class/struct/
    // module/enum/lib/annotation, "other" for a method/block (`def`, `if`, `do`, `{`…).
    // Only a "type" close pops the NAMESPACE stack — a method body's `end` must NOT
    // pop the enclosing class, or every method after the first would lose its namespace
    // (this previously dropped `String#upcase`, `Array#size`, … from the symbol table).
    val openKinds = ArrayDeque<String>()

    // Kind of each open type frame ("class", "enum", …), parallel to [stack]:
    // pushed together with a "type" frame, popped together with it. Lets
    // handlers tell an `enum` body apart (enum-member predicates) without
    // re-parsing the header line.
    val typeKinds = ArrayDeque<String>()

    fun popOpen() {
        if (openKinds.isNotEmpty()) {
            val k = openKinds.removeLast()
            if (k == "type" && stack.isNotEmpty()) {
                stack.removeLast()
                if (typeKinds.isNotEmpty()) typeKinds.removeLast()
            }
        }
    }

    /**
     * A `}` closes only block frames, never a type body (Crystal types close
     * with `end`). A lone `}` atop a type frame belongs to a multi-line
     * literal whose `{` sat on a handler-claimed line that skips balancing
     * (e.g. `SPECIAL_CHARACTERS = {` in regex.cr) — popping here used to
     * destroy the enclosing namespace, so `enum Options` lost its `Regex::`
     * prefix and every later `Regex` method lost its namespace. Now ignored.
     */
    fun popBrace() {
        if (openKinds.lastOrNull() == "other") openKinds.removeLast()
    }
}

/** Type / annotation / lib definition — opens a NAMESPACE frame. */
private fun handleTypeLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = typeRe.find(raw) ?: return false
    val kind = m.groupValues[1]
    val full = m.groupValues[2]
    val name = full.substringAfterLast("::")
    // Offset of the (last-segment) name identifier: group 2's start — NOT the
    // whole-match start (which is the leading whitespace) — plus the prefix length.
    val g1 = m.groups[2]!!
    val offset = lineStart + g1.range.first + (full.length - name.length)
    val enclosing = state.stack.lastOrNull()
    val qualified = if (enclosing != null) "$enclosing::$full" else full
    addSymbol(state.symbols, state.hasCanonical, state.relPath, offset, name, qualified, isType = true)
    state.stack.addLast(qualified)
    state.openKinds.addLast("type")
    state.typeKinds.addLast(kind)
    // Same-line one-liner (`class Error < Exception; end`, 5x in the stdlib)
    // closes the just-pushed frame immediately so it nets to zero.
    if (sameLineEndRe.containsMatchIn(raw)) state.popOpen()
    return true
}

/** alias Name (= ...). */
private fun handleAliasLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = aliasRe.find(raw) ?: return false
    val full = m.groupValues[1]
    val name = full.substringAfterLast("::")
    val g1 = m.groups[1]!!
    val offset = lineStart + g1.range.first + (full.length - name.length)
    val enclosing = state.stack.lastOrNull()
    val qualified = if (enclosing != null) "$enclosing::$name" else name
    addSymbol(state.symbols, state.hasCanonical, state.relPath, offset, name, qualified, isType = false)
    return true
}

/** SCREAMING_SNAKE constant assignment. */
private fun handleConstLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = constRe.find(raw) ?: return false
    val name = m.groupValues[1]
    val g1 = m.groups[1]!!
    val offset = lineStart + g1.range.first
    val enclosing = state.stack.lastOrNull()
    val qualified = if (enclosing != null) "$enclosing::$name" else name
    addSymbol(state.symbols, state.hasCanonical, state.relPath, offset, name, qualified, isType = false)
    // ALL-CAPS enum members (`MAX = 0`) are real constants AND get a generated
    // `member?` predicate — index both keys (CamelCase members are handled by
    // handleEnumLine below; constRe never matches them).
    if (enclosing != null && state.typeKinds.lastOrNull() == "enum" && directlyInType(state)) {
        state.symbols.putIfAbsent(
            "$enclosing#${CrystalNameUtils.crystalUnderscore(name)}?",
            SymbolLoc(state.relPath, offset),
        )
    }
    return true
}

/** def / macro method definitions (column-0 or indented). */
private fun handleDefLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = defRe.find(raw) ?: return false
    val sig = m.groupValues[1]
    val (recv, mname) = parseDefSig(sig)
    val g1 = m.groups[1]!!
    val offset = lineStart + g1.range.first + (sig.length - mname.length)
    val ns = recv ?: state.stack.lastOrNull()
    // For bare top-level builtins (no enclosing namespace) force the simple key
    // so e.g. `raise` jumps to raise.cr, not a private `def raise` elsewhere.
    addMethodSymbol(state.symbols, state.relPath, offset, ns, mname)
    // A `def`/`macro` beats a same-named `fun` for the bare key regardless of
    // VFS walk order (see handleFunLine) — native code shadows C bindings.
    if (ns == null && state.funBareKeys.remove(mname)) {
        state.symbols[mname] = SymbolLoc(state.relPath, offset)
    }
    // A method body owns its own lone-`end`; push an "other" frame so the
    // enclosing type's namespace frame survives it. A same-line one-liner
    // (`def foo; end`) closes the just-pushed frame immediately.
    // Bodiless `abstract def` owns NO close — pushing a frame here would eat
    // the next `end` (the enclosing type's or the next def's) and drift the
    // namespace stack for the rest of the file.
    if (!abstractDefRe.containsMatchIn(raw)) {
        state.openKinds.addLast("other")
        if (sameLineEndRe.containsMatchIn(raw)) state.popOpen()
    }
    return true
}

/**
 * `fun` C-binding declarations (inside `lib`, or top-level like `fun main`).
 * Indexed under the enclosing lib (`LibC#strlen` for DOT-call receivers) and
 * as a bare name (first-wins) for bare calls — several libc functions
 * (`exit`, `read`, `malloc`, …) are called bare in user code. An aliased
 * declaration (`fun foo = bar`) indexes the alias `foo`, not the C name.
 * `fun` inside `lib` is a bodiless declaration (no `end`); a top-level `fun`
 * defines a function with a body, so only it owns a balance frame.
 * Accepted imprecision: multi-line signatures (59 in the stdlib, mostly
 * Windows lib_c) may index a param line as `Lib#param` — harmless, since
 * nothing ever calls those names (bare keys are first-wins anyway).
 */
private fun handleFunLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = funRe.find(raw) ?: return false
    val name = m.groupValues[1]
    val g1 = m.groups[1]!!
    val offset = lineStart + g1.range.first
    val loc = SymbolLoc(state.relPath, offset)
    val ns = state.stack.lastOrNull()
    if (ns != null) {
        state.symbols.putIfAbsent("$ns#$name", loc)
    }
    // Bare key: a `def`/`macro` that already claimed (or later claims) the
    // name wins — see the upgrade in handleDefLine.
    if (state.symbols.putIfAbsent(name, loc) == null) {
        state.funBareKeys.add(name)
    }
    if (ns == null) {
        // Top-level `fun` defines a body (own lone-`end`); `fun` inside `lib`
        // is a bodiless declaration. Same-line one-liners net to zero.
        state.openKinds.addLast("other")
        if (sameLineEndRe.containsMatchIn(raw)) state.popOpen()
    }
    return true
}

/**
 * `getter` / `setter` / `property` (and `?`/`!`/`class_*` variants) macros
 * generate method definitions. Exact matrix from object/properties.cr
 * (verified against crystal 1.21.0):
 * - `getter` → reader `nm`; `setter` → writer `nm=`; `property` → both.
 * - `?` variants name ONLY the predicate reader (`getter? exclusive` →
 *   `exclusive?`, no bare `exclusive`); `property?` adds the writer
 *   (`wants_doc?` + `wants_doc=`).
 * - `!` variants (no `setter!` exists) name predicate + bare + (`property!`)
 *   writer (`property! resolved_type` → `resolved_type?`, `resolved_type`,
 *   `resolved_type=`).
 * `class_*` variants behave the same (static-ness needs no separate key:
 * lookups are `Recv#name` either way). Each generated key is first-wins so
 * an explicit `def wants_doc=` coexists with the macro writer. The offset
 * points at the generated name token so materialize lands on the identifier.
 */
private fun handleGenLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = genRe.find(raw) ?: return false
    val kind = m.groupValues[1]
    val base = kind.removePrefix("class_").removeSuffix("?").removeSuffix("!")
    val suffix = kind.removePrefix("class_").removePrefix(base)
    val names =
        m.groupValues[2].split(',').mapNotNull { tok ->
            Regex("""\s*([a-zA-Z_]\w*[!?]?)""").find(tok)?.groupValues?.get(1)
        }
    val ns = state.stack.lastOrNull()
    for (nm in names) {
        val idx = raw.indexOf(nm)
        val off = if (idx >= 0) lineStart + idx else lineStart
        addGenKeys(state, off, ns, base, suffix, nm)
    }
    return true
}

/** Generated keys for one `getter`/`setter`/`property` name (see [handleGenLine] matrix). */
internal fun addGenKeys(
    state: ScanState,
    off: Int,
    ns: String?,
    base: String,
    suffix: String,
    nm: String,
) {
    if (base == "setter") {
        addMethodSymbol(state.symbols, state.relPath, off, ns, "$nm=")
        return
    }
    when (suffix) {
        "" -> {
            addMethodSymbol(state.symbols, state.relPath, off, ns, nm)
        }

        "?" -> {
            addMethodSymbol(state.symbols, state.relPath, off, ns, "$nm?")
        }

        "!" -> {
            addMethodSymbol(state.symbols, state.relPath, off, ns, "$nm?")
            addMethodSymbol(state.symbols, state.relPath, off, ns, nm)
        }
    }
    if (base == "property") addMethodSymbol(state.symbols, state.relPath, off, ns, "$nm=")
}

/**
 * Enum member predicates (`Red` in `enum Color` → `Color#red?`). Real Crystal
 * generates a `member?` predicate for every enum constant (verified 1.21.0:
 * `Color::Red.red?`, `DarkBlue` → `dark_blue?`, `IO` → `io?`,
 * `UInt128x` → `u_int128x?`; aliases like `Default = LineNumbers` get
 * `default?` too — the alias is a real constant with its own predicate).
 * Only directly inside an `enum` body (checked via [ScanState.typeKinds]):
 * the frame must be open and the innermost type must be an enum. Members
 * with explicit values are indexed as well — the name before `=` is the
 * constant. Comma-separated lists (`A, B`) yield one key per member. The
 * offset points at the CONSTANT so materialize lands on the member.
 * ALL-CAPS members never reach here (constRe claims them first) — their
 * predicate key is added in handleConstLine instead.
 */
private fun handleEnumLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    if (state.typeKinds.lastOrNull() != "enum") return false
    if (!directlyInType(state)) return false
    val ns = state.stack.lastOrNull() ?: return false
    var found = false
    // Accepted imprecision: a value expression containing a comma
    // (`A = f(x, y)`) splits into extra tokens, but only tokens STARTING with
    // a capitalized name yield keys, so the damage is one spurious key at worst.
    for (tok in raw.split(',')) {
        val name = enumMemberRe.find(tok)?.groupValues?.get(1) ?: continue
        val g1 = enumMemberRe.find(tok)!!.groups[1]!!
        // tok is a substring of raw — locate it once, then add the in-token offset.
        val tokStart = raw.indexOf(tok)
        val offset = lineStart + tokStart + g1.range.first
        state.symbols.putIfAbsent(
            "$ns#${CrystalNameUtils.crystalUnderscore(name)}?",
            SymbolLoc(state.relPath, offset),
        )
        found = true
    }
    return found
}

/**
 * Type field declarations (`x : Int32`, `@x : T = default` directly in a
 * struct/class/module/lib body → `Type#field`). Only when the innermost
 * open frame is the type itself (see [directlyInType]): inside a method this
 * shape is a local annotation, and with an empty stack there is no enclosing
 * type. Runs after `getter`/`setter`/`property` expansion (handleGenLine
 * first), so generated readers/writers keep their own keys.
 */
private fun handleFieldLine(
    raw: String,
    lineStart: Int,
    state: ScanState,
): Boolean {
    val m = fieldRe.find(raw) ?: return false
    // See [directlyInType]: `def`/`macro`/`fun` lines are handled before us
    // and never reach here, but `end` accounting still matters (a method body
    // pushes exactly one "other" frame — blockKwRe counts if/while/do-blocks
    // too, so this also excludes fields after blocks).
    if (!directlyInType(state)) return false
    val ns = state.stack.lastOrNull() ?: return false
    val rawName = m.groupValues[1]
    val name = rawName.removePrefix("@@").removePrefix("@")
    val g1 = m.groups[1]!!
    val offset = lineStart + g1.range.first + (g1.value.length - name.length)
    state.symbols.putIfAbsent("$ns#$name", SymbolLoc(state.relPath, offset))
    return true
}

/** Any other line: balance block opens (`def`/control-flow keywords, `{`) against closes (lone-`end`, `}`).
 * `}` pops block frames only (popBrace drops it atop a type frame) — see [ScanState.popBrace].
 * A same-line one-liner (`foo do ... end`) nets to zero via sameLineEndRe. */
private fun balanceOtherLine(
    raw: String,
    state: ScanState,
) {
    val opens = blockKwRe.findAll(raw).count() + raw.count { it == '{' }
    repeat(opens) { state.openKinds.addLast("other") }
    if (lineEndRe.containsMatchIn(raw) || sameLineEndRe.containsMatchIn(raw)) state.popOpen()
    repeat(raw.count { it == '}' }) { state.popBrace() }
}

/** Split a `def` signature into (receiver, methodName). `self.foo` → (null, foo);
 *  `Foo.bar` → (Foo, bar); bare `foo` → (null, foo). */
internal fun parseDefSig(sig: String): Pair<String?, String> {
    if (sig.startsWith("self.")) return null to sig.substring("self.".length)
    val dot = sig.lastIndexOf('.')
    if (dot > 0) return sig.substring(0, dot) to sig.substring(dot + 1)
    return null to sig
}

private val blockKwRe = Regex("""\b(def|macro|if|unless|while|until|case|begin|do)\b""")

// A line whose only code token is `end` (indentation + optional `;`/comment).
// Bare `\bend\b` counting overcounts: `end` inside string literals, comments,
// and same-line closers (`x = [1].map do ... end`) drift the frame stack and
// pop enclosing type frames. Anchoring balance closes to lone-`end` lines is
// the conservative fix: a missed same-line close leaves a stale frame
// (harmless: keys already recorded), while a phantom close destroys a
// namespace (harmful: every later key mis-namespaced).
private val lineEndRe = Regex("""^\s*end\s*(?:[;].*)?$""")

// A one-liner whose opener and closer share the line
// (`class Error < Exception; end`, `def foo; end`) — nets its frame to zero.
private val sameLineEndRe = Regex(""";\s*end\s*(?:[;].*)?$""")
private val typeRe =
    Regex("""^\s*(?:(?:abstract|final|private)\s+)*(class|struct|module|enum|lib|annotation)\s+([A-Z][\w:]*)""")
private val aliasRe = Regex("""^\s*alias\s+([A-Z]\w*(?:::[A-Z]\w*)*)""")
private val constRe = Regex("""^\s*([A-Z][A-Z0-9_]*)\s*=""")
private val defRe =
    Regex("""^\s*(?:(?:private|protected|abstract)\s+)*(?:def|macro)\s+((?:self\.)?(?:[A-Z][\w:]*)?\.?[a-zA-Z_]\w*[!?]?|\[[\]=]?|<=>)""")

// Bodiless `abstract def` (optionally after private/protected): owns no
// `end`, so handleDefLine must not push a balance frame for it.
private val abstractDefRe = Regex("""^\s*(?:(?:private|protected)\s+)*abstract\s+(?:def|macro)\b""")

// No `setter?`/`setter!` macros exist in Crystal (only plain `setter`) —
// the macro set mirrors object/properties.cr exactly.
private val genRe =
    Regex("""^\s*(class_getter[?!]?|class_setter|class_property[?!]?|getter[?!]?|setter|property[?!]?)(?=[\s(:]|$)\s*(.+)$""")

// `fun` C-binding declarations, inside `lib` or top-level. Group 1 is the
// declared name; for `fun foo = bar` aliases that is `foo` (the regex stops
// at whitespace, never reaching `= bar`).
private val funRe = Regex("""^\s*(?:(?:private|protected)\s+)*fun\s+([a-zA-Z_]\w*[!?]?)""")

// CamelCase enum member (`Red`, `DarkBlue`, `Default = LineNumbers`, `A, B`).
// ALL-CAPS members never reach handleEnumLine (constRe claims them first).
private val enumMemberRe = Regex("""^\s*([A-Z]\w*)\s*(?:=[^,]+)?\s*$""")

// Type field declarations (`x : Int32`, `@ivar : T`). The `(?!:)` rejects
// namespace paths (`x::Y`); the type must start uppercase or `::`-qualified
// (excludes locals, call args, ternaries, symbol values). Group 1 keeps any
// `@`/`@@` prefix so the offset can be adjusted past it in handleFieldLine.
private val fieldRe = Regex("""^\s*(?:(?:private|protected)\s+)?(@{0,2}[a-z_]\w*[!?]?)\s*:(?!:)\s*(?:::)?[A-Z]""")
