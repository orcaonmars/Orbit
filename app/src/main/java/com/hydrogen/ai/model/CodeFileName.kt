package com.hydrogen.ai.model

import java.util.Locale

object CodeFileName {
    const val FALLBACK_BASE = "orbit-snippet"
    const val MAX_BASE_LENGTH = 60

    private val FORBIDDEN_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private val MULTI_DASH_REGEX = Regex("-{2,}")
    private val TOP_LEVEL_TYPE_REGEX = Regex(
        """^[ \t]*(?:(?:public|private|protected|internal|abstract|final|open|sealed|data|value|inline|enum|annotation|static|strictfp|expect|actual)\s+)*(?:class|object|interface)\s+([A-Za-z][A-Za-z0-9_]*)(?=$|[\s:{(<,;])""",
        RegexOption.MULTILINE
    )

    fun extensionFor(language: String?): String {
        val normalized = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.isEmpty()) return "txt"
        return when (normalized) {
            "kotlin", "kt" -> "kt"
            "kts" -> "kts"
            "java" -> "java"
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "tsx" -> "tsx"
            "jsx" -> "jsx"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "yaml", "yml" -> "yml"
            "xml" -> "xml"
            "sql" -> "sql"
            "bash", "sh", "shell", "zsh" -> "sh"
            "powershell", "ps1", "pwsh" -> "ps1"
            "bat", "cmd" -> "bat"
            "c" -> "c"
            "cpp", "c++", "cc" -> "cpp"
            "csharp", "cs", "c#" -> "cs"
            "go", "golang" -> "go"
            "rust", "rs" -> "rs"
            "ruby", "rb" -> "rb"
            "php" -> "php"
            "swift" -> "swift"
            "dart" -> "dart"
            "markdown", "md" -> "md"
            "toml" -> "toml"
            "ini" -> "ini"
            "dockerfile" -> ""
            "csv" -> "csv"
            "r" -> "r"
            "lua" -> "lua"
            else -> "txt"
        }
    }

    fun suggest(language: String?, code: String, index: Int = 1): String {
        val normalized = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized == "dockerfile") {
            return "Dockerfile"
        }

        val ext = extensionFor(language)
        val rawBase = when {
            normalized == "java" || normalized == "kotlin" || normalized == "kt" || normalized == "kts" -> {
                val match = TOP_LEVEL_TYPE_REGEX.find(code)
                match?.groupValues?.getOrNull(1) ?: fallbackWithIndex(index)
            }
            else -> fallbackWithIndex(index)
        }

        val sanitized = sanitizeBase(rawBase)
        // Device names cannot be saved on Windows, even when a file extension is present.
        val safeBase = if (isReservedDeviceName(sanitized)) fallbackWithIndex(index) else sanitized
        val candidate = if (ext.isEmpty()) safeBase else "$safeBase.$ext"
        return if (candidate.any { it in FORBIDDEN_CHARS }) {
            if (ext.isEmpty()) FALLBACK_BASE else "$FALLBACK_BASE.$ext"
        } else {
            candidate
        }
    }

    private fun isReservedDeviceName(name: String): Boolean =
        Regex("""^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$""")
            .matches(name.substringBefore('.').uppercase(Locale.ROOT))

    private fun fallbackWithIndex(index: Int): String =
        if (index > 1) "$FALLBACK_BASE-$index" else FALLBACK_BASE

    private fun sanitizeBase(raw: String): String {
        val replaced = buildString(raw.length) {
            for (ch in raw) {
                if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.') {
                    append(ch)
                } else {
                    append('-')
                }
            }
        }
        val collapsed = replaced.replace(MULTI_DASH_REGEX, "-")
        val trimmed = collapsed.trim('-', '.')
        val capped = if (trimmed.length > MAX_BASE_LENGTH) {
            trimmed.substring(0, MAX_BASE_LENGTH).trimEnd('-', '.')
        } else {
            trimmed
        }
        return capped.ifEmpty { FALLBACK_BASE }
    }
}
