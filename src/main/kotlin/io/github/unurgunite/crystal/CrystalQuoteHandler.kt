package io.github.unurgunite.crystal

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import io.github.unurgunite.crystal.psi.CrystalTypes

class CrystalQuoteHandler :
    SimpleTokenSetQuoteHandler(
        CrystalTypes.STRING_LITERAL,
        CrystalTypes.CHAR_LITERAL,
    )
