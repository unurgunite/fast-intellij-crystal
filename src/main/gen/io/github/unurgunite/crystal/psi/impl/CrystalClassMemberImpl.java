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

public class CrystalClassMemberImpl extends ASTWrapperPsiElement implements CrystalClassMember {

  public CrystalClassMemberImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitClassMember(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalAliasDefinition getAliasDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalAliasDefinition.class);
  }

  @Override
  @Nullable
  public CrystalAnnotationDefinition getAnnotationDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalAnnotationDefinition.class);
  }

  @Override
  @Nullable
  public CrystalAnnotationUsage getAnnotationUsage() {
    return PsiTreeUtil.getChildOfType(this, CrystalAnnotationUsage.class);
  }

  @Override
  @Nullable
  public CrystalClassDefinition getClassDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassDefinition.class);
  }

  @Override
  @Nullable
  public CrystalEnumDefinition getEnumDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalEnumDefinition.class);
  }

  @Override
  @Nullable
  public CrystalExtendStatement getExtendStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalExtendStatement.class);
  }

  @Override
  @Nullable
  public CrystalIncludeStatement getIncludeStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalIncludeStatement.class);
  }

  @Override
  @Nullable
  public CrystalLibDefinition getLibDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalLibDefinition.class);
  }

  @Override
  @Nullable
  public CrystalMacroControl getMacroControl() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroControl.class);
  }

  @Override
  @Nullable
  public CrystalMacroDefinition getMacroDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroDefinition.class);
  }

  @Override
  @Nullable
  public CrystalMethodDefinition getMethodDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalMethodDefinition.class);
  }

  @Override
  @Nullable
  public CrystalModuleDefinition getModuleDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalModuleDefinition.class);
  }

  @Override
  @Nullable
  public CrystalPropertyDeclaration getPropertyDeclaration() {
    return PsiTreeUtil.getChildOfType(this, CrystalPropertyDeclaration.class);
  }

  @Override
  @Nullable
  public CrystalPropertyMacro getPropertyMacro() {
    return PsiTreeUtil.getChildOfType(this, CrystalPropertyMacro.class);
  }

  @Override
  @Nullable
  public CrystalRecordDefinition getRecordDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalRecordDefinition.class);
  }

  @Override
  @Nullable
  public CrystalStatement getStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalStatement.class);
  }

  @Override
  @Nullable
  public CrystalStructDefinition getStructDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalStructDefinition.class);
  }

  @Override
  @Nullable
  public CrystalVisibilityModifier getVisibilityModifier() {
    return PsiTreeUtil.getChildOfType(this, CrystalVisibilityModifier.class);
  }

}
