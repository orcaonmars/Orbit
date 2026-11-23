package com.hydrogen.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CodeFileNameTest {

    @Test
    fun python_uses_fallback_base_and_py_extension() {
        assertEquals("orbit-snippet.py", CodeFileName.suggest("python", "print('hi')"))
    }

    @Test
    fun index_greater_than_one_appends_index_suffix() {
        assertEquals("orbit-snippet-3.py", CodeFileName.suggest("python", "print('hi')", index = 3))
        assertEquals("orbit-snippet.py", CodeFileName.suggest("python", "print('hi')", index = 1))
        assertEquals("orbit-snippet.py", CodeFileName.suggest("python", "print('hi')", index = 0))
    }

    @Test
    fun unknown_blank_and_null_language_default_to_txt() {
        assertEquals("txt", CodeFileName.extensionFor(null))
        assertEquals("txt", CodeFileName.extensionFor("   "))
        assertEquals("txt", CodeFileName.extensionFor("brainfuck"))
        assertEquals("orbit-snippet.txt", CodeFileName.suggest(null, "hello"))
        assertEquals("orbit-snippet.txt", CodeFileName.suggest("unknown-lang", "hello"))
    }

    @Test
    fun language_matching_ignores_case_and_surrounding_spaces() {
        assertEquals("kt", CodeFileName.extensionFor(" Kotlin "))
        assertEquals("orbit-snippet.kt", CodeFileName.suggest(" Kotlin ", "println(1)"))
    }

    @Test
    fun kotlin_extracts_top_level_class_object_or_interface() {
        assertEquals("ChatHelper.kt", CodeFileName.suggest("kotlin", "class ChatHelper {\n}"))
        assertEquals("AppConfig.kt", CodeFileName.suggest("kt", "object AppConfig {\n    val x = 1\n}"))
        assertEquals("Repo.kt", CodeFileName.suggest("kotlin", "sealed interface Repo"))
    }

    @Test
    fun java_extracts_top_level_class_with_modifiers() {
        assertEquals("Main.java", CodeFileName.suggest("java", "public final class Main"))
    }

    @Test
    fun dockerfile_returns_exact_dockerfile_without_extension() {
        assertEquals("", CodeFileName.extensionFor("dockerfile"))
        assertEquals("Dockerfile", CodeFileName.suggest("dockerfile", "FROM alpine:3.20"))
        assertEquals("Dockerfile", CodeFileName.suggest(" Dockerfile ", "FROM ubuntu", index = 4))
    }

    @Test
    fun cpp_and_csharp_aliases_map_to_cpp_and_cs() {
        assertEquals("cpp", CodeFileName.extensionFor("c++"))
        assertEquals("cpp", CodeFileName.extensionFor("cpp"))
        assertEquals("cpp", CodeFileName.extensionFor("cc"))
        assertEquals("cs", CodeFileName.extensionFor("c#"))
        assertEquals("cs", CodeFileName.extensionFor("cs"))
        assertEquals("cs", CodeFileName.extensionFor("csharp"))
    }

    @Test
    fun class_name_never_yields_path_characters() {
        assertEquals("orbit-snippet.kt", CodeFileName.suggest("kotlin", "class ../../evil"))
        assertEquals("orbit-snippet.java", CodeFileName.suggest("java", "public class dir/Sub"))
    }

    @Test
    fun all_supported_languages_map_to_expected_extensions() {
        val expected = mapOf(
            "kotlin" to "kt", "kt" to "kt", "kts" to "kts", "java" to "java",
            "python" to "py", "py" to "py", "javascript" to "js", "js" to "js",
            "typescript" to "ts", "ts" to "ts", "tsx" to "tsx", "jsx" to "jsx",
            "html" to "html", "css" to "css", "json" to "json",
            "yaml" to "yml", "yml" to "yml", "xml" to "xml", "sql" to "sql",
            "bash" to "sh", "sh" to "sh", "shell" to "sh", "zsh" to "sh",
            "powershell" to "ps1", "ps1" to "ps1", "pwsh" to "ps1",
            "bat" to "bat", "cmd" to "bat", "c" to "c",
            "go" to "go", "golang" to "go", "rust" to "rs", "rs" to "rs",
            "ruby" to "rb", "rb" to "rb", "php" to "php", "swift" to "swift",
            "dart" to "dart", "markdown" to "md", "md" to "md",
            "toml" to "toml", "ini" to "ini", "csv" to "csv", "r" to "r", "lua" to "lua"
        )
        for ((lang, ext) in expected) {
            assertEquals("Failed for $lang", ext, CodeFileName.extensionFor(lang))
        }
    }

    @Test
    fun long_class_name_is_capped_at_max_base_length() {
        val longName = "VeryLongClassName" + "Abc".repeat(25)
        val result = CodeFileName.suggest("kotlin", "class $longName {}")
        val base = result.substringBeforeLast('.')
        assertEquals(CodeFileName.MAX_BASE_LENGTH, base.length)
        assertEquals("kt", result.substringAfterLast('.'))
    }

    @Test
    fun odd_inputs_never_contain_forbidden_path_or_shell_characters() {
        val forbidden = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        val oddInputs = listOf(
            null to "",
            "" to "class /etc/passwd",
            "   " to "object :?*\"<>|",
            "kotlin" to "class ../../evil",
            "java" to "class C:\\Windows\\System32",
            "kotlin" to "// class FakeComment\nclass RealClass {}",
            "c++" to "int main() { return 0; }",
            "c#" to "class Program {}",
            "../evil" to "echo hi",
            "a/b\\c:d*e?f\"g<h>i|j" to "x",
            "dockerfile" to "RUN rm -rf /",
            "bash" to "#!/bin/bash\necho \"hello\"",
            "python" to "class NotUsedForPython:\n    pass",
            "kotlin" to "class ...---___",
            "kotlin" to "class " + "X".repeat(120),
            "kts" to "plugins { id(\"org.jetbrains.kotlin.jvm\") }",
            "powershell" to "Write-Host 'hi'",
            "unknown!!!" to "???",
            "\t\nRUST\t" to "fn main() {}",
            "sql" to "SELECT * FROM users WHERE name = 'a/b';"
        )
        assertEquals(20, oddInputs.size)
        for ((lang, code) in oddInputs) {
            val result = CodeFileName.suggest(lang, code, index = 2)
            assertFalse("Result '$result' must not be blank", result.isBlank())
            for (ch in forbidden) {
                assertFalse("Result '$result' contains forbidden char '$ch'", ch in result)
            }
        }
    }
}
