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

public class CrystalRecordDefinitionImpl extends ASTWrapperPsiElement implements CrystalRecordDefinition {

  public CrystalRecordDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitRecordDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalClassBody getClassBody() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassBody.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public List<CrystalRecordField> getRecordFieldList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalRecordField.class);
  }

  @Override
  @Nullable
  public CrystalTypeParameters getTypeParameters() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeParameters.class);
  }

}
