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

public class CrystalArrayLiteralImpl extends ASTWrapperPsiElement implements CrystalArrayLiteral {

  public CrystalArrayLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitArrayLiteral(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalArrayOfTail getArrayOfTail() {
    return PsiTreeUtil.getChildOfType(this, CrystalArrayOfTail.class);
  }

  @Override
  @Nullable
  public CrystalExpressionList getExpressionList() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpressionList.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeReference> getTypeReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReference.class);
  }

}
