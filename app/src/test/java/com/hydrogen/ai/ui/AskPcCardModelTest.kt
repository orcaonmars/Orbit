package com.hydrogen.ai.ui

import com.hydrogen.ai.companion.CompanionApproval
import com.hydrogen.ai.companion.CompanionHealthSnapshot
import com.hydrogen.ai.companion.CompanionSession
import com.hydrogen.ai.companion.WorkTask
import com.hydrogen.ai.companion.WorkTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskPcCardModelTest {

    private val session = CompanionSession(
        baseUrl = "http://127.0.0.1:18812",
        deviceId = "dev",
        deviceName = "Phone",
        keyId = "key",
        sessionKeyBytes = ByteArray(32),
        encryptedTransport = "x25519-hkdf-aes256gcm",
        pairedAtEpochSec = 1L
    )
    private val paired = CompanionUiState(session = session)
    private val msg = "m1"

    private fun sent(status: String, polling: Boolean = false) = paired.copy(
        activeApproval = CompanionApproval(approvalId = "apr_1", status = status, origin = "assistant-proposed"),
        isPollingApproval = polling,
        askPcSends = mapOf(msg to AskPcSend(approvalId = "apr_1"))
    )

    @Test
    fun `unpaired phone is sent to setup, never to Send`() {
        val m = AskPcCard.model(CompanionUiState(), msg)
        assertEquals(AskPcCardAction.OPEN_COMPANION, m.action)
        assertEquals("Set up PC Companion", m.actionLabel)
    }

    @Test
    fun `paired with unknown health offers Send`() {
        val m = AskPcCard.model(paired, msg)
        assertEquals(AskPcCardAction.SEND, m.action)
        assertEquals("Send to PC", m.actionLabel)
        assertTrue(m.actionEnabled)
    }

    @Test
    fun `no shared folders points to the companion`() {
        val m = AskPcCard.model(paired.copy(health = CompanionHealthSnapshot(status = "paired", workEnabled = false)), msg)
        assertEquals(AskPcCardAction.OPEN_COMPANION, m.action)
    }

    @Test
    fun `send is disabled while another request is busy`() {
        val m = AskPcCard.model(paired.copy(isRunningWorkTask = true), msg)
        assertEquals(AskPcCardAction.SEND, m.action)
        assertFalse(m.actionEnabled)
    }

    @Test
    fun `sending, then waiting for the PC owner`() {
        val sending = AskPcCard.model(paired.copy(askPcSends = mapOf(msg to AskPcSend(sending = true))), msg)
        assertTrue(sending.showProgress)
        assertNull(sending.action)
        val waiting = AskPcCard.model(sent("PENDING", polling = true), msg)
        assertTrue(waiting.showProgress)
        assertNull(waiting.action)
        val stopped = AskPcCard.model(sent("PENDING"), msg)
        assertEquals(AskPcCardAction.CHECK_APPROVAL, stopped.action)
    }

    @Test
    fun `only an approval from the PC offers Run`() {
        assertEquals(AskPcCardAction.RUN, AskPcCard.model(sent("APPROVED"), msg).action)
        for (status in listOf("PENDING", "DENIED", "REVOKED")) {
            assertTrue(status, AskPcCard.model(sent(status), msg).action != AskPcCardAction.RUN)
        }
        assertEquals(AskPcCardAction.SEND_AGAIN, AskPcCard.model(sent("EXPIRED"), msg).action)
    }

    @Test
    fun `an error offers a retry`() {
        val m = AskPcCard.model(paired.copy(askPcSends = mapOf(msg to AskPcSend(errorMessage = "Busy"))), msg)
        assertEquals("Busy", m.status)
        assertTrue(m.isError)
        assertEquals(AskPcCardAction.SEND, m.action)
        assertEquals("Try again", m.actionLabel)
    }

    @Test
    fun `a newer request elsewhere leaves this card pointing at the companion`() {
        val state = sent("PENDING").copy(activeApproval = CompanionApproval(approvalId = "apr_other", status = "PENDING"))
        assertEquals(AskPcCardAction.OPEN_COMPANION, AskPcCard.model(state, msg).action)
        // Other cards are untouched by this card's send.
        assertEquals(AskPcCardAction.SEND, AskPcCard.model(sent("PENDING"), "m2").action)
    }

    @Test
    fun `task progress, result and failure`() {
        val consumed = sent("CONSUMED")
        val running = consumed.copy(activeTask = WorkTask(taskId = "t", approvalId = "apr_1", status = "RUNNING"), isRunningWorkTask = true)
        assertTrue(AskPcCard.model(running, msg).showProgress)
        assertEquals(
            AskPcCardAction.CHECK_TASK,
            AskPcCard.model(running.copy(isRunningWorkTask = false), msg).action
        )
        val done = consumed.copy(
            activeTask = WorkTask(
                taskId = "t",
                approvalId = "apr_1",
                status = "COMPLETED",
                result = WorkTaskResult(actionType = "READ_FILE_PREVIEW", fileName = "plan.txt", previewText = "hi", bytesRead = 2)
            )
        )
        assertEquals(AskPcCardAction.ADD_RESULT, AskPcCard.model(done, msg).action)
        val binary = done.copy(activeTask = done.activeTask!!.copy(result = WorkTaskResult(actionType = "READ_FILE_PREVIEW", isBinary = true)))
        assertNull(AskPcCard.model(binary, msg).action)
        val failed = consumed.copy(activeTask = WorkTask(taskId = "t", approvalId = "apr_1", status = "FAILED", error = "File not found"))
        val f = AskPcCard.model(failed, msg)
        assertTrue(f.isError)
        assertEquals("It didn't finish on your PC: File not found", f.status)
    }

    @Test
    fun `unpairing after sending says so`() {
        val m = AskPcCard.model(sent("PENDING").copy(session = null), msg)
        assertEquals("This phone is no longer paired with your PC.", m.status)
        assertEquals(AskPcCardAction.OPEN_COMPANION, m.action)
    }
}
