// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static io.github.unurgunite.crystal.psi.CrystalTypes.*;
import io.github.unurgunite.crystal.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import io.github.unurgunite.crystal.stubs.CrystalMacroDefinitionStub;

public class CrystalMacroDefinitionImpl extends CrystalStubbedMacroDefinitionImpl implements CrystalMacroDefinition {

  public CrystalMacroDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalMacroDefinitionImpl(CrystalMacroDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMacroDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalMacroBody getMacroBody() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroBody.class);
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

}
