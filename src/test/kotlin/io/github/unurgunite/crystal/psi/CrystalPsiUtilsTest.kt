package io.github.unurgunite.crystal.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalPsiUtilsTest : BasePlatformTestCase() {
    fun testIsTypeDefinition() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "class Foo\nend\nmodule Bar\nend\ndef baz\nend\n",
            )
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        val moduleDef = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java)!!
        val methodDef = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)!!
        assertTrue(CrystalPsiUtils.isTypeDefinition(classDef))
        assertTrue(CrystalPsiUtils.isTypeDefinition(moduleDef))
        assertFalse(CrystalPsiUtils.isTypeDefinition(methodDef))
        assertFalse(CrystalPsiUtils.isTypeDefinition(file))
    }

    fun testIsDefinition() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "class Foo\nend\ndef bar(x)\nend\n",
            )
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        val methodDef = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)!!
        val param = PsiTreeUtil.findChildOfType(file, CrystalParameter::class.java)!!
        assertTrue(CrystalPsiUtils.isDefinition(classDef))
        assertTrue(CrystalPsiUtils.isDefinition(methodDef))
        assertTrue(CrystalPsiUtils.isDefinition(param))
        assertFalse(CrystalPsiUtils.isDefinition(file))
    }

    fun testIsScopeBoundary() {
        val file =
            myFixture.configureByText(
                "test.cr",
                "class Foo\nend\nmacro bar\nend\nx = 1\n",
            )
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        val macroDef = PsiTreeUtil.findChildOfType(file, CrystalMacroDefinition::class.java)!!
        assertTrue(CrystalPsiUtils.isScopeBoundary(classDef))
        assertTrue(CrystalPsiUtils.isScopeBoundary(macroDef))
        assertFalse(CrystalPsiUtils.isScopeBoundary(file))
    }

    fun testIsCallExpression() {
        val file = myFixture.configureByText("test.cr", "foo(1)\nbar 2\n")
        val calls =
            PsiTreeUtil.findChildrenOfType(file, CrystalMethodCallExpression::class.java) +
                PsiTreeUtil.findChildrenOfType(file, CrystalBareMethodCallExpression::class.java)
        assertFalse("expected call expressions in fixture", calls.isEmpty())
        for (call in calls) assertTrue(CrystalPsiUtils.isCallExpression(call))
        assertFalse(CrystalPsiUtils.isCallExpression(file))
    }

    fun testFirstSignificantChildSkipsLabelsAndSplats() {
        val file = myFixture.configureByText("test.cr", "foo(name: 1, *rest)\n")
        val argList = PsiTreeUtil.findChildOfType(file, CrystalCallArgs::class.java)!!
        val first = CrystalPsiUtils.firstSignificantChild(argList)
        assertNotNull(first)
    }

    fun testFirstSignificantChildEmpty() {
        val file = myFixture.configureByText("empty.cr", "")
        assertNull(CrystalPsiUtils.firstSignificantChild(file))
    }

    fun testQualifiedNameSimpleClass() {
        val file = myFixture.configureByText("test.cr", "class Foo\nend\n")
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        assertEquals("Foo", CrystalPsiUtils.buildQualifiedName(classDef))
    }

    fun testQualifiedNameNested() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
class Outer
  class Inner
  end
end
                """.trimIndent(),
            )
        val inner =
            PsiTreeUtil
                .findChildrenOfType(file, CrystalClassDefinition::class.java)
                .first { it.name == "Inner" }
        assertEquals("Outer::Inner", CrystalPsiUtils.buildQualifiedName(inner))
    }

    fun testQualifiedNameTripleNesting() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
class A
  class B
    class C
    end
  end
end
                """.trimIndent(),
            )
        val c =
            PsiTreeUtil
                .findChildrenOfType(file, CrystalClassDefinition::class.java)
                .first { it.name == "C" }
        assertEquals("A::B::C", CrystalPsiUtils.buildQualifiedName(c))
    }

    fun testQualifiedNameModuleMixed() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
module Foo
  class Bar
  end
end
                """.trimIndent(),
            )
        val bar = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)!!
        assertEquals("Foo::Bar", CrystalPsiUtils.buildQualifiedName(bar))
    }

    fun testQualifiedNameNonTypeReturnsNull() {
        val file = myFixture.configureByText("test.cr", "x = 1\n")
        val element = file.findElementAt(0)!!
        assertNull(CrystalPsiUtils.buildQualifiedName(element))
    }

    fun testGetEnclosingType() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
class Foo
  def m
    x = 1
  end
end
                """.trimIndent(),
            )
        val x = file.findElementAt(file.text.indexOf("x = 1"))!!
        val enclosing = CrystalPsiUtils.getEnclosingType(x)
        assertTrue(enclosing is CrystalClassDefinition)
        assertEquals("Foo", (enclosing as CrystalClassDefinition).name)
    }

    fun testGetEnclosingTypeTopLevelReturnsNull() {
        val file = myFixture.configureByText("test.cr", "x = 1\n")
        assertNull(CrystalPsiUtils.getEnclosingType(file.findElementAt(0)!!))
    }

    fun testBuildNamespacePath() {
        val file = myFixture.configureByText("test.cr", "Foo::Sub.space\n")
        val accesses = PsiTreeUtil.findChildrenOfType(file, CrystalNamespaceAccess::class.java).toList()
        assertFalse("Expected namespace_access elements", accesses.isEmpty())
        val last = accesses.last()
        assertEquals("Foo::Sub", CrystalPsiUtils.buildNamespacePath(last))
    }
}

class CrystalPsiFactoryTest : BasePlatformTestCase() {
    fun testCreateIdentifierLeaf() {
        val node = createLeafFromText(project, "my_var", CrystalTypes.IDENTIFIER)
        assertNotNull(node)
        assertEquals("my_var", node!!.text)
        assertEquals(CrystalTypes.IDENTIFIER, node.elementType)
    }

    fun testCreateInstanceVarLeaf() {
        val node = createLeafFromText(project, "@pump", CrystalTypes.INSTANCE_VAR)
        assertNotNull(node)
        assertEquals("@pump", node!!.text)
    }

    fun testCreateConstantLeaf() {
        val node = createLeafFromText(project, "MyClass", CrystalTypes.CONSTANT)
        assertNotNull(node)
        assertEquals("MyClass", node!!.text)
    }

    fun testMissingTokenTypeReturnsNull() {
        assertNull(createLeafFromText(project, "plain_ident", CrystalTypes.DEF))
    }
}
