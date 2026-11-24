package com.hydrogen.ai.model

enum class CodeTokenKind {
    PLAIN,
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER
}

data class CodeToken(
    val start: Int,
    val end: Int,
    val kind: CodeTokenKind
)

/**
 * Pure Kotlin, linear-time syntax highlighter for Markdown fenced code blocks.
 * Never throws; caps tokenization at [MAX_INPUT_CHARS] characters.
 */
object CodeHighlighter {
    const val MAX_INPUT_CHARS = 100_000

    private data class LanguageSpec(
        val keywords: Set<String>,
        val slashSlashComment: Boolean = true,
        val blockComment: Boolean = true,
        val nestedBlockComment: Boolean = false,
        val hashComment: Boolean = false,
        val bashHashBoundary: Boolean = false,
        val doubleQuote: Boolean = true,
        val singleQuote: Boolean = true,
        val singleQuoteEscape: Boolean = true,
        val tripleDoubleQuote: Boolean = false,
        val tripleSingleQuote: Boolean = false,
        val tripleQuoteAllowsEscape: Boolean = true,
        val backtickString: Boolean = false,
        val backtickEscape: Boolean = true,
        val rustCharOrLifetime: Boolean = false
    )

    private val KOTLIN_SPEC = LanguageSpec(
        keywords = setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "continue", "crossinline", "data", "do",
            "else", "enum", "expect", "external", "false", "final", "finally", "for", "fun",
            "if", "import", "in", "infix", "init", "inline", "inner", "interface", "internal",
            "is", "lateinit", "noinline", "null", "object", "open", "operator", "out",
            "override", "package", "private", "protected", "public", "reified", "return",
            "sealed", "super", "suspend", "tailrec", "this", "throw", "true", "try",
            "typealias", "typeof", "val", "var", "vararg", "when", "where", "while"
        ),
        nestedBlockComment = true,
        tripleDoubleQuote = true,
        tripleQuoteAllowsEscape = false
    )

    private val JAVA_SPEC = LanguageSpec(
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "record",
            "return", "sealed", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true", "try",
            "var", "void", "volatile", "while", "yield"
        )
    )

    private val PYTHON_SPEC = LanguageSpec(
        keywords = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
            "match", "case"
        ),
        slashSlashComment = false,
        blockComment = false,
        hashComment = true,
        tripleDoubleQuote = true,
        tripleSingleQuote = true,
        tripleQuoteAllowsEscape = true
    )

    private val JS_TS_SPEC = LanguageSpec(
        keywords = setOf(
            "abstract", "any", "as", "async", "await", "bigint", "boolean", "break",
            "case", "catch", "class", "const", "continue", "debugger", "declare",
            "default", "delete", "do", "else", "enum", "export", "extends", "false",
            "finally", "for", "from", "function", "if", "implements", "import", "in",
            "infer", "instanceof", "interface", "is", "keyof", "let", "module",
            "namespace", "never", "new", "null", "number", "object", "of", "override",
            "package", "private", "protected", "public", "readonly", "return",
            "satisfies", "static", "string", "super", "switch", "symbol", "this",
            "throw", "true", "try", "type", "typeof", "undefined", "unknown", "var",
            "void", "while", "with", "yield"
        ),
        backtickString = true,
        backtickEscape = true
    )

    private val JSON_SPEC = LanguageSpec(
        keywords = setOf("true", "false", "null"),
        slashSlashComment = false,
        blockComment = false,
        singleQuote = false
    )

    private val BASH_SPEC = LanguageSpec(
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "select",
            "while", "until", "do", "done", "in", "function", "time", "coproc",
            "local", "export", "readonly", "declare", "typeset", "unset", "return",
            "exit", "break", "continue", "shift", "source", "eval", "exec", "trap",
            "true", "false"
        ),
        slashSlashComment = false,
        blockComment = false,
        hashComment = true,
        bashHashBoundary = true,
        singleQuoteEscape = false
    )

    private val CPP_SPEC = LanguageSpec(
        keywords = setOf(
            "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
            "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t",
            "class", "compl", "concept", "const", "consteval", "constexpr", "constinit",
            "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype",
            "default", "delete", "do", "double", "dynamic_cast", "else", "enum",
            "explicit", "export", "extern", "false", "float", "for", "friend", "goto",
            "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept",
            "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private",
            "protected", "public", "register", "reinterpret_cast", "requires", "return",
            "short", "signed", "sizeof", "static", "static_assert", "static_cast",
            "struct", "switch", "template", "this", "thread_local", "throw", "true",
            "try", "typedef", "typeid", "typename", "union", "unsigned", "using",
            "virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq"
        )
    )

    private val GO_SPEC = LanguageSpec(
        keywords = setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
            "map", "package", "range", "return", "select", "struct", "switch", "type",
            "var", "true", "false", "iota", "nil"
        ),
        backtickString = true,
        backtickEscape = false
    )

    private val RUST_SPEC = LanguageSpec(
        keywords = setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn",
            "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
            "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true", "type",
            "unsafe", "use", "where", "while", "yield"
        ),
        nestedBlockComment = true,
        singleQuote = false,
        rustCharOrLifetime = true
    )

    private fun specFor(language: String): LanguageSpec? =
        when (language.trim().lowercase()) {
            "kotlin", "kt", "kts" -> KOTLIN_SPEC
            "java" -> JAVA_SPEC
            "python", "py" -> PYTHON_SPEC
            "javascript", "js", "jsx", "typescript", "ts", "tsx" -> JS_TS_SPEC
            "json" -> JSON_SPEC
            "bash", "sh", "zsh", "shell" -> BASH_SPEC
            "c", "cpp", "c++", "cc", "cxx", "h", "hpp" -> CPP_SPEC
            "go", "golang" -> GO_SPEC
            "rust", "rs" -> RUST_SPEC
            else -> null
        }

    fun tokenize(code: String, language: String): List<CodeToken> {
        val spec = specFor(language) ?: return emptyList()
        val limit = minOf(code.length, MAX_INPUT_CHARS)
        if (limit == 0) return emptyList()

        val out = ArrayList<CodeToken>(64)
        var i = 0
        while (i < limit) {
            val c = code[i]
            when {
                spec.slashSlashComment && c == '/' && i + 1 < limit && code[i + 1] == '/' -> {
                    var j = i + 2
                    while (j < limit && code[j] != '\n' && code[j] != '\r') j++
                    emit(out, i, j, CodeTokenKind.COMMENT)
                    i = j
                }

                spec.blockComment && c == '/' && i + 1 < limit && code[i + 1] == '*' -> {
                    var j = i + 2
                    var depth = 1
                    while (j < limit && depth > 0) {
                        if (spec.nestedBlockComment && code[j] == '/' && j + 1 < limit && code[j + 1] == '*') {
                            depth++
                            j += 2
                        } else if (code[j] == '*' && j + 1 < limit && code[j + 1] == '/') {
                            depth--
                            j += 2
                        } else {
                            j++
                        }
                    }
                    emit(out, i, j, CodeTokenKind.COMMENT)
                    i = j
                }

                spec.hashComment && c == '#' && isHashCommentStart(code, i, spec.bashHashBoundary) -> {
                    var j = i + 1
                    while (j < limit && code[j] != '\n' && code[j] != '\r') j++
                    emit(out, i, j, CodeTokenKind.COMMENT)
                    i = j
                }

                spec.tripleDoubleQuote && c == '"' && i + 2 < limit && code[i + 1] == '"' && code[i + 2] == '"' -> {
                    val end = scanTripleQuote(code, i + 3, limit, '"', spec.tripleQuoteAllowsEscape)
                    emit(out, i, end, CodeTokenKind.STRING)
                    i = end
                }

                spec.tripleSingleQuote && c == '\'' && i + 2 < limit && code[i + 1] == '\'' && code[i + 2] == '\'' -> {
                    val end = scanTripleQuote(code, i + 3, limit, '\'', spec.tripleQuoteAllowsEscape)
                    emit(out, i, end, CodeTokenKind.STRING)
                    i = end
                }

                spec.doubleQuote && c == '"' -> {
                    val end = scanQuoted(code, i + 1, limit, '"', allowEscape = true)
                    emit(out, i, end, CodeTokenKind.STRING)
                    i = end
                }

                spec.rustCharOrLifetime && c == '\'' -> {
                    val charEnd = scanRustCharLiteral(code, i, limit)
                    if (charEnd > i) {
                        emit(out, i, charEnd, CodeTokenKind.STRING)
                        i = charEnd
                    } else {
                        emit(out, i, i + 1, CodeTokenKind.PLAIN)
                        i++
                    }
                }

                spec.singleQuote && c == '\'' -> {
                    val end = scanQuoted(code, i + 1, limit, '\'', allowEscape = spec.singleQuoteEscape)
                    emit(out, i, end, CodeTokenKind.STRING)
                    i = end
                }

                spec.backtickString && c == '`' -> {
                    val end = scanQuoted(code, i + 1, limit, '`', allowEscape = spec.backtickEscape)
                    emit(out, i, end, CodeTokenKind.STRING)
                    i = end
                }

                c in '0'..'9' -> {
                    val end = scanNumber(code, i, limit)
                    emit(out, i, end, CodeTokenKind.NUMBER)
                    i = end
                }

                isIdentStart(c) -> {
                    var j = i + 1
                    while (j < limit && isIdentPart(code[j])) j++
                    val word = code.substring(i, j)
                    val kind = if (word in spec.keywords) CodeTokenKind.KEYWORD else CodeTokenKind.PLAIN
                    emit(out, i, j, kind)
                    i = j
                }

                else -> {
                    emit(out, i, i + 1, CodeTokenKind.PLAIN)
                    i++
                }
            }
        }
        return out
    }

    private fun emit(out: ArrayList<CodeToken>, start: Int, end: Int, kind: CodeTokenKind) {
        if (start >= end) return
        if (kind == CodeTokenKind.PLAIN && out.isNotEmpty()) {
            val last = out[out.lastIndex]
            if (last.kind == CodeTokenKind.PLAIN && last.end == start) {
                out[out.lastIndex] = CodeToken(last.start, end, CodeTokenKind.PLAIN)
                return
            }
        }
        out.add(CodeToken(start, end, kind))
    }

    private fun isHashCommentStart(code: String, index: Int, bashBoundary: Boolean): Boolean {
        if (!bashBoundary || index == 0) return true
        val prev = code[index - 1]
        return prev.isWhitespace() || prev in ";|&(){}"
    }

    private fun scanQuoted(
        code: String,
        start: Int,
        limit: Int,
        quote: Char,
        allowEscape: Boolean
    ): Int {
        var j = start
        while (j < limit) {
            val c = code[j]
            if (allowEscape && c == '\\') {
                j = minOf(j + 2, limit)
            } else if (c == quote) {
                return j + 1
            } else {
                j++
            }
        }
        return limit
    }

    private fun scanTripleQuote(
        code: String,
        start: Int,
        limit: Int,
        quote: Char,
        allowEscape: Boolean
    ): Int {
        var j = start
        while (j < limit) {
            val c = code[j]
            if (allowEscape && c == '\\') {
                j = minOf(j + 2, limit)
            } else if (c == quote && j + 2 < limit && code[j + 1] == quote && code[j + 2] == quote) {
                return j + 3
            } else {
                j++
            }
        }
        return limit
    }

    private fun scanRustCharLiteral(code: String, start: Int, limit: Int): Int {
        if (start + 1 >= limit) return -1
        val next = code[start + 1]
        if (next == '\n' || next == '\r' || next == '\'') return -1
        if (next != '\\') {
            return if (start + 2 < limit && code[start + 2] == '\'') start + 3 else -1
        }
        var j = start + 2
        if (j < limit && code[j] != '\n' && code[j] != '\r') j++ else return -1
        val maxEnd = minOf(limit, start + 14)
        while (j < maxEnd) {
            val c = code[j]
            if (c == '\'') return j + 1
            if (c == '\n' || c == '\r') return -1
            j++
        }
        return -1
    }

    private fun scanNumber(code: String, start: Int, limit: Int): Int {
        var j = start
        if (code[j] == '0' && j + 1 < limit) {
            when (code[j + 1]) {
                'x', 'X' -> {
                    j += 2
                    while (j < limit && (isHexDigit(code[j]) || code[j] == '_')) j++
                    return scanNumericSuffix(code, j, limit)
                }
                'b', 'B' -> {
                    j += 2
                    while (j < limit && (code[j] == '0' || code[j] == '1' || code[j] == '_')) j++
                    return scanNumericSuffix(code, j, limit)
                }
                'o', 'O' -> {
                    j += 2
                    while (j < limit && (code[j] in '0'..'7' || code[j] == '_')) j++
                    return scanNumericSuffix(code, j, limit)
                }
            }
        }

        while (j < limit && (code[j] in '0'..'9' || code[j] == '_')) j++

        if (j + 1 < limit && code[j] == '.' && code[j + 1] in '0'..'9') {
            j += 2
            while (j < limit && (code[j] in '0'..'9' || code[j] == '_')) j++
        }

        if (j < limit && (code[j] == 'e' || code[j] == 'E')) {
            val expStart = j
            j++
            if (j < limit && (code[j] == '+' || code[j] == '-')) j++
            if (j < limit && code[j] in '0'..'9') {
                while (j < limit && (code[j] in '0'..'9' || code[j] == '_')) j++
            } else {
                j = expStart
            }
        }

        return scanNumericSuffix(code, j, limit)
    }

    private fun scanNumericSuffix(code: String, start: Int, limit: Int): Int {
        if (start >= limit) return start
        val c = code[start]
        return when (c) {
            'l', 'L', 'f', 'F', 'd', 'D', 'u', 'U' -> {
                var j = start + 1
                while (j < limit && (code[j] in '0'..'9' || code[j] in "uUlLfF")) j++
                j
            }
            'i' -> {
                if (start + 1 < limit && code[start + 1] in '0'..'9') {
                    var j = start + 2
                    while (j < limit && code[j] in '0'..'9') j++
                    j
                } else {
                    start
                }
            }
            else -> start
        }
    }

    private fun isHexDigit(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun isIdentStart(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == '$'

    private fun isIdentPart(c: Char): Boolean =
        isIdentStart(c) || c in '0'..'9'
}
