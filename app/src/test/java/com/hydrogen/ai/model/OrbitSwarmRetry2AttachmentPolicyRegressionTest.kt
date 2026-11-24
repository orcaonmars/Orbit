package com.hydrogen.ai.model

import org.junit.Test
import org.junit.Assert.*

class OrbitSwarmRetry2AttachmentPolicyRegressionTest {
    @Test
    fun testUnsupportedMessageForPdf() {
        val msg = FileAttachPolicy.unsupportedMessage("document.pdf")
        assertTrue("Message should mention PDF", msg.contains("PDF"))
        assertTrue("Message should be specific", msg.contains("Attach it again"))
    }
}
