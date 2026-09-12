// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import io.github.unurgunite.crystal.stubs.CrystalClassDefinitionStub;

public interface CrystalClassDefinition extends CrystalNamedElement, StubBasedPsiElement<CrystalClassDefinitionStub> {

  @Nullable
  CrystalClassBody getClassBody();

  @Nullable
  CrystalSuperclassClause getSuperclassClause();

  @Nullable
  CrystalTypeParameters getTypeParameters();

}
