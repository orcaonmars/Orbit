package com.hydrogen.ai.companion

import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The real phone client against the real PC Companion code (docs/WIFI_MODE_SPEC.md).
 *
 * Skipped unless ORBIT_INTEROP_PYTHON, ORBIT_INTEROP_HARNESS and ORBIT_INTEROP_AGENT_DIR are set. The harness
 * (Hydrogen-QA/orbit_interop_companion.py) runs a throwaway Companion on 127.0.0.1 with an ephemeral port and
 * temp state. It never touches the owner's running Companion, and it has no way to approve anything: these tests
 * stop at a PENDING request.
 */
class CompanionInteropTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun overUsbThePhonePairsAndTalksToTheRealCompanion() = interop(wifi = false)

    @Test
    fun overWifiThePhonePairsWithAProofOnlyAndTalksToTheRealCompanion() = interop(wifi = true)

    private fun interop(wifi: Boolean) {
        val python = System.getenv("ORBIT_INTEROP_PYTHON")
        val harness = System.getenv("ORBIT_INTEROP_HARNESS")
        val agentDir = System.getenv("ORBIT_INTEROP_AGENT_DIR")
        assumeTrue(
            "Set ORBIT_INTEROP_PYTHON, ORBIT_INTEROP_HARNESS and ORBIT_INTEROP_AGENT_DIR to run the interop test.",
            !python.isNullOrBlank() && !harness.isNullOrBlank() && !agentDir.isNullOrBlank()
        )
        Harness(python!!, harness!!, agentDir!!).use { pc ->
            // Over Wi-Fi the phone sees a 192.168 address (so no legacy code is sent and a proof is required);
            // the test only dials it at the throwaway Companion on 127.0.0.1.
            val baseUrl = if (wifi) "http://192.168.77.10:${pc.port}" else "http://127.0.0.1:${pc.port}"
            val client = HttpCompanionClient(
                lanDialAddress = { if (wifi) byteArrayOf(127, 0, 0, 1) else it }
            )
            runBlocking {
                val health = client.checkHealth(baseUrl)
                assertTrue("health: $health", health is CompanionResult.Success)

                val wrong = client.pairDevice(baseUrl, "Pixel é interop", pc.pairingId, "000000-00000000")
                assertTrue("a wrong code must not pair: $wrong", wrong is CompanionResult.Failure)
                assertEquals("No paired devices.", pc.ask("DEVICES").trim())

                val paired = client.pairDevice(baseUrl, "Pixel é interop", pc.pairingId, pc.pairingCode)
                if (paired !is CompanionResult.Success) fail("pairing failed: $paired")
                val session = (paired as CompanionResult.Success).value
                assertEquals(baseUrl, session.baseUrl)
                val devices = pc.ask("DEVICES")
                assertTrue(devices, devices.contains(session.deviceId))
                assertTrue(devices, devices.contains("Pixel é interop"))

                val requested = client.requestApproval(
                    session = session,
                    actionType = WorkActionType.READ_FILE_PREVIEW,
                    targetPath = pc.notesPath,
                    summary = "Preview notes.txt",
                    idempotencyKey = "interop-" + UUID.randomUUID()
                )
                if (requested !is CompanionResult.Success) fail("request failed: $requested")
                val approval = (requested as CompanionResult.Success).value
                assertEquals("PENDING", approval.status)

                val polled = client.getApprovalStatus(session, approval.approvalId)
                if (polled !is CompanionResult.Success) fail("status failed: $polled")
                assertEquals("PENDING", (polled as CompanionResult.Success).value.status)
                val list = pc.ask("LIST")
                assertTrue(list, list.contains(approval.approvalId) && list.contains("[PENDING]"))

                val revoked = client.revokePairing(session)
                if (revoked !is CompanionResult.Success) fail("revoke failed: $revoked")
                assertTrue((revoked as CompanionResult.Success).value.revoked)
                val afterRevoke = client.getApprovalStatus(session, approval.approvalId)
                assertTrue("a revoked phone must be refused: $afterRevoke", afterRevoke is CompanionResult.Failure)
                assertFalse(pc.ask("DEVICES").contains("revoked=False"))
            }
        }
    }

    private inner class Harness(python: String, script: String, agentDir: String) : AutoCloseable {
        private val process = ProcessBuilder(python, script, agentDir)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        private val lines = LinkedBlockingQueue<String>()
        private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
        val port: Int
        val pairingId: String
        val pairingCode: String
        val notesPath: String

        init {
            Thread {
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { lines.put(it) }
            }.apply { isDaemon = true }.start()
            val ready = json.parseToJsonElement(next("READY ")).jsonObject
            port = ready.getValue("port").jsonPrimitive.content.toInt()
            pairingId = ready.getValue("pairingId").jsonPrimitive.content
            pairingCode = ready.getValue("pairingCode").jsonPrimitive.content
            notesPath = ready.getValue("notesPath").jsonPrimitive.content
            require(File(notesPath).isAbsolute)
        }

        /** Owner-side read-only views of the throwaway Companion ("DEVICES" or "LIST"). */
        fun ask(command: String): String {
            writer.write(command + "\n")
            writer.flush()
            return json.parseToJsonElement(next("$command ")).jsonPrimitive.content
        }

        private fun next(prefix: String): String {
            while (true) {
                val line = lines.poll(30, TimeUnit.SECONDS) ?: throw AssertionError("harness did not answer $prefix")
                if (line.startsWith(prefix)) return line.removePrefix(prefix)
            }
        }

        override fun close() {
            runCatching { writer.write("STOP\n"); writer.flush(); writer.close() }
            if (!process.waitFor(15, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
}
