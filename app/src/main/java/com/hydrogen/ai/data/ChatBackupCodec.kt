package com.hydrogen.ai.data

import com.hydrogen.ai.model.Conversation
import com.hydrogen.ai.model.ConversationListTools
import com.hydrogen.ai.model.MessageStatus
import com.hydrogen.ai.model.MessageVersions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ChatBackup(
    val format: String = ChatBackupCodec.FORMAT,
    val version: Int = ChatBackupCodec.VERSION,
    val exportedAtEpochMs: Long = 0L,
    val conversations: List<Conversation> = emptyList()
)

/**
 * Portable, user-owned chat backup (JSON). Pure and Android-free so it is unit-testable.
 *
 * Safety: device-local attachment paths are never exported, and imported attachments are dropped,
 * so a backup file can never make the app read or delete arbitrary local files. Imported messages
 * that were mid-flight are marked cancelled, and existing conversations are never overwritten.
 */
object ChatBackupCodec {
    const val FORMAT = "hydrogen-chat-backup"
    const val VERSION = 1
    const val MAX_IMPORT_CHARS = 20_000_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    sealed interface DecodeResult {
        data class Success(val conversations: List<Conversation>) : DecodeResult
        data class Failure(val reason: String) : DecodeResult
    }

    data class MergeResult(val conversations: List<Conversation>, val added: Int, val skipped: Int)

    fun encode(conversations: List<Conversation>, nowEpochMs: Long): String {
        val exportable = conversations
            .filter { it.messages.isNotEmpty() && !it.temporary }
            .map { conversation ->
                conversation.copy(
                    // Hidden versions too: no device path leaves the phone from any message.
                    messages = MessageVersions.mapEvery(conversation.messages) { message ->
                        message.copy(attachments = message.attachments.map { it.copy(localUri = "") })
                    }
                )
            }
        return json.encodeToString(ChatBackup(exportedAtEpochMs = nowEpochMs, conversations = exportable))
    }

    fun decode(text: String): DecodeResult {
        if (text.isBlank()) return DecodeResult.Failure("The file is empty.")
        if (text.length > MAX_IMPORT_CHARS) return DecodeResult.Failure("The file is too large to import.")
        val backup = try {
            json.decodeFromString<ChatBackup>(text)
        } catch (_: Exception) {
            return DecodeResult.Failure("This is not a valid Orbit chat backup.")
        } catch (_: StackOverflowError) {
            // Versions nest, so a crafted file could nest them deep enough to overflow the decoder.
            return DecodeResult.Failure("This is not a valid Orbit chat backup.")
        }
        if (backup.format != FORMAT) return DecodeResult.Failure("This is not an Orbit chat backup.")
        if (backup.version > VERSION) {
            return DecodeResult.Failure("This backup was made by a newer Orbit version.")
        }
        val sanitized = backup.conversations
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map(::sanitize)
        return DecodeResult.Success(sanitized)
    }

    fun merge(existing: List<Conversation>, imported: List<Conversation>): MergeResult {
        val existingIds = existing.map { it.id }.toHashSet()
        val fresh = imported.filter { it.id !in existingIds }
        return MergeResult(
            conversations = (existing + fresh).sortedByDescending { it.updatedAtEpochMs },
            added = fresh.size,
            skipped = imported.size - fresh.size
        )
    }

    private fun sanitize(conversation: Conversation): Conversation = conversation.copy(
        title = ConversationListTools.normalizeTitle(conversation.title) ?: "Imported chat",
        // Hidden versions get the same treatment, so no imported message anywhere can point at a local file.
        messages = MessageVersions.mapEvery(conversation.messages.filter { it.status != MessageStatus.STAGED }) { message ->
            message.copy(
                attachments = emptyList(),
                hasSideEffectsInProgress = false,
                status = when (message.status) {
                    MessageStatus.STAGED, MessageStatus.DISPATCHED, MessageStatus.STREAMING -> MessageStatus.CANCELLED
                    else -> message.status
                }
            )
        }.map { message ->
            // A version with no messages has nothing to show; drop it rather than offer an empty arrow stop.
            val versions = message.otherVersions.filter { it.messages.isNotEmpty() }
            if (versions.size == message.otherVersions.size) {
                message
            } else {
                message.copy(otherVersions = versions, versionIndex = message.versionIndex.coerceIn(0, versions.size))
            }
        }
    )
}
