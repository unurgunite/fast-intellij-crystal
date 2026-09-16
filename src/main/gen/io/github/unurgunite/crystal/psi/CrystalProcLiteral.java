// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalProcLiteral extends PsiElement {

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalStatementList getStatementList();

  @NotNull
  List<CrystalTypeReference> getTypeReferenceList();

}
