// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalArrayLiteral extends PsiElement {

  @Nullable
  CrystalArrayOfTail getArrayOfTail();

  @Nullable
  CrystalExpressionList getExpressionList();

  @NotNull
  List<CrystalTypeReference> getTypeReferenceList();

}
