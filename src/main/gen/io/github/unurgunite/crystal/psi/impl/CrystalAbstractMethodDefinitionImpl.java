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

public class CrystalAbstractMethodDefinitionImpl extends ASTWrapperPsiElement implements CrystalAbstractMethodDefinition {

  public CrystalAbstractMethodDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitAbstractMethodDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalMacroInterpolation getMacroInterpolation() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroInterpolation.class);
  }

  @Override
  @Nullable
  public CrystalParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, CrystalParameterList.class);
  }

  @Override
  @Nullable
  public CrystalTypePath getTypePath() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypePath.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
