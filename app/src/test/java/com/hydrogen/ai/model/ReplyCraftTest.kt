package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyCraftTest {
    @Test fun pendingExportRetainsOriginalCodeAndNameAfterVersionSwitch() {
        val first = MdBlock.CodeBlock(language = "kotlin", code = "class First {}", closed = true)
        val second = first.copy(code = "class Second {}")
        val snapshot = CodeExportSnapshot.capture(first)!!
        assertEquals("First.kt", snapshot.fileName)
        assertEquals(first.code, snapshot.code)
        assertEquals("Second.kt", CodeExportSnapshot.capture(second)?.fileName)
    }

    @Test fun incompleteOrBlankCodeIsNotExportable() {
        assertNull(CodeExportSnapshot.capture(MdBlock.CodeBlock(language = "text", code = "partial", closed = false)))
        assertNull(CodeExportSnapshot.capture(MdBlock.CodeBlock(language = "text", code = " ", closed = true)))
    }

    @Test fun normalPhoneCanKeepACompactHeader() {
        assertFalse(ReplyLayoutPolicy.stackHeader(320f, 1f))
        assertTrue(ReplyLayoutPolicy.stackHeader(240f, 1f))
    }

    @Test fun largeFontsStackActionsUntilThereIsEnoughSpace() {
        assertTrue(ReplyLayoutPolicy.stackHeader(320f, 2f))
        assertFalse(ReplyLayoutPolicy.stackHeader(640f, 2f))
    }
}