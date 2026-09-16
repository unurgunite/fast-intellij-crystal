// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMacroBody extends PsiElement {

  @NotNull
  List<CrystalBareCommandExpression> getBareCommandExpressionList();

  @NotNull
  List<CrystalExpression> getExpressionList();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

}
