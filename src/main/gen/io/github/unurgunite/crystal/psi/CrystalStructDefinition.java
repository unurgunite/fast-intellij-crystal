// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import io.github.unurgunite.crystal.stubs.CrystalStructDefinitionStub;

public interface CrystalStructDefinition extends CrystalNamedElement, StubBasedPsiElement<CrystalStructDefinitionStub> {

  @Nullable
  CrystalClassBody getClassBody();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @Nullable
  CrystalSuperclassClause getSuperclassClause();

  @Nullable
  CrystalTypeParameters getTypeParameters();

}
