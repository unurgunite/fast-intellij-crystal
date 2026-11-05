package io.github.unurgunite.crystal.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class CrystalHeredocValidationTest : BasePlatformTestCase() {
    private fun heredocErrors(code: String): List<HighlightInfo> {
        myFixture.configureByText("a.cr", code)
        return myFixture.doHighlighting().filter { it.description?.contains("Heredoc") == true }
    }

    fun testIndentedTerminatorMatchesBody() {
        val errs = heredocErrors("def foo\n  text = <<-CRYSTAL\n    hello\n    CRYSTAL\n  text\nend\n")
        assertEmpty(errs.map { it.description }.toString(), errs)
    }

    fun testTerminatorAtColumnZero() {
        val errs = heredocErrors("def foo\n  text = <<-CRYSTAL\n    hello\nCRYSTAL\n  text\nend\n")
        assertEmpty(errs.map { it.description }.toString(), errs)
    }

    fun testTerminatorDeeperThanBodyStillReported() {
        val errs = heredocErrors("def foo\n  text = <<-CRYSTAL\n  hello\n      CRYSTAL\n  text\nend\n")
        assertSize(1, errs)
    }

    // The block-shaped claim: a heredoc the grammar cannot attach (brace literal)
    // must not produce indent complaints computed from unrelated lines.
    fun testHeredocInBraceLiteralDoesNotReportIndent() {
        val errs = heredocErrors("s = {<<-PRE.lines, 1}\n  x\n  PRE\n")
        val indentErrs = errs.filter { it.description?.contains("indented too deeply") == true }
        assertEmpty(indentErrs.map { it.description }.toString(), indentErrs)
    }

    fun testRealSpecFileHasNoIndentComplaints() {
        val src = File("/Users/pearl/projects/some_jobs/autoreg/lib/ameba/spec/ameba/formatter/util_spec.cr")
        assertTrue("fixture file missing", src.isFile)
        val indentErrs = heredocErrors(src.readText()).filter { it.description?.contains("indented too deeply") == true }
        assertEmpty(indentErrs.map { it.description }.toString(), indentErrs)
    }
}
