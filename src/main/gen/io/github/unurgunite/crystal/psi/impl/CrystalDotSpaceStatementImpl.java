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

public class CrystalDotSpaceStatementImpl extends ASTWrapperPsiElement implements CrystalDotSpaceStatement {

  public CrystalDotSpaceStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitDotSpaceStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public CrystalBareArgumentList getBareArgumentList() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalBareArgumentList.class));
  }

  @Override
  @Nullable
  public CrystalBlock getBlock() {
    return PsiTreeUtil.getChildOfType(this, CrystalBlock.class);
  }

  @Override
  @Nullable
  public CrystalMacroInterpolation getMacroInterpolation() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public CrystalVariableReference getVariableReference() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalVariableReference.class));
  }

}
