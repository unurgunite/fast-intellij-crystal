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

public class CrystalInClauseImpl extends ASTWrapperPsiElement implements CrystalInClause {

  public CrystalInClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitInClause(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalBareArgumentList getBareArgumentList() {
    return PsiTreeUtil.getChildOfType(this, CrystalBareArgumentList.class);
  }

  @Override
  @Nullable
  public CrystalCallArgs getCallArgs() {
    return PsiTreeUtil.getChildOfType(this, CrystalCallArgs.class);
  }

  @Override
  @Nullable
  public CrystalExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpression.class);
  }

  @Override
  @Nullable
  public CrystalExpressionList getExpressionList() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpressionList.class);
  }

  @Override
  @Nullable
  public CrystalMacroInterpolation getMacroInterpolation() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public CrystalStatementList getStatementList() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalStatementList.class));
  }

  @Override
  @Nullable
  public CrystalTupleLiteral getTupleLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalTupleLiteral.class);
  }

}
