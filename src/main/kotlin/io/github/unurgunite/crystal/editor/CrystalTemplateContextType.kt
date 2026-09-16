package io.github.unurgunite.crystal

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

class CrystalTemplateContextType : TemplateContextType("Crystal") {
    override fun isInContext(context: TemplateActionContext): Boolean = context.file.name.endsWith(".cr")
}
