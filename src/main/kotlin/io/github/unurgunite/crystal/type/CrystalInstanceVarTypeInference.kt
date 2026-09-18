package io.github.unurgunite.crystal.type

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalPropertyDeclaration
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.CrystalPsiUtils
import io.github.unurgunite.crystal.psi.util.extractParameterName
import io.github.unurgunite.crystal.type.CrystalMethodReturnTypeInference.splitTypeNames

/**
 * Type inference for instance/class variables (`@name`, `@@count`).
 *
 * The gap this closes: `name = @name` (the `path.cr` shape — `@name` typed by
 * `initialize(@name : String)`) left the local untyped, so DOT-calls on it
 * (`name.starts_with?(...)`) and on the ivar itself (`@name.empty?`) never
 * resolved. The generic inference only understands plain locals: parameters
 * match by exact name (the `@`-shorthand strip never fires for `@`-prefixed
 * queries) and `@`-RHS expressions matched no pattern at all.
 *
 * Sources, in order:
 * 1. Declared types — `@`-shorthand parameters (`def initialize(@name : String)`)
 *    and property declarations (`@name : String`) — indexed once per file
 *    version (dropped on any PSI change), keyed by qualified enclosing type.
 * 2. `@ivar = expr` assignments, via the generic inference (depth-guarded).
 *
 * Only offsets/names/types are cached (never PSI), so the cache cannot go stale.
 * `getter`/`property` macro calls (`getter name : String`) are NOT a source yet:
 * they declare ivars too, but parsing their type annotations is future work.
 */
internal object CrystalInstanceVarTypeInference {
    /** `@name` / `@@name` reference shape (a bare `@` alone is not a reference). */
    private val IVAR_REF = Regex("""^@@?[A-Za-z_]\w*$""")

    /** True for `@name` / `@@count` reference text (surrounding whitespace ignored). */
    fun isIvarRef(text: String): Boolean = IVAR_REF.matches(text.trim())

    /** `true` when [element] is (or wraps) an `@`/`@@` variable access. */
    fun isIvarAccess(element: PsiElement): Boolean {
        if (element is CrystalInstanceVarAccess || element is CrystalClassVarAccess) return true
        val type = element.node?.elementType
        if (type == CrystalTypes.INSTANCE_VAR || type == CrystalTypes.CLASS_VAR) return true
        if (type == CrystalTypes.INSTANCE_VAR_ACCESS || type == CrystalTypes.CLASS_VAR_ACCESS) return true
        val node = element.node ?: return false
        return node.findChildByType(CrystalTypes.INSTANCE_VAR_ACCESS) != null ||
            node.findChildByType(CrystalTypes.CLASS_VAR_ACCESS) != null
    }

    /** `@name` → `name`; null when [varText] is not an ivar reference. */
    fun bareName(varText: String): String? {
        val text = varText.trim()
        if (!isIvarRef(text)) return null
        return text.removePrefix("@@").removePrefix("@")
    }

    /**
     * Full ivar type: declared types first, then `@ivar = expr` assignments.
     * Empty means unknown (no guessing). [depth] guards the assignment fallback.
     */
    fun inferIvarType(
        varText: String,
        context: PsiElement,
        project: Project,
        depth: Int,
    ): List<String> {
        if (bareName(varText) == null) return emptyList()
        val declared = inferDeclaredType(varText, context)
        if (declared.isNotEmpty()) return declared
        // No declaration: `@ivar = expr` assignments via the generic inference
        // (which matches `@`-prefixed targets through the assignment index).
        return CrystalTypeInference.inferTypeList(varText.trim(), context, project, depth + 1)
    }

    /**
     * Types declared for an ivar by `@`-shorthand params and property
     * declarations visible from [context]'s enclosing type. Exact qualified
     * match first (`Path#name`), then any same-bare-name entry in document
     * order (deterministic fallback for top-level contexts).
     */
    fun inferDeclaredType(
        varText: String,
        context: PsiElement,
    ): List<String> {
        val bare = bareName(varText) ?: return emptyList()
        val file = context.containingFile ?: return emptyList()
        val byKey =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result(indexFile(file), PsiModificationTracker.MODIFICATION_COUNT)
            }
        if (byKey.isEmpty()) return emptyList()
        return lookupDeclared(byKey, context, bare)
    }
}

/** Exact qualified match first, then the deterministic same-name fallback. */
private fun lookupDeclared(
    byKey: Map<String, List<String>>,
    context: PsiElement,
    bare: String,
): List<String> {
    val enclosing =
        CrystalPsiUtils.getEnclosingType(context)?.let { CrystalPsiUtils.buildQualifiedName(it) }
    if (enclosing != null) {
        byKey["$enclosing#$bare"]?.let { return it }
    }
    for ((key, types) in byKey) {
        if (key.endsWith("#$bare")) return types
    }
    return emptyList()
}

/** Declared ivar types of [file]: `@`-shorthand params first, then properties. */
private fun indexFile(file: PsiElement): Map<String, List<String>> {
    val byKey = LinkedHashMap<String, List<String>>()
    indexShorthandParams(file, byKey)
    indexPropertyDeclarations(file, byKey)
    return byKey
}

/**
 * `@`-shorthand parameters (`def initialize(@name : String)`). Plain
 * parameters (`def foo(name : String)`) declare NO ivar and are skipped —
 * the gate is the `@`/`@@` access node, not the stripped name.
 */
private fun indexShorthandParams(
    file: PsiElement,
    byKey: MutableMap<String, List<String>>,
) {
    for (method in PsiTreeUtil.collectElementsOfType(file, CrystalMethodDefinition::class.java)) {
        val params = method.parameterList?.parameterList ?: continue
        val enclosing = qualifiedEnclosing(method)
        for (param in params) {
            shorthandParamEntry(param, enclosing)?.let { (key, types) -> byKey.putIfAbsent(key, types) }
        }
    }
}

/** `Type#name` entry for one shorthand parameter, or null when inapplicable. */
private fun shorthandParamEntry(
    param: io.github.unurgunite.crystal.psi.CrystalParameter,
    enclosing: String,
): Pair<String, List<String>>? {
    val isShorthand =
        param.node.findChildByType(CrystalTypes.INSTANCE_VAR_ACCESS) != null ||
            param.node.findChildByType(CrystalTypes.CLASS_VAR_ACCESS) != null
    if (!isShorthand) return null
    val name = extractParameterName(param) ?: return null
    val typeText = param.typeReference?.text ?: return null
    return "$enclosing#$name" to splitTypeNames(typeText)
}

/** Property declarations (`@name : String`, `@@count : Int32`). */
private fun indexPropertyDeclarations(
    file: PsiElement,
    byKey: MutableMap<String, List<String>>,
) {
    for (prop in PsiTreeUtil.collectElementsOfType(file, CrystalPropertyDeclaration::class.java)) {
        propertyDeclEntry(prop)?.let { (key, types) -> byKey.putIfAbsent(key, types) }
    }
}

/** `Type#name` entry for one property declaration, or null when inapplicable. */
private fun propertyDeclEntry(prop: CrystalPropertyDeclaration): Pair<String, List<String>>? {
    val declName = propertyDeclName(prop) ?: return null
    val bare = declName.removePrefix("@@").removePrefix("@")
    if (bare.isEmpty() || bare == declName) return null
    val typeText = prop.typeReference?.text ?: return null
    return "${qualifiedEnclosing(prop)}#$bare" to splitTypeNames(typeText)
}

/** Declared name of a property: raw `@`/`@@` leaf, plain identifier, or var access. */
private fun propertyDeclName(prop: CrystalPropertyDeclaration): String? =
    prop.node.findChildByType(CrystalTypes.INSTANCE_VAR)?.text
        ?: prop.node.findChildByType(CrystalTypes.CLASS_VAR)?.text
        ?: prop.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
        ?: accessText(prop, CrystalInstanceVarAccess::class.java)
        ?: accessText(prop, CrystalClassVarAccess::class.java)

/** Text of the first strict [accessClass] child, if present. */
private fun accessText(
    element: PsiElement,
    accessClass: Class<out PsiElement>,
): String? = PsiTreeUtil.findChildOfType(element, accessClass, false)?.text

/** Qualified enclosing type name of [element], or `<top>` outside any type. */
private fun qualifiedEnclosing(element: PsiElement): String =
    CrystalPsiUtils.getEnclosingType(element)?.let { CrystalPsiUtils.buildQualifiedName(it) } ?: "<top>"
