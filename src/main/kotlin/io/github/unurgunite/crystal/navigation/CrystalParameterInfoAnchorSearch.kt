package io.github.unurgunite.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.unurgunite.crystal.psi.CrystalCallArgs
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Anchor search for parameter info: locates the argument-list PSI node at the
 * cursor through PSI lookup, unmatched-LPAREN text scans and bare-call
 * backtracking. Split out of [CrystalParameterInfoHandler].
 */
internal object CrystalParameterInfoAnchorSearch {
    /**
     * PSI and text fallbacks after the quick check: element at offset, element
     * before the offset, unmatched-LPAREN scan, bare-call backtracking.
     */
    fun findArgsAtElement(
        elementAtOffset: PsiElement?,
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        CrystalBareCallScanner.findArgsParent(elementAtOffset)?.let { return it }
        if (offset > 0) {
            CrystalBareCallScanner.findArgsParent(file.findElementAt(offset - 1))?.let { return it }
        }
        findArgsHolderByUnmatchedLparen(file, offset)?.let { return it }
        return CrystalBareCallScanner.scanBackwardsForBareCall(file, offset)
    }

    /** RPAREN edge case: the args holder is the paren's parent. */
    fun rparenHolder(elementAtOffset: PsiElement?): PsiElement? {
        if (elementAtOffset?.node?.elementType != CrystalTypes.RPAREN) return null
        val parent = elementAtOffset.parent
        if (parent is CrystalCallArgs) return parent
        return parent
    }

    /**
     * Quick check for a DOT-call or statement-start bare call directly before the
     * cursor. Returns the synthetic anchor, or null to continue with PSI lookup.
     *
     * Accepts the anchor when the method name is preceded by DOT (`obj.method`) or
     * sits at statement start — plus an identifier that has a DOT-call immediately
     * before it (`puts Tesa.hika<caret>` must own the DOT-call before the outer
     * call's args win). Plain argument identifiers fall through to PSI lookup.
     */
    fun findQuickCheckAnchor(
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        val quickCheckAnchor = CrystalBareCallScanner.scanBackwardsForBareCall(file, offset)
        if (quickCheckAnchor !is CrystalParameterInfoAnchor) return null
        val nameToken = quickCheckAnchor.nameToken
        // Only use the quick check if it found a DOT-call or a bare call (not just a plain identifier)
        val prev = CrystalBareCallScanner.prevMeaningfulLeaf(nameToken)
        val prevType = prev?.node?.elementType
        // Use quick check for DOT-calls and bare calls (where the method name is at the start of a statement)
        if (prevType == CrystalTypes.DOT || prevType == null) {
            return quickCheckAnchor
        }
        // `puts Tesa.hika<caret>`: the quick check anchored `puts`, but a DOT-call
        // sits between it and the cursor — re-anchor to the DOT-call method name.
        if (prev != null && (prevType == CrystalTypes.IDENTIFIER || prevType == CrystalTypes.CONSTANT)) {
            reanchorToFollowingDotCall(file, offset, prev)?.let { return it }
        }
        return null
    }

    /**
     * When the quick check anchored an outer identifier (`puts`) but a DOT-call
     * (`Tesa.hika`) follows it before the cursor, re-anchor to the DOT-call's
     * method name so parameter info shows the inner call's signature.
     */
    private fun reanchorToFollowingDotCall(
        file: PsiFile,
        offset: Int,
        outerName: PsiElement,
    ): PsiElement? {
        val outerEnd = outerName.textRange.endOffset
        if (outerEnd >= offset) return null
        val innerName = findInnerDotCallName(file, offset, outerEnd) ?: return null
        // Inner name must run straight to the cursor (only whitespace after it).
        if (!file.text.substring(innerName.textRange.endOffset, offset).isBlank()) {
            return null
        }
        val stmtEnd = CrystalBareCallScanner.findStatementEndOffset(file, offset)
        return CrystalParameterInfoAnchor(file.manager, innerName, stmtEnd)
    }

    /** Method name after the first DOT between [outerEnd] and the cursor. */
    private fun findInnerDotCallName(
        file: PsiFile,
        offset: Int,
        outerEnd: Int,
    ): PsiElement? {
        val between = file.text.substring(outerEnd, offset)
        // A DOT between the outer name and the cursor marks an inner DOT-call.
        val dotIndex = between.indexOf('.')
        if (dotIndex < 0) return null
        val innerNameOffset = outerEnd + dotIndex + 1
        if (innerNameOffset >= offset) return null
        return file.findElementAt(innerNameOffset)?.takeIf(CrystalBareCallScanner::isNameToken)
    }

    /**
     * Fallback B: text-based — find the unmatched LPAREN going backwards and
     * resolve the args through it (or synthesize an anchor when the PSI is broken).
     */
    fun findArgsHolderByUnmatchedLparen(
        file: PsiFile,
        offset: Int,
    ): PsiElement? {
        val text = file.text
        val lparenOffset = CrystalBareCallScanner.findUnmatchedLparen(text, offset)
        if (lparenOffset < 0) return null
        val lparenElement = file.findElementAt(lparenOffset) ?: return null
        CrystalBareCallScanner.findArgsParent(lparenElement)?.let { return it }
        return brokenParenAnchor(file, offset, lparenOffset, lparenElement)
    }

    /** Anchor for a broken paren-call: enclosing parent or a synthetic anchor. */
    private fun brokenParenAnchor(
        file: PsiFile,
        offset: Int,
        lparenOffset: Int,
        lparenElement: PsiElement,
    ): PsiElement? {
        val parent = lparenElement.parent
        if (parent != null && parent !is PsiFile) return parent
        // Parent is PsiFile (broken PSI): create synthetic anchor by finding method name before LPAREN
        val nameToken = CrystalBareCallScanner.findIdentifierBeforeLparen(file, lparenOffset) ?: return null
        val stmtEnd = CrystalBareCallScanner.findStatementEndOffset(file, offset)
        return CrystalParameterInfoAnchor(
            file.manager,
            nameToken,
            stmtEnd,
            lparenOffset,
        )
    }
}
