package io.github.unurgunite.crystal.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.navigation.parameterinfo.CrystalParameterInfoHandler
import io.github.unurgunite.crystal.navigation.parameterinfo.CrystalParameterInfoMethodName
import io.github.unurgunite.crystal.navigation.parameterinfo.CrystalParameterInfoReceiver
import io.github.unurgunite.crystal.type.CrystalMethodLookup

class CrystalParameterInfoDotCallTest : BasePlatformTestCase() {
    private val handler = CrystalParameterInfoHandler()

    override fun getTestDataPath(): String = "src/test/testData"

    fun testDotCallFindArgs() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                  def bar(x, y)
                  end
                end
                f = Foo.new
                f.bar(<caret>1, 2)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args in dot-call", argsHolder)
    }

    fun testDotCallMethodName() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                  def bar(x, y)
                  end
                end
                f = Foo.new
                f.bar(<caret>1, 2)
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("bar", name)
    }

    fun testClassMethodCallName() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Foo
                  def self.create(name)
                  end
                end
                Foo.create(<caret>"test")
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull(argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("create", name)
    }

    // ==================== Bare-Call Backtracking Tests (NEW) ====================

    fun testEnvFetchDoesNotShowUnrelatedFetchParams() {
        myFixture.configureByText(
            "fetch_source.cr",
            """
            class HttpClient
              def fetch(url : String, timeout : Int32 = 30, &block : String ->)
              end
            end
            """.trimIndent(),
        )
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                ENV.fetch(<caret>"HOME")
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for ENV.fetch", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("fetch", name)
        val receiverName = CrystalParameterInfoReceiver.findReceiverNameFromSiblings(argsHolder)
        assertEquals("ENV", receiverName)
    }

    fun testClassNewParameterInfo() {
        myFixture.configureByText(
            "test.cr",
            """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end
            Apfelsaft.new(<caret>"hi", 1)
            """.trimIndent(),
        )
        val file = myFixture.file
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Class.new(...)", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("new", name)

        // Check that getInitializeMethod resolves the class
        val project = myFixture.project
        val initMethod =
            io.github.unurgunite.crystal.type.CrystalMethodLookup.getInitializeMethod(
                "Apfelsaft",
                project,
                argsHolder.containingFile,
            )
        assertNotNull("Should find initialize method for Apfelsaft", initMethod)
        val params = initMethod!!.parameterList?.parameterList ?: emptyList()
        assertEquals("initialize should have 2 params", 2, params.size)
    }

    fun testBareNewWithoutInitialize() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
            end
            a = Foo.new <caret>
            """.trimIndent(),
        )
        val file = myFixture.file
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for bare Foo.new", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("new", name)

        // The early .new short-circuit should resolve to initialize via getInitializeMethod
        // For Foo without initialize, it should return null (no stdlib "new" methods loaded)
        val initMethod =
            io.github.unurgunite.crystal.type.CrystalMethodLookup.getInitializeMethod(
                "Foo",
                myFixture.project,
                argsHolder.containingFile,
            )
        assertNull("Should not find initialize for Foo without one", initMethod)
    }

    fun testBareNewWithInitialize() {
        myFixture.configureByText(
            "test.cr",
            """
            class Foo
              def initialize(name : String, count : Int32)
              end
            end
            a = Foo.new <caret>
            """.trimIndent(),
        )
        val file = myFixture.file
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for bare Foo.new", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("new", name)

        // Should resolve to Foo's initialize method directly
        val initMethod =
            io.github.unurgunite.crystal.type.CrystalMethodLookup.getInitializeMethod(
                "Foo",
                myFixture.project,
                argsHolder.containingFile,
            )
        assertNotNull("Should find initialize method for Foo", initMethod)
        val params = initMethod!!.parameterList?.parameterList ?: emptyList()
        assertEquals("initialize should have 2 params", 2, params.size)
    }

    // ==================== DOT-call after method call (regression tests) ====================

    fun testFindArgsDotCallAfterMethodCallWithSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def puts(x)
                end
                class Tesa
                  def self.hika(name : String)
                  end
                end
                puts Tesa.hika <caret>"test"
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Tesa.hika after puts", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("hika", name)
    }

    fun testFindArgsDotCallAfterMethodCallNoSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def puts(x)
                end
                class Tesa
                  def self.hika(name : String)
                  end
                end
                puts Tesa.hika<caret>
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Tesa.hika after puts (no space)", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("hika", name)
    }

    fun testFindArgsNamespaceDotCallAfterMethodCallWithSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def puts(x)
                end
                class RvmCli
                  class Tools
                    def self.hello(name : String)
                    end
                  end
                end
                puts RvmCli::Tools.hello <caret>"test"
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for RvmCli::Tools.hello after puts", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("hello", name)
    }

    fun testFindArgsDotCallAloneWithSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                class Tesa
                  def self.hika(name : String)
                  end
                end
                Tesa.hika <caret>"test"
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for Tesa.hika (no puts, with space)", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("hika", name)
    }

    fun testFindArgsBareCallAfterMethodCallWithSpace() {
        val file =
            myFixture.configureByText(
                "test.cr",
                """
                def puts(x)
                end
                def greet(name : String)
                end
                puts greet <caret>"test"
                """.trimIndent(),
            )
        val argsHolder = handler.findArgsHolder(file, myFixture.caretOffset)
        assertNotNull("Should find args holder for greet after puts", argsHolder)
        val name = CrystalParameterInfoMethodName.findMethodNameForArgs(argsHolder!!)
        assertEquals("greet", name)
    }
}
