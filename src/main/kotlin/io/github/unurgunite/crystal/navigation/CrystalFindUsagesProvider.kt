package io.github.unurgunite.crystal.navigation

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import io.github.unurgunite.crystal.lexer.CrystalLexerAdapter
import io.github.unurgunite.crystal.psi.CrystalClassDefinition
import io.github.unurgunite.crystal.psi.CrystalClassVarAccess
import io.github.unurgunite.crystal.psi.CrystalConstantAssignment
import io.github.unurgunite.crystal.psi.CrystalEnumDefinition
import io.github.unurgunite.crystal.psi.CrystalInstanceVarAccess
import io.github.unurgunite.crystal.psi.CrystalMacroDefinition
import io.github.unurgunite.crystal.psi.CrystalMethodDefinition
import io.github.unurgunite.crystal.psi.CrystalModuleDefinition
import io.github.unurgunite.crystal.psi.CrystalNamedElement
import io.github.unurgunite.crystal.psi.CrystalStructDefinition
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner() =
        DefaultWordsScanner(
            CrystalLexerAdapter(),
            TokenSet.create(CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT, CrystalTypes.INSTANCE_VAR, CrystalTypes.CLASS_VAR),
            TokenSet.create(CrystalTypes.LINE_COMMENT),
            TokenSet.create(CrystalTypes.STRING_LITERAL),
        )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        if (psiElement is CrystalNamedElement) return true
        val tokenType = psiElement.node?.elementType
        return tokenType == CrystalTypes.IDENTIFIER || tokenType == CrystalTypes.CONSTANT
    }

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String =
        when (element) {
            is CrystalInstanceVarAccess -> {
                "instance variable"
            }

            is CrystalClassVarAccess -> {
                "class variable"
            }

            is CrystalClassDefinition -> {
                "class"
            }

            is CrystalModuleDefinition -> {
                "module"
            }

            is CrystalStructDefinition -> {
                "struct"
            }

            is CrystalEnumDefinition -> {
                "enum"
            }

            is CrystalMethodDefinition -> {
                "method"
            }

            is CrystalMacroDefinition -> {
                "macro"
            }

            is CrystalConstantAssignment -> {
                "constant"
            }

            else -> {
                when (element.node?.elementType) {
                    CrystalTypes.CONSTANT -> "type"
                    CrystalTypes.IDENTIFIER -> "symbol"
                    else -> "element"
                }
            }
        }

    override fun getDescriptiveName(element: PsiElement): String = element.text

    override fun getNodeText(
        element: PsiElement,
        useFullName: Boolean,
    ): String = element.text
}
