package io.github.unurgunite.crystal.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.completion.CrystalRecordCompletion
import io.github.unurgunite.crystal.navigation.ivar.CrystalInstanceVarFinder
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalRequireStatement
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex

/**
 * Handles Go to Definition (Ctrl+Click / Ctrl+B) for:
 * 1. Identifiers after DOT (e.g. "Apfel.tanzen" → jumps to "def self.tanzen" or "def tanzen")
 * 2. Instance variables (@name) and class variables (@@name) → jumps to property declaration or shows all usages
 * 3. ".new" on a class (e.g. "Senf.new") → jumps to "def self.new", "record Senf", or "def initialize"
 *    following Crystal's constructor resolution order (self.new > record > initialize).
 */
class CrystalGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // `require "..."` navigation: resolve the required path to its target .cr file.
        requireTargets(sourceElement)?.let { return it }

        return when (sourceElement.node.elementType) {
            CrystalTypes.INSTANCE_VAR, CrystalTypes.CLASS_VAR -> varTargets(sourceElement)
            CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT -> identifierTargets(sourceElement)
            else -> null
        }
    }

    /** Targets for a `require "..."` statement enclosing the element, if any. */
    private fun requireTargets(sourceElement: PsiElement): Array<PsiElement>? {
        val requireStatement =
            PsiTreeUtil.getParentOfType(
                sourceElement,
                CrystalRequireStatement::class.java,
            ) ?: return null
        val targets = CrystalRequireResolver.resolve(requireStatement, sourceElement.project)
        return if (targets.isNotEmpty()) targets.toTypedArray() else null
    }

    /** Targets for `@name` / `@@name`: definitions first, otherwise other usages. */
    private fun varTargets(sourceElement: PsiElement): Array<PsiElement>? {
        // The leaf token's parent should be the CrystalInstanceVarAccess/CrystalClassVarAccess composite
        val varAccess = sourceElement.parent
        val (varName, self) =
            if (varAccess is CrystalInstanceVarAccess || varAccess is CrystalClassVarAccess) {
                varAccess.text to varAccess
            } else {
                // Fallback for @name in property_declaration or parameter (still leaf tokens)
                sourceElement.text to sourceElement
            }
        val anchor = if (self === sourceElement) sourceElement else self
        val targets = CrystalInstanceVarFinder.findDefinitionTargets(varName, anchor)
        if (targets.isNotEmpty()) return targets.toTypedArray()
        val usages =
            CrystalInstanceVarFinder
                .findAllUsages(varName, anchor)
                .filter { it !== anchor }
        return if (usages.isNotEmpty()) usages.toTypedArray() else null
    }

    /**
     * Targets for IDENTIFIER/CONSTANT: only the `.new` constructor case is handled
     * here (self.new > record > initialize order). Everything else resolves via
     * `PsiReference` (same way as top-level calls): the dot_call_access BNF rule
     * provides a CrystalDotCallReference through its mixin, so the platform's
     * TargetElementUtil resolves DOT-calls before reaching this handler as a
     * fallback (e.g. unknown-instance receiver → null, no false-positive guessing).
     */
    private fun identifierTargets(sourceElement: PsiElement): Array<PsiElement>? {
        val name = sourceElement.text
        if (name.isBlank() || name != "new") {
            // Non-.new DOT-calls are fully handled by the PsiReference — return null here
            // so the platform uses the reference resolution result.
            return null
        }
        val className =
            findClassNameBeforeNewToken(sourceElement)
                // No class receiver detected — return null rather than flooding the
                // user with every "new" method in the project.
                ?: return null
        val targets = findNewTargets(className, sourceElement)
        return if (targets.isNotEmpty()) targets.toTypedArray() else null
    }

    /**
     * For "ClassName.new" — extracts the class name from the receiver before ".new".
     * Returns null for non-class receivers (e.g. "obj.new" where obj is lowercase).
     *
     * Uses PsiTreeUtil.prevLeaf to cross composite boundaries: for "Senf.new" the leaf
     * before DOT is the CONSTANT "Senf"; for "Outer::Inner.new" the last CONSTANT ("Inner")
     * is returned, which is the correct key for CrystalMethodByClassIndex (immediate
     * enclosing class).
     */
    private fun findClassNameBeforeNewToken(newToken: PsiElement): String? {
        val dot = prevLeafSkipWhitespace(newToken) ?: return null
        if (dot.node.elementType != CrystalTypes.DOT) return null
        val receiver = prevLeafSkipWhitespace(dot) ?: return null
        if (receiver.node.elementType == CrystalTypes.CONSTANT) return receiver.text
        return null
    }

    /**
     * Resolves "ClassName.new" to its actual target, following Crystal's constructor
     * resolution order:
     * 1. "def self.new" in the class — explicit override of the default constructor
     * 2. "record ClassName, ..." macro — auto-generates "new" with record fields
     * 3. "def initialize" in the class — called by the built-in "Class#new"
     */
    private fun findNewTargets(
        className: String,
        sourceElement: PsiElement,
    ): List<PsiElement> {
        val project = sourceElement.project
        val scope = GlobalSearchScope.allScope(project)

        // 1. Explicit "def self.new" in the class — takes priority (overrides default "new")
        val classMethods =
            StubIndex.getElements(
                CrystalMethodByClassIndex.KEY,
                className,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            )
        val selfNew = classMethods.filter { it.name == "new" }
        if (selfNew.isNotEmpty()) return selfNew.toList()

        // 2. "record" macro — auto-generates "new" with the record fields as parameters
        val recordDef = CrystalRecordCompletion.findRecordDefinition(className, sourceElement.containingFile)
        if (recordDef != null) return listOf(recordDef)

        // 3. Default: "def initialize" (called by the built-in "Class#new")
        val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, sourceElement.containingFile)
        if (initMethod != null) return listOf(initMethod)

        return emptyList()
    }

    private fun prevLeafSkipWhitespace(element: PsiElement): PsiElement? {
        var prev: PsiElement? = PsiTreeUtil.prevLeaf(element)
        while (prev != null && prev.node.elementType.toString() == "WHITE_SPACE") {
            prev = PsiTreeUtil.prevLeaf(prev)
        }
        return prev
    }
}
