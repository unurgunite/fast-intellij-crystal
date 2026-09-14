// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalInClause extends PsiElement {

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalExpressionList getExpressionList();

  @NotNull
  CrystalStatementList getStatementList();

  @Nullable
  CrystalTupleLiteral getTupleLiteral();

}
