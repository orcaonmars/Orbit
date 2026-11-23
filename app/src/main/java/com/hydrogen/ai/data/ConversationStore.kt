package com.hydrogen.ai.data

import com.hydrogen.ai.model.Conversation
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ConversationStore {
    suspend fun loadConversations(): List<Conversation>
    suspend fun saveConversations(conversations: List<Conversation>)

    /** What the last [loadConversations] could not read; null when everything was read. */
    fun lastLoadProblem(): ChatsLoadProblem? = null

    /** Removes copies of a chats file an earlier load could not fully read (Delete all data). */
    suspend fun deleteKeptCopies() {}
}

/** Wraps [inner] so a temporary chat is never written, whichever code path saves the list. */
class SavedChatsOnly(private val inner: ConversationStore) : ConversationStore {
    override suspend fun loadConversations(): List<Conversation> = inner.loadConversations().filterNot { it.temporary }
    override suspend fun saveConversations(conversations: List<Conversation>) =
        inner.saveConversations(conversations.filterNot { it.temporary })
    override fun lastLoadProblem(): ChatsLoadProblem? = inner.lastLoadProblem()
    override suspend fun deleteKeptCopies() = inner.deleteKeptCopies()
}

@Serializable
private data class ConversationEnvelope(
    val schemaVersion: Int = 1,
    val conversations: List<Conversation> = emptyList()
)

class FileConversationStore(
    private val storageDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) : ConversationStore {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Volatile private var loadProblem: ChatsLoadProblem? = null

    private val storeFile: File
        get() = File(storageDir, "conversations.json")

    /**
     * Never throws for a damaged file: whatever can be read is returned (see [ConversationFileCodec]). When anything
     * could not be read, the file is first copied byte for byte to conversations.unreadable-<time>.json, so the next
     * save can't lose what was left out.
     */
    override suspend fun loadConversations(): List<Conversation> = mutex.withLock {
        withContext(ioDispatcher) {
            loadProblem = null
            if (!storeFile.exists()) return@withContext emptyList()
            val raw = try {
                storeFile.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
            val decoded = if (raw == null) {
                ConversationFileCodec.Decoded(emptyList(), ChatsLoadProblem(unreadable = true))
            } else {
                ConversationFileCodec.decode(raw, json)
            }
            if (decoded.problem != null) {
                val kept = File(storageDir, "$KEPT_PREFIX${clock()}.json").toPath()
                // A file that can't be read can usually still be renamed, so the next save never writes over it.
                runCatching { Files.copy(storeFile.toPath(), kept) }
                    .onFailure { runCatching { Files.move(storeFile.toPath(), kept) } }
                loadProblem = decoded.problem
            }
            decoded.conversations.sortedByDescending { it.updatedAtEpochMs }
        }
    }

    override fun lastLoadProblem(): ChatsLoadProblem? = loadProblem

    override suspend fun deleteKeptCopies() {
        mutex.withLock {
            withContext(ioDispatcher) {
                storageDir.listFiles { file -> file.name.startsWith(KEPT_PREFIX) }?.forEach { it.delete() }
            }
        }
    }

    private companion object {
        const val KEPT_PREFIX = "conversations.unreadable-"
    }

    override suspend fun saveConversations(conversations: List<Conversation>) {
        mutex.withLock {
            withContext(ioDispatcher) {
                if (!storageDir.exists()) {
                    storageDir.mkdirs()
                }
                val payload = json.encodeToString(
                    ConversationEnvelope(
                        schemaVersion = 1,
                        conversations = conversations.sortedByDescending { it.updatedAtEpochMs }
                    )
                )
                val tmpFile = File(storageDir, "conversations.json.tmp")
                tmpFile.writeText(payload, Charsets.UTF_8)
                try {
                    Files.move(
                        tmpFile.toPath(),
                        storeFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    Files.move(
                        tmpFile.toPath(),
                        storeFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
        }
    }
}
