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
        val baseState = initialState and STATE_MASK
        val depth = initialState ushr DEPTH_SHIFT
        super.start(buffer, startOffset, endOffset, baseState)
        (flex as CrystalLexer).interpolationDepth = depth
    }
}
