// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalDotSpaceStatement extends PsiElement {

  @NotNull
  CrystalBareArgumentList getBareArgumentList();

  @Nullable
  CrystalBlock getBlock();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @NotNull
  CrystalVariableReference getVariableReference();

}
