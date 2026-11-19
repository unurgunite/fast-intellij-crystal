package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A bare (parenthesis-free) call may pass a typed label with a default
 * (`routes rules, path : Regex? = nil`). The `?` belongs to the type, so
 * the `=` must end the type and start the default value — otherwise the type
 * arm fails and the label's value is mis-parsed, throwing off arity.
 */
class CrystalArgumentCountTypedBareLabelTest : BasePlatformTestCase() {
    fun testNamedTypedParamsAsSplatArgs() {
        myFixture.configureByText(
            "a.cr",
            """
            macro routes(name, *members)
              nil
            end

            class Config
              routes rules,
                path : Regex? = nil,
                host : Regex? = nil
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroWithSplatAcceptsExtraArgs() {
        myFixture.configureByText(
            "a.cr",
            """
            macro routes(name, *members)
              def {{name.id}}
                {{members[0]}}
              end
            end

            class Config
              routes rules,
                "path",
                "host"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testMacroWithSplatOnOneLine() {
        myFixture.configureByText(
            "a.cr",
            """
            macro routes(name, *members)
              def {{name.id}}
                nil
              end
            end

            class Config
              routes rules, "path", "host"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
