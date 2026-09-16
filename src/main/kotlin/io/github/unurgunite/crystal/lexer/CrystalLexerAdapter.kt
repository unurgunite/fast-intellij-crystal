package io.github.unurgunite.crystal.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.FlexLexer

/**
 * Lexer adapter that encodes interpolationDepth into the lexer state.
 * This ensures IntelliJ's incremental re-lexing correctly restores the
 * interpolation nesting level when starting mid-file.
 *
 * State encoding: lower 16 bits = JFlex yystate, upper 16 bits = interpolationDepth
 */
class CrystalLexerAdapter : FlexAdapter(CrystalLexer(null)) {
    companion object {
        // State encoding masks (see class KDoc): JFlex state in the low half,
        // interpolation depth in the high half.
        private const val STATE_MASK = 0xFFFF
        private const val DEPTH_SHIFT = 16
    }

    override fun getState(): Int {
        val flexLexer = flex as CrystalLexer
        val baseState = super.getState() and STATE_MASK
        val depth = flexLexer.interpolationDepth
        return baseState or (depth shl DEPTH_SHIFT)
    }

    override fun start(
        buffer: CharSequence,
        startOffset: Int,
        endOffset: Int,
        initialState: Int,
    ) {
        val flexLexer = flex as CrystalLexer
        // Fresh lex from the start: reset ALL mutable lexer fields. JFlex's
        // zzResetReader does not touch user fields, and FlexAdapter reuses one
        // instance across files — stale `lastSignificantToken` / `stateStack` /
        // `afterDef` etc. leaked parse-affecting state between files (verified
        // 2026-09-14: golden failures depended on suite execution order).
        // Mid-file re-lex (startOffset > 0) keeps the stacks: incremental
        // highlighting re-enters nested states with only the encoded depth.
        if (startOffset == 0) {
            flexLexer.resetState()
        }
        val baseState = initialState and STATE_MASK
        val depth = initialState ushr DEPTH_SHIFT
        super.start(buffer, startOffset, endOffset, baseState)
        flexLexer.interpolationDepth = depth
    }
}
