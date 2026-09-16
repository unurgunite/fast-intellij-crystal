// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalEnumBody extends PsiElement {

  @NotNull
  List<CrystalAnnotationUsage> getAnnotationUsageList();

  @NotNull
  List<CrystalClassVarAccess> getClassVarAccessList();

  @NotNull
  List<CrystalEnumConstant> getEnumConstantList();

  @NotNull
  List<CrystalExpression> getExpressionList();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  List<CrystalMethodDefinition> getMethodDefinitionList();

  @NotNull
  List<CrystalVisibilityModifier> getVisibilityModifierList();

}
