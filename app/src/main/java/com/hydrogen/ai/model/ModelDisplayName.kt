package com.hydrogen.ai.model

/**
 * Short, human-friendly model label for the top bar: drops publisher/folder prefixes
 * ("local-private/gemma-3" -> "gemma-3", "models/gemini-2.5-flash" -> "gemini-2.5-flash")
 * and Ollama's default ":latest" tag. Never throws; blank ids read as "Choose a model".
 */
object ModelDisplayName {
    const val PLACEHOLDER = "Choose a model"
    const val MAX_CHARS = 48

    fun of(modelId: String): String {
        val trimmed = modelId.trim().trimEnd('/', '\\')
        if (trimmed.isEmpty()) return PLACEHOLDER
        val lastSegment = trimmed.substringAfterLast('/').substringAfterLast('\\').ifBlank { trimmed }
        val withoutTag = lastSegment.removeSuffix(":latest").ifBlank { lastSegment }
        return cap(withoutTag)
    }

    /**
     * Title-style name for labels: "local-private/gemma-4-26b-a4b-it" -> "Gemma 4 26B A4B",
     * "gemini-2.5-flash" -> "Gemini 2.5 Flash", "qwen2.5:7b-instruct-q4_K_M" -> "Qwen2.5 7B",
     * "claude-3-5-sonnet-20241022" -> "Claude 3.5 Sonnet", "gpt-4o-mini" -> "GPT-4o Mini".
     * Drops build noise (instruct/it/chat, quantization, file formats, dates). Pickers keep showing the full id.
     */
    fun friendly(modelId: String): String {
        val base = of(modelId)
        if (base == PLACEHOLDER || base.endsWith("…")) return base
        // Quantization tags contain underscores ("q4_K_M"), so drop noise before splitting on "_".
        val tokens = base.replace(DATE_SUFFIX, "").replace(MONTH_DAY_END, "")
            .split('-', ':', ' ')
            .filter { !NOISE.matches(it.lowercase()) }
            .flatMap { it.split('_') }
            .filter { it.isNotEmpty() && !NOISE.matches(it.lowercase()) }
            .toMutableList()
        if (tokens.isEmpty()) return base
        // "3-5" in "claude-3-5-sonnet" is version 3.5.
        var i = 0
        while (i < tokens.size - 1) {
            if (SINGLE_DIGIT.matches(tokens[i]) && SINGLE_DIGIT.matches(tokens[i + 1])) {
                tokens[i] = tokens[i] + "." + tokens.removeAt(i + 1)
            }
            i++
        }
        val words = tokens.map(::formatToken).toMutableList()
        // OpenAI style keeps the hyphen: "GPT-4o", "GPT-OSS".
        if (words.size >= 2 && words[0] == "GPT") {
            val head = "GPT-" + words[1]
            words.subList(0, 2).clear()
            words.add(0, head)
        }
        return cap(words.joinToString(" "))
    }

    /**
     * Compact label for places that already show the service, such as the composer's model chip: the friendly name
     * without its leading family word when what is left still names the model ("Gemini 2.5 Flash" -> "2.5 Flash",
     * "Claude Sonnet 4.5" -> "Sonnet 4.5"). Numbered or sized ids keep their family ("Gemma 4 26B A4B",
     * "Qwen2.5 7B", "Llama 3.1 8B") because a leftover size alone would not say which model it is.
     */
    fun short(modelId: String): String {
        val full = friendly(modelId)
        if (full == PLACEHOLDER || full.endsWith("…")) return full
        val words = full.split(' ')
        if (words.size < 3) return full
        val head = words.first()
        // "GPT-4o", "Qwen2.5" and "o3" carry the model's name in the first word itself, so it stays.
        if (head.isEmpty() || !head.all(Char::isLetter)) return full
        val rest = words.drop(1)
        // The shorthand has to keep naming the model: a version, or a variant word such as "Flash" or "Sonnet".
        val versionLeads = rest.first().let { word -> word.any(Char::isLetter) || word.contains('.') }
        val variantWord = rest.any { word -> word.none(Char::isDigit) && word.any(Char::isLetter) }
        if (!versionLeads || !variantWord) return full
        return cap(rest.joinToString(" "))
    }

    private fun formatToken(token: String): String {
        val lower = token.lowercase()
        BRANDS[lower]?.let { return it }
        return when {
            SIZE.matches(lower) || ACTIVE_SIZE.matches(lower) -> token.uppercase()
            OPENAI_REASONING.matches(lower) -> lower
            SHORT_CODE.matches(lower) -> token.uppercase()
            token.first().isLetter() -> token.first().uppercase() + token.drop(1)
            else -> token
        }
    }

    private fun cap(text: String): String = if (text.length > MAX_CHARS) text.take(MAX_CHARS - 1) + "…" else text

    private val DATE_SUFFIX = Regex("""[-_](20\d\d-?\d\d-?\d\d)(?=$|[-_:])""")
    private val NOISE = Regex(
        """it|instruct|chat|hf|gguf|ggml|mlx|awq|gptq|exl2|latest|preview|(i?q\d(_[a-z0-9]+)*)|((fp|bf)16)|fp8|\d+bit"""
    )
    private val MONTH_DAY_END = Regex("""-\d\d-\d\d$""")
    private val SINGLE_DIGIT = Regex("""\d""")
    private val SIZE = Regex("""\d+(\.\d+)?[bmk]""")
    private val ACTIVE_SIZE = Regex("""[ae]\d+(\.\d+)?b""")
    private val OPENAI_REASONING = Regex("""o\d""")
    private val SHORT_CODE = Regex("""[a-z]\d""")
    private val BRANDS = mapOf(
        "gpt" to "GPT", "oss" to "OSS", "deepseek" to "DeepSeek", "olmo" to "OLMo", "smollm" to "SmolLM",
        "internlm" to "InternLM", "glm" to "GLM", "lfm" to "LFM", "vl" to "VL", "moe" to "MoE"
    )
}
