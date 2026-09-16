// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMethodBody extends PsiElement {

  @Nullable
  CrystalElseClause getElseClause();

  @Nullable
  CrystalEnsureClause getEnsureClause();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  List<CrystalRescueClause> getRescueClauseList();

  @NotNull
  List<CrystalStatementList> getStatementListList();

}
