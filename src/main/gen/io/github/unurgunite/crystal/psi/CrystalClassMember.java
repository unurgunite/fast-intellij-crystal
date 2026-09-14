// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalClassMember extends PsiElement {

  @Nullable
  CrystalAliasDefinition getAliasDefinition();

  @Nullable
  CrystalAnnotationDefinition getAnnotationDefinition();

  @Nullable
  CrystalAnnotationUsage getAnnotationUsage();

  @Nullable
  CrystalClassDefinition getClassDefinition();

  @Nullable
  CrystalEnumDefinition getEnumDefinition();

  @Nullable
  CrystalExtendStatement getExtendStatement();

  @Nullable
  CrystalIncludeStatement getIncludeStatement();

  @Nullable
  CrystalLibDefinition getLibDefinition();

  @Nullable
  CrystalMacroControl getMacroControl();

  @Nullable
  CrystalMacroDefinition getMacroDefinition();

  @Nullable
  CrystalMethodDefinition getMethodDefinition();

  @Nullable
  CrystalModuleDefinition getModuleDefinition();

  @Nullable
  CrystalPropertyDeclaration getPropertyDeclaration();

  @Nullable
  CrystalPropertyMacro getPropertyMacro();

  @Nullable
  CrystalRecordDefinition getRecordDefinition();

  @Nullable
  CrystalStatement getStatement();

  @Nullable
  CrystalStructDefinition getStructDefinition();

  @Nullable
  CrystalVisibilityModifier getVisibilityModifier();

}
