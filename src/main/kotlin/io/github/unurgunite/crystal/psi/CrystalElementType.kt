package io.github.unurgunite.crystal.psi

import com.intellij.psi.tree.IElementType
import io.github.unurgunite.crystal.CrystalLanguage

class CrystalElementType(
    debugName: String,
) : IElementType(debugName, CrystalLanguage) {
    override fun toString(): String = "CrystalElementType.${super.toString()}"
}
