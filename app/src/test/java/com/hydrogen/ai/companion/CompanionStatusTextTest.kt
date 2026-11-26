package com.hydrogen.ai.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionStatusTextTest {

    private val safeCaps = CompanionCapabilities(
        health = true,
        devicePairing = true,
        encryptedTransport = true,
        explicitApprovals = true,
        pathAllowlist = true
    )

    private fun health(status: String = "unpaired", roots: Int = 1, caps: CompanionCapabilities = safeCaps) =
        CompanionHealthSnapshot(
            service = "hydrogen-windows-work-companion",
            version = "0.3.0",
            status = status,
            workEnabled = status == "paired" && roots > 0,
            pairedDeviceCount = if (status == "paired") 1 else 0,
            allowlistRootCount = roots,
            capabilities = caps
        )

    private val session = CompanionSession(
        baseUrl = "http://10.0.2.2:18812",
        deviceId = "dev-123",
        deviceName = "Pixel",
        keyId = "key-9",
        sessionKeyBytes = byteArrayOf(0x5A, 0x5A, 0x5A, 0x5A),
        encryptedTransport = "x25519-hkdf-aesgcm",
        pairedAtEpochSec = 1L
    )

    @Test
    fun headlineSaysWhetherThePhoneIsPaired() {
        assertEquals("Paired with your PC", CompanionStatusText.headline(paired = true))
        assertEquals("Not paired yet", CompanionStatusText.headline(paired = false))
    }

    @Test
    fun beforeTheFirstCheckOnlyAPairedPhoneIsToldToCheck() {
        assertEquals(emptyList<String>(), CompanionStatusText.summary(paired = false, health = null))
        assertEquals(listOf("Not checked yet. Tap Check connection."), CompanionStatusText.summary(true, null))
    }

    @Test
    fun unpairedButReachablePointsToPairing() {
        assertEquals(
            listOf("Your PC is reachable. Pair it below to connect."),
            CompanionStatusText.summary(paired = false, health = health())
        )
    }

    @Test
    fun pairedSummaryCountsSharedFoldersOrAsksToShareOne() {
        assertEquals(
            "Orbit can ask to look inside 1 shared folder, read-only.",
            CompanionStatusText.summary(true, health("paired", roots = 1))[1]
        )
        assertEquals(
            "Orbit can ask to look inside 3 shared folders, read-only.",
            CompanionStatusText.summary(true, health("paired", roots = 3))[1]
        )
        assertEquals(
            "No folders are shared yet. Share one in Orbit PC Companion on your PC.",
            CompanionStatusText.summary(true, health("paired", roots = 0))[1]
        )
    }

    @Test
    fun pairedPhoneThatThePcForgotGetsNoSummary() {
        assertEquals(emptyList<String>(), CompanionStatusText.summary(paired = true, health = health("unpaired")))
    }

    @Test
    fun safetyNoteOnlyWhenThePcReportsEncryptionAndApprovals() {
        assertNotNull(CompanionStatusText.safetyNote(health()))
        assertNull(CompanionStatusText.safetyWarning(health()))
        assertNull(CompanionStatusText.safetyNote(null))
        assertNull(CompanionStatusText.safetyWarning(null))

        val noApprovals = health(caps = safeCaps.copy(explicitApprovals = false))
        assertNull(CompanionStatusText.safetyNote(noApprovals))
        assertNotNull(CompanionStatusText.safetyWarning(noApprovals))

        val approvalNotRequired = health().copy(approvalRequired = false)
        assertNotNull(CompanionStatusText.safetyWarning(approvalNotRequired))
    }

    @Test
    fun technicalLinesKeepTheRawFieldsButNeverTheSessionKey() {
        val lines = CompanionStatusText.technicalLines(session, health("paired"))
        val all = lines.joinToString("\n")
        assertTrue(all.contains("hydrogen-windows-work-companion v0.3.0 (paired)"))
        assertTrue(all.contains("Shared folders: 1, PC work: on, Paired phones: 1"))
        assertTrue(all.contains("Offers: pairing, encryption, approvals, folder allowlist"))
        assertTrue(all.contains("Turned off: remote file read, remote commands, task management"))
        assertTrue(all.contains("dev-123") && all.contains("key-9") && all.contains("http://10.0.2.2:18812"))
        assertFalse(all.contains("ZZZZ"))
        assertFalse(all.contains("5A", ignoreCase = true))
        assertFalse(all.contains("90, 90"))
    }

    @Test
    fun technicalLinesAreEmptyWithNothingKnown() {
        assertEquals(emptyList<String>(), CompanionStatusText.technicalLines(null, null))
    }

    @Test
    fun approvalLinesNeverSuggestThePhoneApproves() {
        // QA scripts look for "Waiting for explicit" while a request is pending.
        assertTrue(CompanionStatusText.approvalLine("PENDING", waiting = true).startsWith("Waiting for explicit"))
        assertTrue(CompanionStatusText.approvalLine("PENDING", waiting = false).contains("Nothing runs until you approve it"))
        for (status in listOf("DENIED", "EXPIRED", "REVOKED")) {
            assertTrue(status, CompanionStatusText.approvalLine(status, waiting = false).contains("Nothing ran."))
        }
        assertEquals("Status on your PC: odd.", CompanionStatusText.approvalLine("ODD", waiting = false))
    }

    @Test
    fun taskLinesArePlain() {
        assertEquals("Running on your PC (40%)", CompanionStatusText.taskLine("RUNNING", 40))
        assertEquals("Running on your PC (100%)", CompanionStatusText.taskLine("RUNNING", 250))
        assertEquals("Done", CompanionStatusText.taskLine("COMPLETED", 100))
        assertEquals("Paused", CompanionStatusText.taskLine("PAUSED", 0))
    }

    @Test
    fun unreachableExplainsWhatToDoWithoutRawErrors() {
        val usb = CompanionStatusText.unreachable(loopback = true)
        val lan = CompanionStatusText.unreachable(loopback = false)
        listOf(usb, lan).forEach {
            org.junit.Assert.assertTrue(it, it.startsWith("Can't reach your PC."))
            org.junit.Assert.assertFalse(it, it.contains("127.0.0.1") || it.contains("Failed"))
        }
        org.junit.Assert.assertTrue(usb.contains("Wi-Fi mode"))
    }
}
