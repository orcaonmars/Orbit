package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {

    @Test
    fun no_placeholders_means_no_variables() {
        assertTrue(PromptTemplate.variables("Summarise this article").isEmpty())
    }

    @Test
    fun variables_keep_first_appearance_order_without_duplicates() {
        assertEquals(
            listOf("topic", "audience"),
            PromptTemplate.variables("Explain {{topic}} to {{audience}}. Keep {{topic}} short.")
        )
    }

    @Test
    fun spaces_inside_braces_are_trimmed() {
        assertEquals(listOf("topic"), PromptTemplate.variables("{{ topic }} and {{topic}}"))
    }

    @Test
    fun invalid_placeholders_are_plain_text() {
        val long = "a".repeat(33)
        assertTrue(PromptTemplate.variables("{{}} {{  }} {{a.b}} {{$long}} {{a{b}}").isEmpty())
        // Triple braces must not crash; the inner {{x}} is the variable.
        assertEquals(listOf("x"), PromptTemplate.variables("{{{x}}}"))
    }

    @Test
    fun at_most_max_variables_are_returned() {
        val text = (1..12).joinToString(" ") { "{{v$it}}" }
        assertEquals(PromptTemplate.MAX_VARIABLES, PromptTemplate.variables(text).size)
        assertEquals("v1", PromptTemplate.variables(text).first())
    }

    @Test
    fun fill_replaces_every_occurrence() {
        assertEquals(
            "Explain tides to kids. Keep tides short.",
            PromptTemplate.fill("Explain {{topic}} to {{ audience }}. Keep {{topic}} short.", mapOf("topic" to "tides", "audience" to "kids"))
        )
    }

    @Test
    fun missing_values_stay_as_written() {
        assertEquals("Hi Ana, re {{ subject }}", PromptTemplate.fill("Hi {{name}}, re {{ subject }}", mapOf("name" to "Ana")))
    }

    @Test
    fun values_are_never_filled_again() {
        assertEquals("say {{b}} then {{b}}", PromptTemplate.fill("say {{a}} then {{b}}", mapOf("a" to "{{b}}")))
        assertEquals("cost \$5 \\ more", PromptTemplate.fill("cost {{p}}", mapOf("p" to "\$5 \\ more")))
    }

    @Test
    fun empty_values_leave_text_unchanged() {
        val text = "Translate {{text}} into {{target_language}}"
        assertEquals(text, PromptTemplate.fill(text, emptyMap()))
    }

    @Test
    fun examples_all_have_blanks_and_are_added_once() {
        PromptTemplate.EXAMPLES.forEach { assertTrue(it, PromptTemplate.variables(it).isNotEmpty()) }
        val once = PromptTemplate.withExamples(emptyList(), "ex")
        assertEquals(PromptTemplate.EXAMPLES.size, once.size)
        assertEquals(once, PromptTemplate.withExamples(once, "ex2"))
        assertEquals(listOf("recipient", "subject"), PromptTemplate.variables(PromptTemplate.EXAMPLES[0]))
    }

    @Test
    fun blanked_preview_hides_braces_on_one_line() {
        assertEquals("Plan a ___-day trip to ___.", PromptTemplate.blanked("Plan a {{days}}-day trip to {{ place }}."))
        assertEquals("Translate into ___: ___ {{a.b}}", PromptTemplate.blanked("Translate into {{language}}:\n\n{{text}} {{a.b}}"))
    }

    @Test
    fun labels_read_like_words() {
        assertEquals("Topic", PromptTemplate.label("topic"))
        assertEquals("Target language", PromptTemplate.label("target_language"))
        assertEquals("Due date", PromptTemplate.label("due-date"))
    }
}
