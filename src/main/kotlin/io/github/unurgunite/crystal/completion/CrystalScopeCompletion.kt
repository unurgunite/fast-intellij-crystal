package io.github.unurgunite.crystal.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import io.github.unurgunite.crystal.psi.CrystalAssignment
import io.github.unurgunite.crystal.psi.CrystalBlock
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalForStatement
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes
import io.github.unurgunite.crystal.psi.util.extractParameterName
import io.github.unurgunite.crystal.stubs.CrystalMethodByClassIndex
import io.github.unurgunite.crystal.type.CrystalMethodLookup

/**
 * Free-text completion: scope items (block params, for-vars, method params,
 * locals, class vars, enclosing-class methods) plus stdlib types and classes
 * for empty/uppercase prefixes.
 */
internal object CrystalScopeCompletion {
    /**
     * Free-text completion — scope items + classes (uppercase only).
     * A leading @ / @@ is treated as part of the variable name so instance (@foo)
     * and class (@@bar) variables are suggested even when only the sigil is typed.
     */
    fun completeFreeText(
        parameters: CompletionParameters,
        position: PsiElement,
        project: Project,
        result: CompletionResultSet,
    ) {
        val actualPrefix = CrystalCompletionContributor.computeCompletionPrefix(parameters.editor, parameters.offset)
        val isVarPrefix = actualPrefix.startsWith("@")
        val effectiveResult =
            if (isVarPrefix) {
                result.withPrefixMatcher(result.prefixMatcher.cloneWithPrefix(actualPrefix))
            } else {
                result
            }
        val isUppercase = actualPrefix.isNotEmpty() && actualPrefix[0].isUpperCase()

        addLocalCompletions(position, parameters, effectiveResult)

        // Only suggest classes and stdlib types when prefix starts with uppercase.
        // When the prefix is a variable sigil (@ / @@), skip classes entirely.
        if (actualPrefix.isEmpty() || isUppercase) {
            for (lookup in CrystalTypeCompletionProvider.getStdlibTypeLookups()) {
                effectiveResult.addElement(lookup)
            }
            for (className in CrystalCompletionHelper.getAllClassNames(project)) {
                effectiveResult.addElement(CrystalLookupBuilders.buildClassLookup(className))
            }
        }
    }

    private fun addLocalCompletions(
        position: PsiElement,
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val seen = mutableSetOf<String>()

        addBlockParameters(position, seen, result)
        addForLoopVariables(position, seen, result)

        val method = PsiTreeUtil.getParentOfType(position, CrystalMethodDefinition::class.java)
        addMethodParameters(method, seen, result)
        addLocalVariables(position, seen, result)
        addClassVariables(parameters, seen, result)
        addEnclosingClassMethods(position, method, seen, result)
    }

    /** Block parameters (highest priority) — from all enclosing blocks. */
    private fun addBlockParameters(
        position: PsiElement,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        var currentBlock = PsiTreeUtil.getParentOfType(position, CrystalBlock::class.java)
        while (currentBlock != null) {
            val paramList = currentBlock.parameterList
            if (paramList != null) {
                for (param in paramList.parameterList) {
                    val name = extractParameterName(param) ?: continue
                    addParameterLookup(name, seen, result, CrystalCompletionContributor.PRIORITY_BLOCK_PARAMETER)
                }
            }
            currentBlock = PsiTreeUtil.getParentOfType(currentBlock.parent, CrystalBlock::class.java)
        }
    }

    /** Adds a parameter lookup unless the name was already suggested. */
    private fun addParameterLookup(
        name: String,
        seen: MutableSet<String>,
        result: CompletionResultSet,
        priority: Double,
    ) {
        if (seen.add(name)) {
            result.addElement(
                CrystalCompletionContributor.prioritizedLookup(
                    name,
                    AllIcons.Nodes.Parameter,
                    "parameter",
                    priority,
                ),
            )
        }
    }

    /** For-loop variables — IDENTIFIERs before the IN keyword. */
    private fun addForLoopVariables(
        position: PsiElement,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        val forStmt = PsiTreeUtil.getParentOfType(position, CrystalForStatement::class.java) ?: return
        for (child in forStmt.children) {
            if (child.node?.elementType == CrystalTypes.IDENTIFIER && seen.add(child.text)) {
                result.addElement(
                    CrystalCompletionContributor.prioritizedLookup(
                        child.text,
                        AllIcons.Nodes.Variable,
                        "for variable",
                        CrystalCompletionContributor.PRIORITY_FOR_VARIABLE,
                    ),
                )
            }
        }
    }

    /** Method parameters of the enclosing method. */
    private fun addMethodParameters(
        method: CrystalMethodDefinition?,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        if (method == null) return
        for (param in method.parameterList?.parameterList ?: emptyList()) {
            val name = extractParameterName(param) ?: continue
            addParameterLookup(name, seen, result, CrystalCompletionContributor.PRIORITY_EXPLICIT_PARAMETER)
        }
    }

    /** Local variables (method/block scoped, forward-reference excluded). */
    private fun addLocalVariables(
        position: PsiElement,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        val scope = findCompletionScope(position) ?: return
        PsiTreeUtil
            .findChildrenOfType(scope, CrystalAssignment::class.java)
            .asSequence()
            .filter { it.textOffset < position.textOffset }
            .mapNotNull { it.firstChild }
            // Handle raw IDENTIFIER tokens (local variables)
            .filter { it.node?.elementType == CrystalTypes.IDENTIFIER }
            .map { it.text }
            .filter { seen.add(it) }
            .forEach {
                result.addElement(
                    CrystalCompletionContributor.prioritizedLookup(
                        it,
                        AllIcons.Nodes.Variable,
                        "local",
                        CrystalCompletionContributor.PRIORITY_LOCAL,
                    ),
                )
            }
    }

    /**
     * Instance + class variables of the enclosing class (all methods).
     * Walks the entire file, stopping at nested type boundaries. This handles
     * the bare `@` parse error where methods after the caret become loose
     * file-level tokens outside the truncated class PSI node.
     */
    private fun addClassVariables(
        parameters: CompletionParameters,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        CrystalCompletionVarCollector.collectClassVariables(parameters.originalFile) { name, typeText ->
            if (seen.add(name)) {
                result.addElement(
                    CrystalCompletionContributor.prioritizedLookup(
                        name,
                        AllIcons.Nodes.Variable,
                        typeText,
                        CrystalCompletionContributor.PRIORITY_CLASS_VARIABLE,
                    ),
                )
            }
        }
    }

    /** Class methods of the enclosing class plus inherited (superclass) ones. */
    private fun addEnclosingClassMethods(
        position: PsiElement,
        method: CrystalMethodDefinition?,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        if (method == null) return
        val enclosingClassName = CrystalMethodLookup.getEnclosingClassName(method) ?: return
        val project = position.project
        val searchScope = GlobalSearchScope.allScope(project)
        addClassMethods(enclosingClassName, CrystalCompletionContributor.PRIORITY_OWN_CLASS_METHOD, searchScope, project, seen, result)

        // Inherited: direct superclass
        val enclosingClass = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
        val superClassName = enclosingClass?.superclassClause?.typeReference?.text
        if (superClassName != null && superClassName != enclosingClassName) {
            addClassMethods(superClassName, CrystalCompletionContributor.PRIORITY_SUPERCLASS_METHOD, searchScope, project, seen, result)
        }
    }

    /**
     * Adds methods of a specific class (via CrystalMethodByClassIndex) to the result.
     */
    private fun addClassMethods(
        className: String,
        priority: Double,
        scope: GlobalSearchScope,
        project: Project,
        seen: MutableSet<String>,
        result: CompletionResultSet,
    ) {
        StubIndex
            .getElements(
                CrystalMethodByClassIndex.KEY,
                className,
                project,
                scope,
                CrystalMethodDefinition::class.java,
            ).asSequence()
            .mapNotNull { it.name }
            .filter { it != "initialize" && seen.add(it) }
            .forEach {
                val lookup =
                    LookupElementBuilder
                        .create(it)
                        .withIcon(AllIcons.Nodes.Method)
                        .withTypeText("method", true)
                result.addElement(PrioritizedLookupElement.withPriority(lookup, priority))
            }
    }

    /**
     * Finds the scope element for local variable completion.
     * Crystal uses method-scoped local variables (like Ruby).
     */
    private fun findCompletionScope(element: PsiElement): PsiElement? {
        // Inside a method — scan the method body
        val method = PsiTreeUtil.getParentOfType(element, CrystalMethodDefinition::class.java)
        if (method != null) return method
        // Inside a block without method — scan the block
        val block = PsiTreeUtil.getParentOfType(element, CrystalBlock::class.java)
        if (block != null) return block
        // Top level — scan the file
        return element.containingFile
    }
}
