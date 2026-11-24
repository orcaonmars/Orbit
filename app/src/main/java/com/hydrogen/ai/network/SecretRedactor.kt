package com.hydrogen.ai.network

/**
 * Hides API keys and tokens in text that may be shown, copied or saved (error details, the error log, a support
 * report). Some services echo part of a rejected key in their error message, and a pasted curl line can carry one.
 * Redaction is best effort and pattern based, so error text is also never sent anywhere by Orbit itself.
 */
object SecretRedactor {
    const val HIDDEN = "[hidden]"

    /** Known key shapes, longest prefixes first so "sk-ant-" wins over "sk-". */
    private val KEY_SHAPES = listOf(
        Regex("""sk-ant-[A-Za-z0-9_\-]{6,}"""),
        Regex("""sk-or-[A-Za-z0-9_\-]{6,}"""),
        Regex("""sk-proj-[A-Za-z0-9_\-]{6,}"""),
        Regex("""sk-svcacct-[A-Za-z0-9_\-]{6,}"""),
        Regex("""sk-[A-Za-z0-9_\-*]{12,}"""),
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""gsk_[A-Za-z0-9]{10,}"""),
        Regex("""xai-[A-Za-z0-9]{10,}"""),
        Regex("""hf_[A-Za-z0-9]{10,}"""),
        Regex("""nvapi-[A-Za-z0-9_\-]{10,}"""),
        Regex("""fw_[A-Za-z0-9]{10,}"""),
        Regex("""csk-[A-Za-z0-9]{10,}"""),
        // Shorter key-shaped URL segments must not leak in diagnostic paths.
        // Keep the separators so the remaining route is still useful.
        Regex("""(?i)(?<=/)sk-[A-Za-z0-9_\-]{6,}(?=[/?#\s"'>,;)]|$)""")
    )

    /** "Bearer abc...", "x-api-key: abc...", "api_key=abc..." and similar, whatever the key looks like. */
    private val LABELLED = listOf(
        Regex("""(?i)(bearer\s+)([A-Za-z0-9._~+/=\-]{8,})"""),
        Regex("""(?i)((?:x-api-key|x-goog-api-key|authorization|api[_-]?key|access[_-]?token)["']?\s*[:=]\s*["']?)([^\s"',}&]{8,})"""),
        Regex("""(?i)([?&](?:key|token|api_key)=)([^\s&"']{8,})"""),
        Regex("""(?i)((?:token|secret|password|api[_-]?key)["']?\s*[:=]\s*["']?)([^\s"',}&]{6,})""")
    )

    fun redact(text: String, knownSecrets: Collection<String?> = emptyList()): String {
        if (text.isEmpty()) return text
        var out = text
        for (secret in knownSecrets) {
            val s = secret?.trim().orEmpty()
            if (s.length >= 6) out = out.replace(s, HIDDEN)
        }
        for (pattern in LABELLED) {
            out = pattern.replace(out) { m -> m.groupValues[1] + HIDDEN }
        }
        for (pattern in KEY_SHAPES) {
            out = pattern.replace(out, HIDDEN)
        }
        return out
    }
}
