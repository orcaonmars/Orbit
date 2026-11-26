package com.hydrogen.ai.companion

/**
 * Plain-language wording for the PC Companion status card, kept pure so it can be unit-tested.
 *
 * The raw health and session fields stay available through [technicalLines], which the sheet shows under
 * "Technical details" for support. Nothing here ever includes key material.
 */
object CompanionStatusText {

    fun headline(paired: Boolean): String = if (paired) "Paired with your PC" else "Not paired yet"

    /**
     * Shown when the PC can't be reached at all, instead of the raw socket error. A loopback address only reaches the
     * PC from the emulator or over USB (adb reverse), so a phone gets pointed at Wi-Fi mode.
     */
    fun unreachable(loopback: Boolean): String = if (loopback) {
        "Can't reach your PC. This address only works over USB. To connect over Wi-Fi, turn on Wi-Fi mode in the " +
            "PC Companion and tap Change address."
    } else {
        "Can't reach your PC. Check that the PC Companion is running and the address is right, then tap Check connection."
    }

    /**
     * Short sentences about the connection. Empty before the first check of an unpaired phone, and when the PC
     * no longer knows a paired phone (the error message explains that case).
     */
    fun summary(paired: Boolean, health: CompanionHealthSnapshot?): List<String> {
        if (health == null) {
            return if (paired) listOf("Not checked yet. Tap Check connection.") else emptyList()
        }
        if (!paired) return listOf("Your PC is reachable. Pair it below to connect.")
        if (!health.status.equals("paired", ignoreCase = true)) return emptyList()
        return listOf(
            "Connected to your PC.",
            if (health.workEnabled) {
                "Orbit can ask to look inside ${count(health.allowlistRootCount, "shared folder")}, read-only."
            } else {
                "No folders are shared yet. Share one in Orbit PC Companion on your PC."
            }
        )
    }

    /** What the PC promises, or null when it does not report the protections Orbit relies on (see [safetyWarning]). */
    fun safetyNote(health: CompanionHealthSnapshot?): String? =
        if (health != null && isProtected(health)) {
            "Encrypted end to end. Every request needs your approval on the PC."
        } else {
            null
        }

    /** A warning when a reachable PC does not report encryption and per-request approval; null otherwise. */
    fun safetyWarning(health: CompanionHealthSnapshot?): String? =
        if (health != null && !isProtected(health)) {
            "This PC doesn't report encryption and per-request approval. Only pair with your own Orbit PC Companion."
        } else {
            null
        }

    /**
     * One plain sentence for a Work request's approval state. Every state that did not run says so, and none
     * suggests the phone can approve: approval only ever happens in Orbit PC Companion on the PC.
     */
    fun approvalLine(status: String, waiting: Boolean): String = when (status) {
        "PENDING" -> if (waiting) {
            "Waiting for explicit approval on your PC…"
        } else {
            "Waiting for explicit approval on your PC. Nothing runs until you approve it in Orbit PC Companion."
        }
        "APPROVED" -> "Approved on your PC. Run it when you're ready."
        "CONSUMED" -> "Approved and used. The task below has the result."
        "DENIED" -> "Denied on your PC. Nothing ran. You can request it again above."
        "EXPIRED" -> "Not approved in time. Nothing ran. You can request it again above."
        "REVOKED" -> "Withdrawn on your PC. Nothing ran. You can request it again above."
        else -> "Status on your PC: ${status.lowercase()}."
    }

    /** Short plain status for a Work task, e.g. "Running on your PC (40%)". */
    fun taskLine(status: String, progressPercent: Int): String = when (status) {
        "QUEUED" -> "Queued on your PC"
        "RUNNING" -> "Running on your PC (${progressPercent.coerceIn(0, 100)}%)"
        "COMPLETED" -> "Done"
        "FAILED" -> "Didn't finish"
        "CANCELLED" -> "Cancelled"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** Raw fields for "Technical details". */
    fun technicalLines(session: CompanionSession?, health: CompanionHealthSnapshot?): List<String> = buildList {
        if (health != null) {
            add("Service: ${health.service} v${health.version} (${health.status})")
            add(
                "Shared folders: ${health.allowlistRootCount}, PC work: ${if (health.workEnabled) "on" else "off"}" +
                    ", Paired phones: ${health.pairedDeviceCount}"
            )
            val caps = health.capabilities
            val features = listOf(
                "pairing" to caps.devicePairing,
                "encryption" to caps.encryptedTransport,
                "approvals" to caps.explicitApprovals,
                "folder allowlist" to caps.pathAllowlist,
                "remote file read" to caps.remoteFileRead,
                "remote commands" to caps.remoteCommandExecution,
                "task management" to caps.remoteTaskManagement
            )
            add("Offers: " + features.filter { it.second }.joinToString { it.first }.ifEmpty { "none" })
            add("Turned off: " + features.filterNot { it.second }.joinToString { it.first }.ifEmpty { "none" })
        }
        if (session != null) {
            add("This phone: ${session.deviceName} (${session.deviceId})")
            add("Key ID: ${session.keyId}, ${session.encryptedTransport}")
            add("Address: ${session.baseUrl}")
        }
    }

    private fun isProtected(health: CompanionHealthSnapshot): Boolean =
        health.capabilities.encryptedTransport && health.capabilities.explicitApprovals && health.approvalRequired

    private fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"
}
