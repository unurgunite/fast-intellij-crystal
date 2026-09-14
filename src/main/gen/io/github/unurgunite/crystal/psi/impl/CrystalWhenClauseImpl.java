// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static io.github.unurgunite.crystal.psi.CrystalTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import io.github.unurgunite.crystal.psi.*;

public class CrystalWhenClauseImpl extends ASTWrapperPsiElement implements CrystalWhenClause {

  public CrystalWhenClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitWhenClause(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalBareArgumentList> getBareArgumentListList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalBareArgumentList.class);
  }

  @Override
  @NotNull
  public List<CrystalCallArgs> getCallArgsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalCallArgs.class);
  }

  @Override
  @NotNull
  public List<CrystalClassVarAccess> getClassVarAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalClassVarAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalInstanceVarAccess> getInstanceVarAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalInstanceVarAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public List<CrystalRegexExpression> getRegexExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalRegexExpression.class);
  }

  @Override
  @NotNull
  public CrystalStatementList getStatementList() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalStatementList.class));
  }

}
