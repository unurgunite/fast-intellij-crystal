package io.github.unurgunite.crystal

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.TokenSet
import io.github.unurgunite.crystal.lexer.CrystalLexerAdapter
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.parser.CrystalParser
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalParserDefinition : ParserDefinition {
    companion object {
        val FILE =
            object : IStubFileElementType<PsiFileStub<CrystalFile>>(CrystalLanguage) {
                override fun getExternalId(): String = "crystal.FILE"

                // Bump on ANY grammar/lexer change that alters the stub tree shape.
                // Stale stubs built by an older parser (or from older file bytes)
                // fail live-index reconciliation with
                // `UpToDateStubIndexMismatch: Stub count (7) doesn't match
                // stubbed node length (12)` (e.g. io/delimited.cr, 2026-09).
                // Version 3 forces a full stdlib reindex with the current parser.
                override fun getStubVersion(): Int = 4
            }
    }

    override fun createLexer(project: Project?): Lexer = CrystalLexerAdapter()

    override fun createParser(project: Project?): PsiParser {
        // Safety net against runaway backtracking on pathological inputs. The
        // value must stay high enough for real code: grammar-kit charges one
        // level per rule call, and a single nested call costs dozens (every
        // `statement` alternative is a rule of its own), so a seven-deep
        // do-block chain of `builder.field(...)` calls — an ordinary JSON
        // builder — ran past 200 and stopped parsing mid-file with
        // "unmatched input". 10_000 still cuts off genuine runaway recursion
        // long before it can freeze the IDE. Override with
        // -Dcrystal.gpub.max.level for tuning.
        try {
            if (System.getProperty("grammar.kit.gpub.max.level") == null) {
                val limit = System.getProperty("crystal.gpub.max.level") ?: "10000"
                System.setProperty("grammar.kit.gpub.max.level", limit)
            }
        } catch (_: SecurityException) {
        }
        return CrystalParser()
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(CrystalTokenTypes.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = CrystalTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = CrystalTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = CrystalTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = CrystalFile(viewProvider)
}
