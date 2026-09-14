// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalWhenClause extends PsiElement {

  @NotNull
  List<CrystalBareArgumentList> getBareArgumentListList();

  @NotNull
  List<CrystalCallArgs> getCallArgsList();

  @NotNull
  List<CrystalClassVarAccess> getClassVarAccessList();

  @NotNull
  List<CrystalExpression> getExpressionList();

  @NotNull
  List<CrystalInstanceVarAccess> getInstanceVarAccessList();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @NotNull
  List<CrystalRegexExpression> getRegexExpressionList();

  @NotNull
  CrystalStatementList getStatementList();

}
