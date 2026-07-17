// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import io.github.unurgunite.crystal.stubs.CrystalMethodDefinitionStub;

public interface CrystalMethodDefinition extends CrystalNamedElement, StubBasedPsiElement<CrystalMethodDefinitionStub> {

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @Nullable
  CrystalMethodBody getMethodBody();

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalTypePath getTypePath();

  @Nullable
  CrystalTypeReference getTypeReference();

}
