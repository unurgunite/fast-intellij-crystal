// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalArgument extends PsiElement {

  @NotNull
  List<CrystalArgument> getArgumentList();

  @Nullable
  CrystalArrayLiteral getArrayLiteral();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalFreshVar getFreshVar();

  @Nullable
  CrystalHashLiteral getHashLiteral();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @Nullable
  CrystalTupleLiteral getTupleLiteral();

  @NotNull
  List<CrystalTypeReference> getTypeReferenceList();

}
