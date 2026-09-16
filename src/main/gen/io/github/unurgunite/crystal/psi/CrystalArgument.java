// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalArgument extends PsiElement {

  @NotNull
  List<CrystalArgument> getArgumentList();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalFreshVar getFreshVar();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @Nullable
  CrystalTypeReference getTypeReference();

}
