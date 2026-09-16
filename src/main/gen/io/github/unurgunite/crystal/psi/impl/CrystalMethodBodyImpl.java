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

public class CrystalMethodBodyImpl extends ASTWrapperPsiElement implements CrystalMethodBody {

  public CrystalMethodBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMethodBody(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalElseClause getElseClause() {
    return PsiTreeUtil.getChildOfType(this, CrystalElseClause.class);
  }

  @Override
  @Nullable
  public CrystalEnsureClause getEnsureClause() {
    return PsiTreeUtil.getChildOfType(this, CrystalEnsureClause.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroControl> getMacroControlList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControl.class);
  }

  @Override
  @NotNull
  public List<CrystalRescueClause> getRescueClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalRescueClause.class);
  }

  @Override
  @NotNull
  public List<CrystalStatementList> getStatementListList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalStatementList.class);
  }

}
