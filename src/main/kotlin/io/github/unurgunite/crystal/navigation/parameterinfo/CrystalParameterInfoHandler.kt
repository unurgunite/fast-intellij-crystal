package io.github.unurgunite.crystal.navigation.parameterinfo

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.CrystalLanguage
import io.github.unurgunite.crystal.completion.CrystalCompletionHelper
import io.github.unurgunite.crystal.completion.CrystalRecordCompletion
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.psi.CrystalBareArgumentList
import io.github.unurgunite.crystal.psi.CrystalBareMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalDotCallAccess
import io.github.unurgunite.crystal.psi.CrystalMethodCallExpression
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalParameter
import io.github.unurgunite.crystal.psi.CrystalRecordDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.stubs.CrystalMethodIndex

/**
 * Provides parameter info (Ctrl+P) for Crystal method calls.
 * Supports:
 * - Parenthesized calls: foo(a, b)
 * - Bare (parenthesis-free) calls: foo a, b
 * - Dot-calls with parens: obj.method(a, b)
 * - Dot-calls bare: obj.method a, b
 * - Class method calls: Foo.bar(a, b)
 *
 * Handles cursor positions after comma with/without whitespace,
 * including incomplete expressions where the PSI tree is broken,
 * and bare calls where no argument has been typed yet.
 */
class CrystalParameterInfoHandler : ParameterInfoHandler<PsiElement, Any> {
    // ==================== Record Parameter Info ====================

    companion object {
        // ", " separator length, used to advance the highlight across parameters.
        private const val PARAMETER_SEPARATOR_LENGTH = 2
    }

    /**
     * True when [gap] is whitespace without a newline (possibly empty): the cursor
     * sits right after an identifier with no arguments typed yet.
     */
    private fun isBlankSingleLineGap(gap: String): Boolean = gap.isBlank() && !gap.contains('\n')

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val argsHolder = findArgsHolder(context.file, context.offset) ?: return null
        val methodName = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder) ?: return null

        // Special case: "new" on a class → resolve directly to "initialize" parameters
        // (see findNewParameterInfo).
        if (methodName == "new") {
            return findNewParameterInfo(argsHolder, context)
        }

        val methods = findCandidateMethods(context, methodName, argsHolder)
        if (methods.isEmpty()) return null
        context.itemsToShow = methods
        return argsHolder
    }

    /**
     * Methods named [methodName] from the index, narrowed to the receiver's class
     * for DOT-calls on CONSTANT receivers. This prevents stdlib methods like
     * ENV.fetch from showing params of Hash#fetch etc.
     *
     * The "new" special case never reaches here: constructor calls resolve directly
     * to "initialize" parameters via CrystalMethodByClassIndex (O(1)) instead of
     * searching CrystalMethodIndex for ALL methods named "new" across the entire
     * stdlib (expensive + causes freezes).
     */
    private fun findCandidateMethods(
        context: CreateParameterInfoContext,
        methodName: String,
        argsHolder: PsiElement,
    ): Array<CrystalMethodDefinition> {
        val project = context.project
        val scope = GlobalSearchScope.allScope(project)
        val methods =
            StubIndex
                .getElements(
                    CrystalMethodIndex.KEY,
                    methodName,
                    project,
                    scope,
                    CrystalMethodDefinition::class.java,
                )
        val receiverName =
            CrystalParameterInfoReceiver
                .findReceiverNameFromSiblings(argsHolder)
                ?.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
                ?: return methods.toTypedArray()
        return methods
            .filter { method ->
                CrystalParameterInfoReceiver.findEnclosingTypeName(method) == receiverName
            }.toTypedArray()
    }

    /**
     * Resolves parameter info for ClassName.new(...) calls.
     * Skips the expensive CrystalMethodIndex search for "new" (which would load ALL
     * stdlib .new methods) and goes directly to initialize resolution via
     * CrystalMethodByClassIndex (O(1) lookup).
     */
    private fun findNewParameterInfo(
        argsHolder: PsiElement,
        context: CreateParameterInfoContext,
    ): PsiElement? {
        val className = CrystalParameterInfoReceiver.findClassNameBeforeNew(argsHolder) ?: return null

        // 1. Try initialize method (most common case)
        val project = context.project
        val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, argsHolder.containingFile)
        if (initMethod != null) {
            context.itemsToShow = arrayOf(initMethod)
            return argsHolder
        }

        // 2. Try record macro (record Foo, bar : String, baz : Int32)
        val file = argsHolder.containingFile ?: return null
        val recordDef = CrystalRecordCompletion.findRecordDefinition(className, file)
        if (recordDef != null) {
            context.itemsToShow = arrayOf(CrystalParameterInfoIndex.extractRecordParameterList(recordDef))
            return argsHolder
        }

        // No initialize method, no record → no parameter info
        return null
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        var result = findArgsHolder(context.file, context.offset)
        if (result == null && context.offset > 0) {
            // IntelliJ sometimes calls with offset-1; try offset+1
            result = findArgsHolder(context.file, context.offset + 1)
        }
        return result
    }

    override fun showParameterInfo(
        element: PsiElement,
        context: CreateParameterInfoContext,
    ) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun updateParameterInfo(
        parameterOwner: PsiElement,
        context: UpdateParameterInfoContext,
    ) {
        val offset = context.offset
        val index = CrystalParameterInfoIndex.computeCurrentParameterIndex(parameterOwner, offset)
        context.setCurrentParameter(index)
    }

    override fun updateUI(
        method: Any?,
        context: ParameterInfoUIContext,
    ) {
        if (method == null) {
            context.isUIComponentEnabled = false
            return
        }

        val params = parameterTextsOf(method) ?: return
        if (params.isEmpty()) {
            showNoParameters(context)
            return
        }
        val text = params.joinToString(", ")

        var startHighlight = -1
        var endHighlight = -1

        val currentIndex = context.currentParameterIndex
        if (currentIndex in params.indices) {
            startHighlight = params.take(currentIndex).sumOf { it.length + PARAMETER_SEPARATOR_LENGTH }
            endHighlight = startHighlight + params[currentIndex].length
        }

        context.setupUIComponentPresentation(
            text,
            startHighlight,
            endHighlight,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    /**
     * Rendered parameter texts for a popup item (method definition or record
     * info), or null when the item is not parameter-bearing.
     */
    private fun parameterTextsOf(method: Any): List<String>? =
        when (method) {
            is CrystalMethodDefinition -> (method.parameterList?.parameterList ?: emptyList()).map { it.text.trim() }
            is RecordParameterInfo -> method.params.map { it.text }
            else -> null
        }

    private fun showNoParameters(context: ParameterInfoUIContext) {
        context.setupUIComponentPresentation(
            "<no parameters>",
            -1,
            -1,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    // ==================== Anchor Search ====================

    /**
     * Finds the argument-list PSI node that contains or is adjacent to the given offset.
     *
     * Search order:
     * 1. Quick check (DOT-call / statement-start bare call directly before cursor)
     * 2. PSI-based: CrystalCallArgs or CrystalBareArgumentList at offset/offset-1
     * 3. Unmatched LPAREN (for broken PSI in paren-calls)
     * 4. Bare-call backtracking (for bare calls with no/incomplete args)
     * 5. RPAREN edge case
     */
    fun findArgsHolder(
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        // Quick check: if the cursor is directly after a method name (DOT-call or bare call),
        // return a synthetic anchor for it. This must run BEFORE the Primary/Fallback A paths
        // because `findArgsParent` would otherwise return the OUTER call's args (e.g., for
        // `puts Tesa.hika<caret>`, it would return `puts`'s CrystalBareArgumentList instead of
        // `hika`'s).
        CrystalParameterInfoAnchorSearch.findQuickCheckAnchor(file, offset)?.let { return it }

        val elementAtOffset = file.findElementAt(offset)
        CrystalParameterInfoAnchorSearch.findArgsAtElement(elementAtOffset, file, offset)?.let { return it }

        return CrystalParameterInfoAnchorSearch.rparenHolder(elementAtOffset)
    }
}
