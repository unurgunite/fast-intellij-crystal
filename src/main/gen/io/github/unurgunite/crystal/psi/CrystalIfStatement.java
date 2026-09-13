// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalIfStatement extends PsiElement {

  @Nullable
  CrystalCondition getCondition();

  @Nullable
  CrystalElseClause getElseClause();

  @NotNull
  List<CrystalElsifClause> getElsifClauseList();

  @Nullable
  CrystalStatementList getStatementList();

}
