// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import io.github.unurgunite.crystal.stubs.CrystalEnumDefinitionStub;

public interface CrystalEnumDefinition extends CrystalNamedElement, StubBasedPsiElement<CrystalEnumDefinitionStub> {

  @Nullable
  CrystalEnumBody getEnumBody();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @Nullable
  CrystalTypeReference getTypeReference();

}
