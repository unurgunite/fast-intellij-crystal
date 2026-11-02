package io.github.unurgunite.crystal.inspections

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile

/**
 * True when [file] is a real Crystal document rather than a fragment injected
 * into another language (Markdown READMEs, doc comments). Linting such code
 * blocks produces reports the user cannot act on, in files they never opened.
 */
internal fun isAnalyzableCrystalFile(file: PsiFile): Boolean = !InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)
