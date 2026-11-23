package com.hydrogen.ai.model

import com.hydrogen.ai.model.PromptPackCodec.SharedPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PromptPackCodecTest {
    private fun rawCode(json: String): String =
        PromptPackCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun error(text: String): String =
        PromptPackCodec.decode(text).exceptionOrNull()?.message ?: "no error"

    @Test
    fun roundTripsThreePrompts() {
        val prompts = listOf(
            SharedPrompt("Summarize", "Summarize in 5 bullets:"),
            SharedPrompt("Translate", "Translate into natural English:"),
            SharedPrompt("Review", "Review this code:\n- bugs\n- security")
        )
        val code = PromptPackCodec.encode(prompts)
        assertTrue(code.startsWith(PromptPackCodec.PREFIX))
        assertTrue(code.none { it.isWhitespace() })
        assertEquals(prompts, PromptPackCodec.decode(code).getOrThrow())
    }

    @Test
    fun decodesACodeInsideASentence() {
        val code = PromptPackCodec.encode(listOf(SharedPrompt("Fix", "Fix my grammar")))
        val decoded = PromptPackCodec.decode("try this: $code thanks").getOrThrow()
        assertEquals(listOf(SharedPrompt("Fix", "Fix my grammar")), decoded)
    }

    @Test
    fun missingPrefixFails() {
        assertEquals("No Orbit prompt pack found.", error("just some text"))
        assertEquals("This prompt pack is from a newer Orbit version.", error("orbit-prompts:v9:abc"))
    }

    @Test
    fun damagedCodesFail() {
        assertEquals("The prompt pack is damaged.", error(PromptPackCodec.PREFIX + "!!!not*base64"))
        assertEquals("The prompt pack is damaged.", error(rawCode("hello, not json")))
        assertEquals("The prompt pack is empty.", error(PromptPackCodec.PREFIX + " next"))
        assertEquals("The prompt pack has no prompts.", error(rawCode("{\"prompts\":[]}")))
        assertEquals("The prompt pack is too long.", error(PromptPackCodec.PREFIX + "A".repeat(PromptPackCodec.MAX_TOKEN + 1)))
    }

    @Test
    fun tooManyPromptsFailOnDecodeButAreCappedOnEncode() {
        val many = (1..21).map { SharedPrompt("P$it", "Prompt number $it") }
        val items = many.joinToString(",") { "{\"title\":\"${it.title}\",\"text\":\"${it.text}\"}" }
        assertEquals("A prompt pack can hold at most 20 prompts.", error(rawCode("{\"prompts\":[$items]}")))
        val decoded = PromptPackCodec.decode(PromptPackCodec.encode(many)).getOrThrow()
        assertEquals(PromptPackCodec.MAX_PROMPTS, decoded.size)
        assertEquals(many.take(20), decoded)
    }

    @Test
    fun overLongTitleOrTextFails() {
        val longTitle = "T".repeat(PromptPackCodec.MAX_TITLE + 1)
        assertEquals(
            "Prompt titles must be 60 characters or fewer.",
            error(rawCode("{\"prompts\":[{\"title\":\"$longTitle\",\"text\":\"ok\"}]}"))
        )
        val longText = "x".repeat(PromptPackCodec.MAX_TEXT + 1)
        assertEquals(
            "Prompt texts must be 4000 characters or fewer.",
            error(rawCode("{\"prompts\":[{\"title\":\"t\",\"text\":\"$longText\"}]}"))
        )
        assertEquals(
            "Every prompt in the pack needs text.",
            error(rawCode("{\"prompts\":[{\"title\":\"t\",\"text\":\"   \"}]}"))
        )
    }

    @Test
    fun encodeDropsBlankTextsAndNamesUntitledPrompts() {
        val code = PromptPackCodec.encode(
            listOf(
                SharedPrompt("Empty", "   "),
                SharedPrompt("  ", "\n  Write a haiku about the sea and the moon and the stars tonight\nsecond line")
            )
        )
        val decoded = PromptPackCodec.decode(code).getOrThrow()
        assertEquals(1, decoded.size)
        assertEquals("Write a haiku about the sea and the moon", decoded[0].title)
        assertTrue(decoded[0].title.length <= 40)
    }

    @Test
    fun duplicatesAreKeptOnce() {
        val item = "{\"title\":\"Same\",\"text\":\"Same text\"}"
        val decoded = PromptPackCodec.decode(rawCode("{\"prompts\":[$item,$item,{\"title\":\"Same\",\"text\":\"Other\"}]}"))
            .getOrThrow()
        assertEquals(listOf(SharedPrompt("Same", "Same text"), SharedPrompt("Same", "Other")), decoded)
    }

    @Test
    fun templateBlanksAndUnicodeSurvive() {
        val prompt = SharedPrompt("Explain ✨", "Explain {{topic}} to a {{audience}} in 日本語")
        assertEquals(listOf(prompt), PromptPackCodec.decode(PromptPackCodec.encode(listOf(prompt))).getOrThrow())
    }

    @Test
    fun controlCharactersAreStripped() {
        val decoded = PromptPackCodec.decode(rawCode("{\"prompts\":[{\"title\":\"A\\u0007B\",\"text\":\"x\\r\\ny\"}]}"))
            .getOrThrow()
        assertEquals(SharedPrompt("AB", "x\ny"), decoded.single())
    }

    @Test
    fun mergeAddsNewPromptsOnTopAndSkipsKnownTexts() {
        val existing = listOf(SavedPrompt("a", "Summarize", "Summarize this"))
        val imported = listOf(
            SharedPrompt("Dup", "  Summarize this  "),
            SharedPrompt("A very long imported title that is far beyond the forty character library limit", "New one")
        )
        val (merged, added) = PromptPackCodec.mergeInto(existing, imported, idPrefix = "imp-1")
        assertEquals(1, added)
        assertEquals(listOf("imp-1-1", "a"), merged.map { it.id })
        assertTrue(merged[0].title.length <= PromptLibrary.MAX_TITLE_LENGTH)
        assertEquals("New one", merged[0].text)
        val (again, addedAgain) = PromptPackCodec.mergeInto(merged, imported, idPrefix = "imp-2")
        assertEquals(0, addedAgain)
        assertEquals(merged, again)
    }

    @Test
    fun fromSavedKeepsTitleAndText() {
        val saved = listOf(SavedPrompt("id-1", "Summarize", "Summarize this"))
        assertEquals(listOf(SharedPrompt("Summarize", "Summarize this")), PromptPackCodec.fromSaved(saved))
    }
}
