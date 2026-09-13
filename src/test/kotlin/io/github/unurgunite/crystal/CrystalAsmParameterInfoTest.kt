package io.github.unurgunite.crystal

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.unurgunite.crystal.navigation.CrystalAsmParameterInfoHandler
import io.github.unurgunite.crystal.psi.CrystalAsmExpression
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class CrystalAsmParameterInfoTest : BasePlatformTestCase() {

    private val handler = CrystalAsmParameterInfoHandler()

    /** Records setter calls, serves stubbed getters for parameter-info contexts. */
    private class ContextProxy(
        private val values: Map<String, Any?>,
    ) : InvocationHandler {
        val calls = mutableListOf<Pair<String, Array<out Any?>>>()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val name = method.name
            if (name.startsWith("set") || name.startsWith("show") || name == "setupUIComponentPresentation") {
                calls.add(name to (args ?: emptyArray()))
                return defaultFor(method.returnType)
            }
            if (values.containsKey(name)) return values[name]
            if (name == "toString") return "ContextProxy"
            if (name == "hashCode") return System.identityHashCode(proxy)
            if (name == "equals") return proxy === args?.firstOrNull()
            return defaultFor(method.returnType)
        }

        private fun defaultFor(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            Void.TYPE -> null
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> context(iface: Class<T>, values: Map<String, Any?>): Pair<T, ContextProxy> {
        val proxy = ContextProxy(values)
        val instance = Proxy.newProxyInstance(
            iface.classLoader, arrayOf(iface), proxy
        ) as T
        return instance to proxy
    }

    private fun ownerAtCaret(): CrystalAsmExpression {
        val file = myFixture.file
        val offset = myFixture.editor.caretModel.offset
        val (context, _) = context(
            CreateParameterInfoContext::class.java,
            mapOf("getFile" to file, "getOffset" to offset, "getProject" to project)
        )
        val found = handler.findElementForParameterInfo(context)
        assertNotNull("Handler should find asm expression at caret", found)
        return found!!
    }

    private fun sectionAtCaret(): Int {
        val owner = ownerAtCaret()
        val (context, proxy) = context(
            UpdateParameterInfoContext::class.java,
            mapOf(
                "getFile" to myFixture.file,
                "getOffset" to myFixture.editor.caretModel.offset,
                "getProject" to project
            )
        )
        handler.updateParameterInfo(owner, context)
        val setCall = proxy.calls.firstOrNull { it.first == "setCurrentParameter" }
        assertNotNull("Handler should call setCurrentParameter", setCall)
        return setCall!!.second.firstOrNull() as? Int
            ?: throw AssertionError("setCurrentParameter should receive an Int")
    }

    fun testAsmSectionDetectionTemplate() {
        // Cursor inside the template string → section 0
        myFixture.configureByText("test.cr", "asm(\"no<caret>p\")")
        assertEquals(0, sectionAtCaret())
    }

    fun testAsmSectionDetectionOutputs() {
        // Cursor after first colon → section 1 (outputs)
        myFixture.configureByText("test.cr", "asm(\"rdtsc\" : <caret>\"=a\"(low))")
        assertEquals(1, sectionAtCaret())
    }

    fun testAsmSectionDetectionInputs() {
        // Cursor after second colon → section 2 (inputs)
        myFixture.configureByText("test.cr", "asm(\"addl\" : \"=r\"(result) : <caret>\"0\"(a))")
        assertEquals(2, sectionAtCaret())
    }

    fun testAsmSectionDetectionClobbers() {
        // Cursor after third colon → section 3 (clobbers)
        myFixture.configureByText("test.cr", "asm(\"addl\" : \"=r\"(result) : \"0\"(a) : <caret>\"cc\")")
        assertEquals(3, sectionAtCaret())
    }

    fun testFindElementExposesSingleItem() {
        myFixture.configureByText("test.cr", "asm(\"no<caret>p\")")
        val file = myFixture.file
        val offset = myFixture.editor.caretModel.offset
        val (context, proxy) = context(
            CreateParameterInfoContext::class.java,
            mapOf("getFile" to file, "getOffset" to offset, "getProject" to project)
        )
        val owner = handler.findElementForParameterInfo(context)
        assertNotNull("Should find asm expression", owner)
        val itemsCall = proxy.calls.firstOrNull { it.first == "setItemsToShow" }
        assertNotNull("Handler should expose items via setItemsToShow", itemsCall)
        val items = itemsCall!!.second.firstOrNull() as? Array<*>
        assertNotNull("setItemsToShow should receive an array", items)
        assertEquals(1, items!!.size)
        assertTrue(items[0] is CrystalAsmParameterInfoHandler.AsmInfo)
    }

    fun testUpdateUIHighlightsCurrentSection() {
        val info = CrystalAsmParameterInfoHandler.AsmInfo()
        val (context, proxy) = context(
            ParameterInfoUIContext::class.java,
            mapOf("getCurrentParameterIndex" to 1, "getDefaultParameterColor" to null)
        )
        handler.updateUI(info, context)
        val setup = proxy.calls.firstOrNull { it.first == "setupUIComponentPresentation" }
        assertNotNull("Handler should set up UI presentation", setup)
        val args = setup!!.second
        val text = args[0] as? String
        assertNotNull("First arg should be text", text)
        assertEquals("template : outputs : inputs : clobbers : options", text)
        val start = args[1] as? Int
        assertNotNull("Second arg should be highlight start", start)
        val end = args[2] as? Int
        assertNotNull("Third arg should be highlight end", end)
        // "template".length + " : ".length = 8 + 3 = 11 → "outputs" spans 11..18
        assertEquals(11, start)
        assertEquals(18, end)
        assertEquals("outputs", text!!.substring(start!!, end!!))
    }

    fun testUpdateUIDisablesOnNullInfo() {
        val (context, proxy) = context(
            ParameterInfoUIContext::class.java,
            mapOf("getCurrentParameterIndex" to 0)
        )
        handler.updateUI(null, context)
        val disable = proxy.calls.firstOrNull { it.first == "setUIComponentEnabled" }
        assertNotNull("Handler should disable UI component on null info", disable)
        assertEquals(false, disable!!.second.firstOrNull())
    }
}
