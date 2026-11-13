package io.github.unurgunite.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalUnusedVariableInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
    }

    // ==================== Basic Unused Variables ====================

    fun testUnusedVariable() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              <weak_warning descr="Variable 'x' is never used">x</weak_warning> = 42
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUsedVariable() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = 42
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testUnderscorePrefixNoWarning() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              _unused = 42
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Reassignment ====================

    fun testReassignmentFirstUnused() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 1
              x = 2
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReassignmentLastUnused() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = 1
              puts x
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 2
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReassignmentBothUsed() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = 1
              puts x
              x = 2
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Compound Assignment ====================

    fun testCompoundAssignmentNoWarning() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = 0
              x += 1
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Method Parameters ====================

    fun testMethodParameterNoWarning() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(x : Int32, y : String)
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Instance/Class Vars ====================

    fun testInstanceVarNotChecked() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              @name = "hello"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testClassVarNotChecked() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              @@count = 0
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Branch ====================

    fun testVariableUsedInIfBranch() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = 42
              if true
                puts x
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Multiple Unused Variables ====================

    fun testMultipleUnusedVariables() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              <weak_warning descr="Variable 'a' is never used">a</weak_warning> = 1
              <weak_warning descr="Variable 'b' is never used">b</weak_warning> = 2
              c = 3
              puts c
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Top-Level Variables ====================

    fun testTopLevelUnusedVariable() {
        myFixture.configureByText(
            "test.cr",
            """
            <weak_warning descr="Variable 'f' is never used">f</weak_warning> = "hello"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testTopLevelUsedVariable() {
        myFixture.configureByText(
            "test.cr",
            """
            f = "hello"
            puts f
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Expression ====================

    fun testVariableUsedInArithmeticExpression() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              local_var = "local"
              p = local_var + "sdf"
              puts "a:#{p}"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInArithmeticExpressionTopLevel() {
        myFixture.configureByText(
            "test.cr",
            """
            local_var = "local"
            p = local_var + "sdf"
            puts "a:#{p}"
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInAddition() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = 1
              y = x + 2
              puts y
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInStringConcat() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              name = "world"
              greeting = "hello " + name
              puts greeting
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInStringInterpolation() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              name = "world"
              puts "hello #{name}"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Own Reassignment ====================

    fun testVariableUsedInOwnReassignment() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              abc = "string"
              abc = abc.upcase
              puts abc
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Conditional Branch False Positives ====================

    fun testInitialAssignWithConditionalReassignInIf() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              if true
                x = "override"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInUnless() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              unless true
                x = "override"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInBeginRescue() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              begin
                x = "override"
              rescue
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInWhile() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              while false
                x = "override"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInUntil() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              until true
                x = "override"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInFor() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              for item in [] of String
                x = item
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalReassignInCase() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              case 1
              when 1
                x = "one"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithNestedConditionals() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              if true
                if false
                  x = "deep"
                end
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testInitialAssignWithConditionalAndUnconditionalOverwrite() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = ""
              if true
                <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = "override"
              end
              x = "final"
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testBeginWithoutRescueStillWarns() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = ""
              begin
                x = "override"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testConditionalAssignInElsifChain() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              if true
                x = "one"
              elsif false
                x = "two"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testConditionalAssignInIfElse() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              x = ""
              if true
                x = "one"
              else
                x = "two"
              end
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testAssignedInLoopReadAfterLoop() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(rows)
              config = nil
              rows.each do |row|
                config = row.config
              end
              Lint.run(config)
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testAssignedAndReadInsideBlock() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(items)
              items.each do |item|
                name = item.name
                puts name
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testReassignedInLoopAndReadInsideSameBlock() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(items)
              state = :start
              items.each do |item|
                state = :running
                handle state
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testGenuinelyDeadReassignmentStillWarns() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 1
              x = 2
              puts x
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testValueAssignedInBlockReadAfterBlock() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(list)
              value = nil
              list.each { |x| value = x * 2 }
              puts value
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // A block body runs once per iteration, so a value written at its end is
    // read again at the top of the next iteration (a tokenizer:
    // `issue = start_token = nil` after the case, read by the next yield).
    fun testWriteAtEndOfBlockReadByNextIteration() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(list)
              start = nil
              list.each do |item|
                use start
                start = item
              end
            end
            def use(x); end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testDeadStoreInsideBlockStillReported() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              list = [1]
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 1
              list.each do
                <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 2
                <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 3
              end
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    // ==================== Reads inside range/slice expressions ====================

    /** ameba `unless_else.cr`: the variable is read one line later, as a range bound. */
    fun testReadAsRangeBoundIsUsed() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(location, else_location)
              cond_end_pos = source.pos(location, end: true)
              else_begin_pos = source.pos(else_location)
              body_range = cond_end_pos...else_begin_pos
              puts body_range
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** ameba `heredoc_indent.cr`: the variable indexes a slice. */
    fun testReadAsSliceIndexIsUsed() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo(body_dedent)
              dedent = body_dedent
              puts "#{" " * 2}#{line[dedent..]}"
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    /** Exclusive range on both sides. */
    fun testReadAsExclusiveRangeBoundIsUsed() {
        myFixture.configureByText(
            "test.cr",
            """
            def foo
              first = 1
              last = 5
              slice = first...last
              puts slice
            end
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }
}
