package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Every inspection needs `inspectionDescriptions/<shortName>.html`, otherwise
 * the platform logs `PluginException: Inspection #X has no description` and the
 * Settings pane shows an empty description.
 */
class CrystalInspectionDescriptionsTest : BasePlatformTestCase() {
    private val inspections =
        listOf(
            "CrystalArgumentCount",
            "CrystalColonSpacing",
            "CrystalEmptyCollection",
            "CrystalInstanceVarType",
            "CrystalLibFunParameterType",
            "CrystalSingleQuoteString",
            "CrystalTypeMismatch",
            "CrystalUnusedVariable",
        )

    fun testDescriptionsAreOnTheClasspathAndNonEmpty() {
        inspections.forEach { name ->
            val path = "inspectionDescriptions/$name.html"
            val stream = javaClass.classLoader.getResourceAsStream(path)
            assertNotNull("missing $path", stream)
            val body = stream!!.use { it.readBytes().decodeToString() }
            assertTrue("$path has no <body>", body.contains("<body>"))
            assertTrue("$path is too short", body.length > 120)
        }
    }

    fun testDescriptionsMatchRegisteredShortNames() {
        val pluginXml =
            javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml")!!.use { it.readBytes().decodeToString() }
        inspections.forEach { name ->
            assertTrue("no <localInspection> registration for $name", pluginXml.contains("shortName=\"$name\""))
        }
    }
}
