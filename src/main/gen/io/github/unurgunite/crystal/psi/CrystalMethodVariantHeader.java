// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMethodVariantHeader extends PsiElement {

  @NotNull
  CrystalMacroControl getMacroControl();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalTypePath getTypePath();

  @Nullable
  CrystalTypeReference getTypeReference();

}
