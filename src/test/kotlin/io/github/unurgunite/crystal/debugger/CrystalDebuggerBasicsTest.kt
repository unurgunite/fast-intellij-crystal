package io.github.unurgunite.crystal.debugger

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class CrystalDebugAdapterIdTest : TestCase() {
    fun testIdentity() {
        assertEquals("crystal-lldb", CrystalDebugAdapterId.type)
        assertEquals(CrystalDebugAdapterId, CrystalDebugAdapterSupportProvider().adapterId)
    }
}

class CrystalBreakpointPropertiesTest : TestCase() {
    fun testStateRoundTrip() {
        val props = CrystalBreakpointProperties()
        assertSame(props, props.state)
        // loadState is a no-op — must not throw
        props.loadState(CrystalBreakpointProperties())
    }
}

class CrystalExceptionBreakpointTypeTest : TestCase() {
    fun testDisplayText() {
        assertEquals("Crystal Exception", CrystalExceptionBreakpointType().getDisplayText(null))
    }
}

class CrystalLineBreakpointTypeTest : BasePlatformTestCase() {

    private val type = CrystalLineBreakpointType()

    fun testCanPutAtCrystalFile() {
        val file = myFixture.configureByText("main.cr", "x = 1\n")
        assertTrue(type.canPutAt(file.virtualFile, 0, project))
    }

    fun testCannotPutAtForeignFile() {
        val file = myFixture.configureByText("notes.txt", "hello\n")
        assertFalse(type.canPutAt(file.virtualFile, 0, project))
    }

    fun testCreateProperties() {
        val file = myFixture.configureByText("main.cr", "x = 1\n")
        assertNotNull(type.createBreakpointProperties(file.virtualFile, 0))
    }
}

class CrystalDebugAdapterSupportProviderTest : BasePlatformTestCase() {
    fun testCreatesDescriptor() {
        val descriptor = CrystalDebugAdapterSupportProvider().createDebugAdapterDescriptor(project)
        assertNotNull(descriptor)
        assertEquals(CrystalDebugAdapterId, descriptor.id)
    }

    fun testRunnerWiredToAdapter() {
        // The program runner launches debug sessions; the provider must serve the same adapter id
        assertEquals("crystal-lldb", CrystalDebugAdapterSupportProvider().adapterId.type)
        assertEquals(DefaultDebugExecutor.EXECUTOR_ID, com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID)
    }

    fun testDescriptorBreakpoints() {
        val descriptor = CrystalDebugAdapterSupportProvider().createDebugAdapterDescriptor(project)
        val breakpoints = descriptor.breakpointsDescription
        assertNotNull(breakpoints)
    }

    fun testLldbDapCandidateIsExecutableOrNull() {
        // No PATH lookup by design (fixed candidates only): result is either an
        // executable file or null (→ "lldb-dap" fallback at launch time).
        val candidate = CrystalDebugAdapterDescriptor.findLldbDapCandidate()
        if (candidate != null) {
            assertTrue(java.io.File(candidate).canExecute())
        }
    }
}
