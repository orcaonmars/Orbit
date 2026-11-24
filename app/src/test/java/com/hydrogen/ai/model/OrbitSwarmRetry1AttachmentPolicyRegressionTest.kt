package com.hydrogen.ai.model

import org.junit.Test
import org.junit.Assert.*

class OrbitSwarmRetry1AttachmentPolicyRegressionTest {
    @Test
    fun testUnsupportedMessageForDocx() {
        val msg = FileAttachPolicy.unsupportedMessage("example.docx")
        // Before the patch, the message would be the generic unsupported text.
        // After the patch, it should mention that it is a Word file.
        assertTrue("Message should mention Word file", msg.contains("Word file"))
    }
}