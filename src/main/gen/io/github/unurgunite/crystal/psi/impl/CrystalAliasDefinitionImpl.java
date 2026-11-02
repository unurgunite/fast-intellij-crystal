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
import io.github.unurgunite.crystal.stubs.CrystalAliasDefinitionStub;

public class CrystalAliasDefinitionImpl extends CrystalStubbedAliasDefinitionImpl implements CrystalAliasDefinition {

  public CrystalAliasDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalAliasDefinitionImpl(CrystalAliasDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitAliasDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeReference> getTypeReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReference.class);
  }

}
