package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryTest {
    private fun item(id: String, text: String, at: Long = 0) = MemoryItem(id, text, at)

    @Test
    fun addPutsNewItemFirstAndCleansWhitespace() {
        val list = UserMemory.add(listOf(item("a", "I write Kotlin")), "  I am\n vegetarian  ", "b", 5)
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals("I am vegetarian", list.first().text)
        assertEquals(5L, list.first().createdAt)
    }

    @Test
    fun addIgnoresBlankAndCaseInsensitiveDuplicates() {
        val start = listOf(item("a", "I write Kotlin"))
        assertEquals(start, UserMemory.add(start, "   ", "b", 1))
        assertEquals(start, UserMemory.add(start, "i WRITE kotlin", "b", 1))
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
    fun removeAndUpdate() {
        val start = listOf(item("a", "one"), item("b", "two"))
        assertEquals(listOf("b"), UserMemory.remove(start, "a").map { it.id })
        assertEquals("uno", UserMemory.update(start, "a", " uno ").first().text)
        assertEquals(start, UserMemory.update(start, "a", "TWO"))
        assertEquals(start, UserMemory.update(start, "a", "  "))
        assertEquals("ONE", UserMemory.update(start, "a", "ONE").first().text)
    }

    @Test
    fun systemBlockIsOldestFirstAndBounded() {
        assertNull(UserMemory.systemBlock(emptyList()))
        val block = UserMemory.systemBlock(listOf(item("b", "newer"), item("a", "older")))
        assertEquals("${UserMemory.HEADER}\n- older\n- newer", block)
        val many = (0 until UserMemory.MAX_ITEMS).map { item("i$it", "y".repeat(UserMemory.MAX_ITEM_CHARS)) }
        val big = UserMemory.systemBlock(many)!!
        assertTrue(big.length <= UserMemory.MAX_BLOCK_CHARS)
        assertTrue(big.startsWith(UserMemory.HEADER))
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
        val list = listOf(item("a", "one", 1), item("b", "two", 2))
        assertEquals(list, UserMemory.decode(UserMemory.encode(list)))
        assertEquals(emptyList<MemoryItem>(), UserMemory.decode("not json"))
        val messy = """[{"id":"a","text":"x","createdAt":1},{"id":"a","text":"dup","createdAt":2},
            {"id":"","text":"y","createdAt":3},{"id":"c","text":"  ","createdAt":4},{"id":"d","text":"z","createdAt":5,"extra":1}]"""
        assertEquals(listOf("a", "d"), UserMemory.decode(messy).map { it.id })
    }
}
