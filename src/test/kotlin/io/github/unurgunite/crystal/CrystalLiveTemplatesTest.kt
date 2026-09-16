package io.github.unurgunite.crystal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards `liveTemplates/Crystal.xml` scoping: every template must be bound to
 * the CRYSTAL context (backed by `CrystalTemplateContextType`) and must not
 * leak into OTHER languages. Regression test — all 21 templates once shipped
 * with `OTHER=true` and zero CRYSTAL bindings.
 */
class CrystalLiveTemplatesTest {
    private fun templates(): List<Pair<String, Map<String, String>>> {
        val stream =
            javaClass.getResourceAsStream("/liveTemplates/Crystal.xml")
                ?: throw AssertionError("liveTemplates/Crystal.xml not on test classpath")
        val doc =
            stream.use {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
            }
        doc.documentElement.normalize()
        val nodes = doc.getElementsByTagName("template")
        return (0 until nodes.length).map { i ->
            val el = nodes.item(i) as org.w3c.dom.Element
            val name = el.getAttribute("name")
            val options = el.getElementsByTagName("option")
            val context =
                (0 until options.length)
                    .map { j -> options.item(j) as org.w3c.dom.Element }
                    .filter { it.parentNode.nodeName == "context" }
                    .associate { it.getAttribute("name") to it.getAttribute("value") }
            name to context
        }
    }

    @Test
    fun `all templates are scoped to CRYSTAL`() {
        val all = templates()
        assertEquals(21, all.size)
        for ((name, context) in all) {
            assertEquals("template '$name' must bind CRYSTAL", "true", context["CRYSTAL"])
        }
    }

    @Test
    fun `no template leaks into OTHER languages`() {
        for ((name, context) in templates()) {
            assertTrue(
                "template '$name' must not enable OTHER (got '${context["OTHER"]}')",
                context["OTHER"] == null || context["OTHER"] == "false",
            )
        }
    }
}
