package io.github.unurgunite.crystal.highlighting

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.tree.IElementType
import io.github.unurgunite.crystal.lexer.CrystalLexerAdapter
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalTodoIndexer : LexerBasedTodoIndexer() {
    override fun createLexer(consumer: OccurrenceConsumer): Lexer = CrystalTodoFilterLexer(CrystalCapLexer(CrystalLexerAdapter()), consumer)
}

private class CrystalTodoFilterLexer(
    lexer: Lexer,
    consumer: OccurrenceConsumer,
) : BaseFilterLexer(lexer, consumer) {
    override fun advance() {
        val tokenType = delegate.tokenType
        if (tokenType == CrystalTypes.LINE_COMMENT) {
            addOccurrenceInToken(UsageSearchContext.IN_COMMENTS.toInt())
            advanceTodoItemCountsInToken()
        }
        delegate.advance()
    }
}

/**
 * Wraps the Crystal lexer and stops it after a generous token budget
 * (one token per source character) so a single pathological file cannot
 * stall the entire indexing pass.
 */
private class CrystalCapLexer(
    private val delegate: Lexer,
) : Lexer() {
    private var cap = Long.MAX_VALUE
    private var count = 0

    override fun start(
        buffer: CharSequence,
        startOffset: Int,
        endOffset: Int,
        initialState: Int,
    ) {
        delegate.start(buffer, startOffset, endOffset, initialState)
        cap = (endOffset - startOffset).toLong() + 1
    }

    override fun advance() {
        if (count++ > cap) return
        delegate.advance()
    }

    override fun getTokenType(): IElementType? {
        if (count > cap) return null
        return delegate.tokenType
    }

    override fun getTokenStart(): Int = delegate.tokenStart

    override fun getTokenEnd(): Int = delegate.tokenEnd

    override fun getState(): Int = delegate.state

    override fun getBufferSequence(): CharSequence = delegate.bufferSequence

    override fun getBufferEnd(): Int = delegate.bufferEnd

    override fun getCurrentPosition(): LexerPosition = delegate.currentPosition

    override fun restore(position: LexerPosition) = delegate.restore(position)
}
