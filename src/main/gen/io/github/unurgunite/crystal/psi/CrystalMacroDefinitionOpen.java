// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import io.github.unurgunite.crystal.stubs.CrystalMacroDefinitionStub;

public interface CrystalMacroDefinitionOpen extends CrystalMacroDefinition, StubBasedPsiElement<CrystalMacroDefinitionStub> {

  @Nullable
  CrystalMacroBody getMacroBody();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalTypePath getTypePath();

}
