package com.hydrogen.ai.data

import com.hydrogen.ai.model.MessageRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved chats file must survive the update from Orbit 0.1 untouched, and a damaged file must cost as little as
 * possible. The fixture is a real conversations.json written by the frozen 0.1 build on the emulator.
 */
class ConversationFileCodecTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val v01: String =
        requireNotNull(javaClass.classLoader?.getResource("upgrade/v0.1-conversations.json")) { "fixture missing" }
            .readText(Charsets.UTF_8)

    @Test fun fileWrittenByOrbit01ReadsWithNothingLost() {
        val decoded = ConversationFileCodec.decode(v01, json)
        assertNull(decoded.problem)
        assertEquals(2, decoded.conversations.size)

        val tutor = decoded.conversations.single { it.title == "Explain photosynthesis simply" }
        assertTrue(tutor.systemPrompt.startsWith("You are a patient, encouraging tutor."))
        assertFalse(tutor.pinned)
        assertEquals(2, tutor.messages.size)
        val reply = tutor.messages.last()
        assertEquals(MessageRole.ASSISTANT, reply.role)
        assertTrue(reply.turnMetadata?.thinkingSummary.orEmpty().startsWith("The user wants a plan"))
        assertEquals("stop", reply.turnMetadata?.finishReason)

        val lisbon = decoded.conversations.single { it.title == "Plan a weekend in Lisbon with a table" }
        assertTrue(lisbon.pinned)
        assertEquals("gemini-4-hydrogen", lisbon.modelId)
        assertEquals(4, lisbon.messages.size)
        assertTrue(lisbon.messages[1].content.contains("| Train | Low | 2 h | Reading on the way |"))
        assertEquals("Now make it shorter", lisbon.messages[2].content)
    }

    @Test fun fieldsOrbit01NeverWroteStartAtTheirDefaults() {
        val chats = ConversationFileCodec.decode(v01, json).conversations
        chats.forEach { chat ->
            assertFalse(chat.archived)
            assertNull(chat.projectId)
            assertFalse(chat.temporary)
            chat.messages.forEach { message ->
                assertNull(message.thinkingMs)
                assertTrue(message.otherVersions.isEmpty())
                assertEquals(0, message.versionIndex)
            }
        }
    }

    @Test fun fieldsFromANewerOrbitAreIgnored() {
        val newer = v01.replace("\"pinned\":true}", "\"pinned\":true,\"colour\":\"blue\",\"folders\":[1,2]}")
        val decoded = ConversationFileCodec.decode(newer, json)
        assertNull(decoded.problem)
        assertEquals(2, decoded.conversations.size)
    }

    @Test fun aDamagedMessageIsLeftOutAndTheRestOfItsChatKept() {
        val damaged = v01.replace(
            "\"role\":\"USER\",\"content\":\"Now make it shorter\"",
            "\"role\":\"ROBOT\",\"content\":\"Now make it shorter\""
        )
        assertTrue(damaged != v01)
        val decoded = ConversationFileCodec.decode(damaged, json)
        assertEquals(ChatsLoadProblem(skippedMessages = 1), decoded.problem)
        assertEquals(2, decoded.conversations.size)
        val lisbon = decoded.conversations.single { it.pinned }
        assertEquals(3, lisbon.messages.size)
        assertTrue(lisbon.messages.none { it.content == "Now make it shorter" })
    }

    @Test fun aChatThatCannotBeReadIsLeftOutAndTheOthersKept() {
        // No id: a required field, and no message is to blame, so the chat as a whole can't be read.
        val damaged = v01.replace("{\"id\":\"95b6e878-13da-4bcc-aa42-bfef61432b9d\",", "{")
        assertTrue(damaged != v01)
        val decoded = ConversationFileCodec.decode(damaged, json)
        assertEquals(ChatsLoadProblem(skippedChats = 1), decoded.problem)
        assertEquals(listOf("Plan a weekend in Lisbon with a table"), decoded.conversations.map { it.title })
    }

    @Test fun aHalfWrittenFileIsReportedUnreadable() {
        val decoded = ConversationFileCodec.decode(v01.take(v01.length / 2), json)
        assertEquals(ChatsLoadProblem(unreadable = true), decoded.problem)
        assertTrue(decoded.conversations.isEmpty())
    }

    @Test fun somethingThatIsNotAChatsFileIsReportedUnreadable() {
        listOf("not json {", "[]", "42", "{\"schemaVersion\":1}", "{\"conversations\":{}}").forEach { raw ->
            assertEquals(raw, ChatsLoadProblem(unreadable = true), ConversationFileCodec.decode(raw, json).problem)
        }
    }

    @Test fun anEmptyFileIsSimplyNoChats() {
        val decoded = ConversationFileCodec.decode("  \n", json)
        assertNull(decoded.problem)
        assertTrue(decoded.conversations.isEmpty())
    }

    @Test fun anEmptyListIsNoProblem() {
        val decoded = ConversationFileCodec.decode("{\"schemaVersion\":1,\"conversations\":[]}", json)
        assertNull(decoded.problem)
        assertTrue(decoded.conversations.isEmpty())
    }

    @Test fun noticesUsePlainWordsAndTheRightNumber() {
        val notices = listOf(
            ChatsLoadProblem(unreadable = true),
            ChatsLoadProblem(skippedChats = 1),
            ChatsLoadProblem(skippedChats = 3),
            ChatsLoadProblem(skippedMessages = 1),
            ChatsLoadProblem(skippedMessages = 2),
            ChatsLoadProblem(skippedChats = 1, skippedMessages = 4)
        ).map { it.notice }
        notices.forEach { notice ->
            assertFalse(notice, notice.contains('!'))
            assertFalse(notice, notice.contains('—'))
            assertFalse(notice, notice.contains('–'))
            assertTrue(notice, notice.contains("kept on this phone"))
        }
        assertTrue(notices[1].startsWith("1 saved chat couldn't be read and was left out."))
        assertTrue(notices[2].startsWith("3 saved chats couldn't be read and were left out."))
        assertTrue(notices[3].startsWith("1 message couldn't be read and was left out."))
        assertTrue(notices[4].startsWith("2 messages couldn't be read and were left out."))
        assertTrue(notices[5].startsWith("1 saved chat and 4 messages couldn't be read and were left out."))
        assertNotNull(notices[0])
    }
}
