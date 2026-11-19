package io.github.unurgunite.crystal.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import io.github.unurgunite.crystal.lexer.CrystalTokenTypes
import io.github.unurgunite.crystal.psi.CrystalTypes

/**
 * Parser base for external grammar rules that need more than a token check.
 *
 * GrammarKit static-imports this class into the generated parser and takes the
 * parser helpers (`enter_section_`, `recursion_guard_`, ...) from it.
 */
abstract class CrystalParserUtil : GeneratedParserUtilBase() {
    companion object {
        /**
         * Succeeds only when the current `{{` token touches the previous token with no
         * whitespace. Crystal splices a method name only when the parts are glued
         * (`build_{{name}}2`); a spaced `{{...}}` after a method name is an ordinary
         * argument, so `obj.new {{x}}, 1` must not read `new {{x}}` as the callee.
         * GrammarKit skips whitespace between rules, so no BNF guard can see the gap;
         * the raw token stream can.
         */
        @JvmStatic
        fun parseGluedInterpolation(
            builder: PsiBuilder,
            level: Int,
        ): Boolean {
            if (!recursion_guard_(builder, level, "parseGluedInterpolation")) return false
            if (builder.rawLookup(0) != CrystalTypes.MACRO_INTERPOLATION_BEGIN) return false
            return builder.rawLookup(-1) != CrystalTokenTypes.WHITE_SPACE
        }
    }
}
