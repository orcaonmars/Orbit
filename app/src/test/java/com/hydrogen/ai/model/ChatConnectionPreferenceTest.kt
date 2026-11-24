package com.hydrogen.ai.model

import com.hydrogen.ai.data.ChatBackupCodec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatConnectionPreferenceTest {
    @Test fun restoringSelectionNeverCopiesCredentialsOrForeignCatalog() {
        val a = RelayConfig(baseUrl = "https://a.example", modelId = "a", rememberTokenInKeystore = true)
        val b = RelayConfig(baseUrl = "https://openrouter.ai/api", modelId = "b",
            openRouterReasoningModels = setOf("b"), rememberTokenInKeystore = false)
        val restored = ChatConnectionPreference.capture(a)!!.applyTo(b)!!
        assertEquals("https://a.example", restored.effectiveBaseUrl)
        assertEquals("a", restored.modelId)
        assertFalse(restored.rememberTokenInKeystore)
        assertTrue(restored.openRouterReasoningModels.isEmpty())
        assertNull(ChatConnectionPreference.capture(a.copy(baseUrl = "https://user:pass@a.example")))
        assertNull(ChatConnectionPreference.capture(a.copy(baseUrl = "https://a.example?key=secret")))
        assertNull(ChatConnectionPreference.capture(a.copy(baseUrl = "http://public.example")))
    }

    @Test fun backupsCannotExportOrImportLocalEndpointChoices() {
        val preference = ChatConnectionPreference.capture(RelayConfig(baseUrl = "https://private.example", modelId = "a"))!!
        val chat = Conversation("id", "Title", 1, 2, messages = listOf(
            ChatMessage(id = "m", correlationId = "c", role = MessageRole.USER, content = "Hi", createdAtEpochMs = 1)
        ), preferredConnection = preference)
        val exported = ChatBackupCodec.encode(listOf(chat), 3)
        assertFalse(exported.contains("private.example"))
        val crafted = Json.encodeToString(com.hydrogen.ai.data.ChatBackup(conversations = listOf(chat)))
        val decoded = ChatBackupCodec.decode(crafted) as ChatBackupCodec.DecodeResult.Success
        assertNull(decoded.conversations.single().preferredConnection)
        val local = Json.decodeFromString<Conversation>(Json.encodeToString(chat))
        assertEquals(preference, local.preferredConnection)
    }
}
