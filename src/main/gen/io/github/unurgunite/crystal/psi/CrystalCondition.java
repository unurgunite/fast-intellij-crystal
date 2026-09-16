// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalCondition extends PsiElement {

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @NotNull
  CrystalExpression getExpression();

  @Nullable
  CrystalFreshVar getFreshVar();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

}
