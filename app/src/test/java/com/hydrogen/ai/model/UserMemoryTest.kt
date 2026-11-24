package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryTest {
    private fun item(id: String, text: String, at: Long = 0, pinned: Boolean = false) =
        MemoryItem(id, text, at, pinned)

    @Test
    fun addPutsNewItemFirstAndCleansWhitespace() {
        val list = UserMemory.add(listOf(item("a", "I write Kotlin")), "  I am\n vegetarian  ", "b", 5)
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals("I am vegetarian", list.first().text)
        assertEquals(5L, list.first().createdAt)
        assertFalse(list.first().pinned)
    }

    @Test
    fun addIgnoresBlankAndCaseInsensitiveDuplicates() {
        val start = listOf(item("a", "I write Kotlin"))
        assertEquals(start, UserMemory.add(start, "   ", "b", 1))
        assertEquals(start, UserMemory.add(start, "i WRITE kotlin", "b", 1))
    }

    @Test
    fun addSkipsNearIdenticalNotesButKeepsDistinctOnes() {
        val start = listOf(item("a", "I really like dark mode"))
        // Five of six words are shared (> 0.8), so this is the same fact restated.
        assertEquals(start, UserMemory.add(start, "I really like dark mode theme", "b", 2))
        // Accents, punctuation and case do not create a second note.
        val cafe = listOf(item("a", "I like café"))
        assertEquals(cafe, UserMemory.add(cafe, "i like CAFE!", "b", 2))
        // Four of six words shared stays below the threshold: a genuinely different note.
        val more = UserMemory.add(start, "I really like light mode", "b", 2)
        assertEquals(listOf("b", "a"), more.map { it.id })
    }

    @Test
    fun addClipsLongTextAndDropsOldestPastLimit() {
        var list = emptyList<MemoryItem>()
        for (i in 0 until UserMemory.MAX_ITEMS + 3) list = UserMemory.add(list, "fact $i", "id$i", i.toLong())
        assertEquals(UserMemory.MAX_ITEMS, list.size)
        assertEquals("id${UserMemory.MAX_ITEMS + 2}", list.first().id)
        assertTrue(list.none { it.id == "id0" })
        val long = UserMemory.add(emptyList(), "x".repeat(1000), "l", 0)
        assertEquals(UserMemory.MAX_ITEM_CHARS, long.single().text.length)
    }

    @Test
    fun addDropsOldestUnpinnedNoteSoPinnedOnesSurviveTheCap() {
        var list = emptyList<MemoryItem>()
        for (i in 0 until UserMemory.MAX_ITEMS) list = UserMemory.add(list, "note $i", "id$i", i.toLong())
        val oldestIndex = list.lastIndex
        assertFalse(list.last().pinned)
        list = list.mapIndexed { index, it -> if (index == oldestIndex) it.copy(pinned = true) else it }

        val after = UserMemory.add(list, "fresh note", "fresh", 1_000)

        assertEquals(UserMemory.MAX_ITEMS, after.size)
        assertEquals("fresh", after.first().id)
        assertTrue(after.any { it.id == "id0" }) // pinned oldest kept
        assertTrue(after.none { it.id == "id1" }) // next-oldest unpinned dropped
    }

    @Test
    fun removeAndUpdate() {
        val start = listOf(item("a", "one"), item("b", "two"))
        assertEquals(listOf("b"), UserMemory.remove(start, "a").map { it.id })
        assertEquals("uno", UserMemory.update(start, "a", " uno ").first().text)
        assertEquals(start, UserMemory.update(start, "a", "TWO"))
        assertEquals(start, UserMemory.update(start, "a", "  "))
        assertEquals("ONE", UserMemory.update(start, "a", "ONE").first().text)
    }

    @Test
    fun updateRejectsNearDuplicateOfAnotherNoteAndKeepsPinned() {
        val start = listOf(item("a", "I really like dark mode"), item("b", "I drink tea"))
        assertEquals(start, UserMemory.update(start, "b", "I really like dark mode theme"))
        val pinned = listOf(item("a", "one", pinned = true))
        assertTrue(UserMemory.update(pinned, "a", "uno").single().pinned)
    }

    @Test
    fun systemBlockIsPinnedFirstThenNewestAndBounded() {
        assertNull(UserMemory.systemBlock(emptyList()))
        val items = listOf(
            item("new", "newest", at = 30),
            item("pinOld", "pinned older", at = 10, pinned = true),
            item("old", "oldest", at = 10),
            item("pinNew", "pinned newer", at = 20, pinned = true)
        )
        assertEquals(
            "${UserMemory.HEADER}\n- pinned newer\n- pinned older\n- newest\n- oldest",
            UserMemory.systemBlock(items)
        )
        val many = (0 until UserMemory.MAX_ITEMS).map {
            item("i$it", "$it" + "y".repeat(UserMemory.MAX_ITEM_CHARS - 2), at = it.toLong())
        }
        // The oldest note is pinned, so it must still fit inside the block cap.
        val withPinnedOldest = many.mapIndexed { index, it -> if (index == 0) it.copy(pinned = true) else it }
        val big = UserMemory.systemBlock(withPinnedOldest)!!
        assertTrue(big.length <= UserMemory.MAX_BLOCK_CHARS)
        assertTrue(big.startsWith(UserMemory.HEADER))
        assertTrue(big.contains("\n- 0"))
        assertFalse(big.contains("\n- 50"))
    }

    @Test
    fun combineWithInstructions() {
        val mem = listOf(item("a", "I am vegetarian"))
        assertEquals("Be brief", UserMemory.combineWithInstructions("Be brief", mem, enabled = false))
        assertEquals("Be brief", UserMemory.combineWithInstructions("Be brief", emptyList(), enabled = true))
        assertEquals(
            "${UserMemory.HEADER}\n- I am vegetarian\n\nBe brief",
            UserMemory.combineWithInstructions("Be brief", mem, enabled = true)
        )
        assertEquals("${UserMemory.HEADER}\n- I am vegetarian", UserMemory.combineWithInstructions(" ", mem, true))
    }

    @Test
    fun applyToMergesIntoSingleSystemMessage() {
        val user = ChatMessage(id = "u", correlationId = "c", role = MessageRole.USER, content = "hi", createdAtEpochMs = 1)
        val mem = listOf(item("a", "I am vegetarian"))
        assertEquals(listOf(user), UserMemory.applyTo(listOf(user), mem, enabled = false, createdAtEpochMs = 0))
        assertEquals(listOf(user), UserMemory.applyTo(listOf(user), emptyList(), enabled = true, createdAtEpochMs = 0))

        val onlyMemory = UserMemory.applyTo(listOf(user), mem, enabled = true, createdAtEpochMs = 0)
        assertEquals(2, onlyMemory.size)
        assertEquals(MessageRole.SYSTEM, onlyMemory.first().role)
        assertEquals("${UserMemory.HEADER}\n- I am vegetarian", onlyMemory.first().content)

        val withInstructions = SystemPromptPolicy.applyTo(listOf(user), "Be brief", 0)
        val merged = UserMemory.applyTo(withInstructions, mem, enabled = true, createdAtEpochMs = 0)
        assertEquals(1, merged.count { it.role == MessageRole.SYSTEM })
        assertEquals("${UserMemory.HEADER}\n- I am vegetarian\n\nBe brief", merged.first().content)
        assertEquals(user, merged.last())
    }

    @Test
    fun rememberCommandParsing() {
        assertEquals("I am vegetarian", UserMemory.rememberCommand("Remember that I am vegetarian."))
        assertEquals("tabs not spaces", UserMemory.rememberCommand("  remember: tabs not spaces "))
        assertEquals("my cat is Miso", UserMemory.rememberCommand("Please remember my cat is Miso"))
        assertNull(UserMemory.rememberCommand("Do you remember my cat?"))
        assertNull(UserMemory.rememberCommand("remember?"))
        assertNull(UserMemory.rememberCommand("remember that ."))
        assertNull(UserMemory.rememberCommand("hello"))
    }

    @Test
    fun encodeDecodeRoundTripAndBadInput() {
        val list = listOf(item("a", "one", 1, pinned = true), item("b", "two", 2))
        assertEquals(list, UserMemory.decode(UserMemory.encode(list)))
        assertEquals(emptyList<MemoryItem>(), UserMemory.decode("not json"))
        val messy = """[{"id":"a","text":"x","createdAt":1},{"id":"a","text":"dup","createdAt":2},
            {"id":"","text":"y","createdAt":3},{"id":"c","text":"  ","createdAt":4},{"id":"d","text":"z","createdAt":5,"extra":1}]"""
        assertEquals(listOf("a", "d"), UserMemory.decode(messy).map { it.id })
    }

    @Test
    fun decodeReadsLegacyJsonWithoutPinnedAndPreservesDistinctSavedIds() {
        val legacy = """[{"id":"a","text":"I live in Toronto","createdAt":1}]"""
        assertEquals(listOf(item("a", "I live in Toronto", 1)), UserMemory.decode(legacy))

        val pinnedThenRestated = """[{"id":"a","text":"Pinned note","createdAt":1,"pinned":true},
            {"id":"b","text":"Pinned Note!","createdAt":2},{"id":"c","text":"Other","createdAt":3}]"""
        val decoded = UserMemory.decode(pinnedThenRestated)
        assertEquals(listOf("a", "b", "c"), decoded.map { it.id })
        assertEquals(decoded, UserMemory.decode(UserMemory.encode(decoded)))
        assertTrue(decoded.first().pinned)
    }
}
