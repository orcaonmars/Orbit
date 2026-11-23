package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun assertTokenInvariants(code: String, tokens: List<CodeToken>, expectedEnd: Int = code.length) {
        var cursor = 0
        for (token in tokens) {
            assertTrue("Token must be non-empty: $token", token.start < token.end)
            assertTrue("Token out of bounds: $token for len=${code.length}", token.start >= 0 && token.end <= expectedEnd)
            assertTrue("Tokens must be sorted & non-overlapping: prev=$cursor, cur=${token.start}", token.start >= cursor)
            cursor = token.end
        }
        if (expectedEnd > 0 && tokens.isNotEmpty()) {
            assertEquals(0, tokens.first().start)
            assertEquals(expectedEnd, tokens.last().end)
            assertEquals(code.substring(0, expectedEnd), tokens.joinToString("") { code.substring(it.start, it.end) })
        }
    }

    private fun texts(code: String, tokens: List<CodeToken>, kind: CodeTokenKind): List<String> =
        tokens.filter { it.kind == kind }.map { code.substring(it.start, it.end) }

    @Test
    fun keywordsAndAliasesAreHighlightedWithoutSplittingIdentifiers() {
        val kotlinCode = "val value = if (true) return else null"
        val kotlinTokens = CodeHighlighter.tokenize(kotlinCode, "  KOTLIN ")
        assertTokenInvariants(kotlinCode, kotlinTokens)
        assertEquals(listOf("val", "if", "true", "return", "else", "null"), texts(kotlinCode, kotlinTokens, CodeTokenKind.KEYWORD))

        val pyCode = "def fn1(x):\n    return None if x is False else True"
        val pyTokens = CodeHighlighter.tokenize(pyCode, "py")
        assertTokenInvariants(pyCode, pyTokens)
        assertEquals(listOf("def", "return", "None", "if", "is", "False", "else", "True"), texts(pyCode, pyTokens, CodeTokenKind.KEYWORD))

        val cases = listOf(
            "java" to "public class App { void run() {} }",
            "js" to "const fn = async () => await Promise.resolve(null);",
            "ts" to "interface User { readonly id: number; }",
            "json" to "{\"ok\": true, \"err\": null}",
            "sh" to "if true; then export X=1; fi",
            "cpp" to "constexpr int main() { return 0; }",
            "go" to "func main() { defer close(ch) }",
            "rust" to "pub fn main() { let mut x = true; }"
        )
        for ((lang, sample) in cases) {
            val tokens = CodeHighlighter.tokenize(sample, lang)
            assertTokenInvariants(sample, tokens)
            assertTrue("Expected keywords for $lang", tokens.any { it.kind == CodeTokenKind.KEYWORD })
        }
    }

    @Test
    fun stringsHandleBackslashEscapesTripleQuotesAndRustLifetimes() {
        val js = """const a = "he said \"hi\\\"", b = 'it\'s ok', c = `tmpl\``;"""
        val jsTokens = CodeHighlighter.tokenize(js, "JavaScript")
        assertTokenInvariants(js, jsTokens)
        assertEquals(
            listOf("\"he said \\\"hi\\\\\\\"\"", "'it\\'s ok'", "`tmpl\\``"),
            texts(js, jsTokens, CodeTokenKind.STRING)
        )

        val py = "a = \"\"\"line1\n\\\"escaped\\\"\"\"\"\nb = '''single\ntriple'''\n"
        val pyTokens = CodeHighlighter.tokenize(py, "Python")
        assertTokenInvariants(py, pyTokens)
        assertEquals(
            listOf("\"\"\"line1\n\\\"escaped\\\"\"\"\"", "'''single\ntriple'''"),
            texts(py, pyTokens, CodeTokenKind.STRING)
        )

        val rust = "fn f<'a>(s: &'a str) -> char { '\\'' }"
        val rustTokens = CodeHighlighter.tokenize(rust, "rust")
        assertTokenInvariants(rust, rustTokens)
        assertEquals(listOf("'\\''"), texts(rust, rustTokens, CodeTokenKind.STRING))
        assertEquals(listOf("fn"), texts(rust, rustTokens, CodeTokenKind.KEYWORD))
    }

    @Test
    fun commentsRespectLanguageSyntaxAndNesting() {
        val kt = "/* outer /* inner */ still comment */\nval x = 1 // trailing\n"
        val ktTokens = CodeHighlighter.tokenize(kt, "kt")
        assertTokenInvariants(kt, ktTokens)
        assertEquals(
            listOf("/* outer /* inner */ still comment */", "// trailing"),
            texts(kt, ktTokens, CodeTokenKind.COMMENT)
        )
        assertEquals(listOf("val"), texts(kt, ktTokens, CodeTokenKind.KEYWORD))

        val bash = "echo foo#bar # real comment\n# full line\n"
        val bashTokens = CodeHighlighter.tokenize(bash, "bash")
        assertTokenInvariants(bash, bashTokens)
        assertEquals(
            listOf("# real comment", "# full line"),
            texts(bash, bashTokens, CodeTokenKind.COMMENT)
        )

        val cpp = "#include <stdio.h>\n// c++ comment\nint x = 0;"
        val cppTokens = CodeHighlighter.tokenize(cpp, "c++")
        assertTokenInvariants(cpp, cppTokens)
        assertEquals(listOf("// c++ comment"), texts(cpp, cppTokens, CodeTokenKind.COMMENT))

        val json = "{\"url\": \"https://a.b\", \"n\": 1} // not comment"
        val jsonTokens = CodeHighlighter.tokenize(json, "JSON")
        assertTokenInvariants(json, jsonTokens)
        assertTrue(texts(json, jsonTokens, CodeTokenKind.COMMENT).isEmpty())
    }

    @Test
    fun numbersAndRangeOperatorsTokenizeCleanly() {
        val code = "val list = 1..10; val n = 0xFF_0A + 0b1010 + 1.5e-3 + 42L + 3.14f + 1.toString()"
        val tokens = CodeHighlighter.tokenize(code, "kotlin")
        assertTokenInvariants(code, tokens)
        assertEquals(
            listOf("1", "10", "0xFF_0A", "0b1010", "1.5e-3", "42L", "3.14f", "1"),
            texts(code, tokens, CodeTokenKind.NUMBER)
        )
    }

    @Test
    fun unterminatedStringsAndCommentsRunToEndWithoutThrowing() {
        val badStr = "val s = \"unterminated \\\" quote \\"
        val strTokens = CodeHighlighter.tokenize(badStr, "kotlin")
        assertTokenInvariants(badStr, strTokens)
        assertEquals(listOf("\"unterminated \\\" quote \\"), texts(badStr, strTokens, CodeTokenKind.STRING))

        val badTriple = "x = \"\"\"unclosed\ntriple"
        val tripleTokens = CodeHighlighter.tokenize(badTriple, "python")
        assertTokenInvariants(badTriple, tripleTokens)
        assertEquals(listOf("\"\"\"unclosed\ntriple"), texts(badTriple, tripleTokens, CodeTokenKind.STRING))

        val badComment = "int x = 1; /* unclosed\ncomment"
        val commentTokens = CodeHighlighter.tokenize(badComment, "c")
        assertTokenInvariants(badComment, commentTokens)
        assertEquals(listOf("/* unclosed\ncomment"), texts(badComment, commentTokens, CodeTokenKind.COMMENT))
    }

    @Test
    fun unknownLanguageAndEmptyInputReturnEmptyList() {
        assertTrue(CodeHighlighter.tokenize("val x = 1", "unknown").isEmpty())
        assertTrue(CodeHighlighter.tokenize("val x = 1", "").isEmpty())
        assertTrue(CodeHighlighter.tokenize("val x = 1", "plaintext").isEmpty())
        assertTrue(CodeHighlighter.tokenize("", "kotlin").isEmpty())
    }

    @Test
    fun inputIsCappedAt100kChars() {
        val unit = "val x = 42 // hi\n"
        val repeats = (CodeHighlighter.MAX_INPUT_CHARS / unit.length) + 100
        val bigCode = unit.repeat(repeats)
        assertTrue(bigCode.length > CodeHighlighter.MAX_INPUT_CHARS)

        val tokens = CodeHighlighter.tokenize(bigCode, "kotlin")
        assertTokenInvariants(bigCode, tokens, expectedEnd = CodeHighlighter.MAX_INPUT_CHARS)
        assertEquals(CodeHighlighter.MAX_INPUT_CHARS, tokens.last().end)
    }
}
