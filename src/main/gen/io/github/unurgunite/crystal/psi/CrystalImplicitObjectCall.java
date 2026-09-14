// This is a generated file. Not intended for manual editing.
package io.github.unurgunite.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalImplicitObjectCall extends PsiElement {

  @NotNull
  List<CrystalArgument> getArgumentList();

  @NotNull
  List<CrystalArrayLiteral> getArrayLiteralList();

  @NotNull
  List<CrystalAsmExpression> getAsmExpressionList();

  @NotNull
  List<CrystalAssignment> getAssignmentList();

  @Nullable
  CrystalBareArgumentList getBareArgumentList();

  @NotNull
  List<CrystalBareCommandExpression> getBareCommandExpressionList();

  @NotNull
  List<CrystalBeginStatement> getBeginStatementList();

  @Nullable
  CrystalBlock getBlock();

  @NotNull
  List<CrystalBreakExpression> getBreakExpressionList();

  @Nullable
  CrystalCallArgs getCallArgs();

  @NotNull
  List<CrystalCaseStatement> getCaseStatementList();

  @NotNull
  List<CrystalClassVarAccess> getClassVarAccessList();

  @NotNull
  List<CrystalCommandExpression> getCommandExpressionList();

  @NotNull
  List<CrystalDotCallAccess> getDotCallAccessList();

  @NotNull
  List<CrystalExpression> getExpressionList();

  @NotNull
  List<CrystalGroupedExpression> getGroupedExpressionList();

  @NotNull
  List<CrystalHashLiteral> getHashLiteralList();

  @NotNull
  List<CrystalHeredocLiteral> getHeredocLiteralList();

  @NotNull
  List<CrystalIfStatement> getIfStatementList();

  @NotNull
  List<CrystalImplicitObjectCall> getImplicitObjectCallList();

  @NotNull
  List<CrystalInstanceSizeofExpression> getInstanceSizeofExpressionList();

  @NotNull
  List<CrystalInstanceVarAccess> getInstanceVarAccessList();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @NotNull
  List<CrystalMethodCallExpression> getMethodCallExpressionList();

  @NotNull
  List<CrystalNamespaceAccess> getNamespaceAccessList();

  @NotNull
  List<CrystalNextExpression> getNextExpressionList();

  @NotNull
  List<CrystalOffsetofExpression> getOffsetofExpressionList();

  @NotNull
  List<CrystalPercentLiteral> getPercentLiteralList();

  @NotNull
  List<CrystalPointerofExpression> getPointerofExpressionList();

  @NotNull
  List<CrystalProcLiteral> getProcLiteralList();

  @NotNull
  List<CrystalRegexExpression> getRegexExpressionList();

  @NotNull
  List<CrystalReturnExpression> getReturnExpressionList();

  @NotNull
  List<CrystalSizeofExpression> getSizeofExpressionList();

  @NotNull
  List<CrystalStringExpression> getStringExpressionList();

  @NotNull
  List<CrystalSymbolStringExpression> getSymbolStringExpressionList();

  @NotNull
  List<CrystalTupleLiteral> getTupleLiteralList();

  @NotNull
  List<CrystalTypeReceiverExpression> getTypeReceiverExpressionList();

  @Nullable
  CrystalTypeReference getTypeReference();

  @NotNull
  List<CrystalTypeofExpression> getTypeofExpressionList();

  @NotNull
  List<CrystalUninitializedExpression> getUninitializedExpressionList();

  @NotNull
  List<CrystalUnlessStatement> getUnlessStatementList();

  @NotNull
  List<CrystalVariableReference> getVariableReferenceList();

  @NotNull
  List<CrystalYieldExpression> getYieldExpressionList();

}
