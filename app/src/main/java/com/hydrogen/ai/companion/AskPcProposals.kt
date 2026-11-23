package com.hydrogen.ai.companion

import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.SystemPromptPolicy

/**
 * A reply's suggestion to ask the paired PC for one read-only action ("Ask your PC").
 * Nothing is sent until the user taps Send on the card, and the Windows owner still approves it on the PC.
 */
data class AskPcProposal(
    val actionType: WorkActionType,
    /** Drive-absolute Windows path, validated and shown to the user exactly as it will be sent. */
    val targetPath: String,
    /** The AI's explanation, cleaned like the companion does; shown as a quote and sent as `reason`. */
    val reason: String?
) {
    /** File or folder name for the phone's fixed summary template (the drive root keeps "C:\"). */
    val targetName: String
        get() = targetPath.trimEnd('\\').substringAfterLast('\\').let { if (it.endsWith(":")) "$it\\" else it }

    /** Built from a fixed template, never from model text (see docs/CLAUDE_REVIEW_C7.md). */
    val summary: String
        get() = when (actionType) {
            WorkActionType.READ_FILE_PREVIEW -> "Preview $targetName"
            WorkActionType.LIST_DIRECTORY -> "List $targetName"
        }.let { takeCodePoints(it, AskPcProposals.MAX_REASON_CODE_POINTS) }

    val actionLabel: String
        get() = when (actionType) {
            WorkActionType.READ_FILE_PREVIEW -> "Preview a file"
            WorkActionType.LIST_DIRECTORY -> "List a folder"
        }
}

/**
 * The first [max] Unicode code points of [text], never splitting a surrogate pair
 * (the companion counts approval text in code points, like Python's len()).
 */
internal fun takeCodePoints(text: String, max: Int): String {
    if (text.length <= max) return text
    if (text.codePointCount(0, text.length) <= max) return text
    return text.substring(0, text.offsetByCodePoints(0, max))
}

/**
 * Finds and hides fenced ```orbit-pc blocks in assistant replies.
 *
 * ```orbit-pc
 * action: preview | list
 * path: C:\Users\me\Notes\plan.txt
 * reason: one short sentence
 * ```
 *
 * Only a closed block at the top level (not inside another code fence) counts. A block whose fields
 * don't validate gives no card, but is still hidden from the reply.
 */
object AskPcProposals {
    const val FENCE_TAG = "orbit-pc"
    const val MAX_PATH_LENGTH = 1024
    const val MAX_REASON_CODE_POINTS = 200

    private val DRIVE_ABSOLUTE = Regex("^[A-Za-z]:\\\\")
    private val INVALID_PATH_CHARS = setOf('<', '>', '"', '|', '?', '*')

    /** Told to the model only while a PC is paired (see [hintEnabled]). */
    val SYSTEM_HINT: String = """
        Orbit PC Companion: the user's Windows PC is paired with this phone and shares some folders. If reading one file or listing one folder on that PC would clearly help, you may suggest it by ending your reply with exactly one block:
        ```orbit-pc
        action: preview
        path: C:\full\absolute\path\file.txt
        reason: one short sentence the PC owner will see
        ```
        Use action "list" for a folder. Only preview and list exist: nothing can be written, run or deleted. The user must tap Send and then approve it on the PC, so never claim you have already seen the file. Use only paths the user gave or that appeared in earlier PC results, never guessed ones. Leave the block out when it isn't needed.
    """.trimIndent()

    /** Paired, and the PC hasn't said its work is off (health is unknown until the first check after a restart). */
    fun hintEnabled(paired: Boolean, workEnabled: Boolean?): Boolean = paired && workEnabled != false

    /** [instructions] with the hint appended, for context-window budgeting. */
    fun withSystemHint(instructions: String, enabled: Boolean): String = when {
        !enabled -> instructions
        instructions.isBlank() -> SYSTEM_HINT
        else -> instructions.trimEnd() + "\n\n" + SYSTEM_HINT
    }

    /** Appends the hint to the dispatch's single system message (after memory), creating it if needed. */
    fun applySystemHint(messages: List<ChatMessage>, enabled: Boolean, createdAtEpochMs: Long): List<ChatMessage> {
        if (!enabled) return messages
        if (messages.any { it.id == SystemPromptPolicy.MESSAGE_ID }) {
            return messages.map {
                if (it.id == SystemPromptPolicy.MESSAGE_ID) it.copy(content = withSystemHint(it.content, true)) else it
            }
        }
        val systemMessage = ChatMessage(
            id = SystemPromptPolicy.MESSAGE_ID,
            correlationId = SystemPromptPolicy.MESSAGE_ID,
            role = MessageRole.SYSTEM,
            content = SYSTEM_HINT,
            createdAtEpochMs = createdAtEpochMs
        )
        return listOf(systemMessage) + messages
    }

    /** Calm wording for the companion's proposal errors (PROTOCOL.md); other errors keep the companion's text. */
    fun failureMessage(failure: CompanionResult.Failure): String = when (failure.code) {
        "too_many_pending_assistant_proposals" -> "3 suggestions are already waiting on your PC. Approve or deny those first."
        "too_many_pending_approvals" -> "5 requests are already waiting on your PC. Approve or deny those first."
        "assistant_action_not_allowed" -> "Your PC only lets AI suggestions preview a file or list a folder."
        "approvals_paused" -> "Your PC has paused new requests. Resume them from the Orbit icon in the Windows tray."
        "network_error" -> "Couldn't reach your PC. Check that PC Companion is running, then try again."
        else -> failure.userMessage
    }

    /** The first valid proposal in [content], or null. */
    fun parse(content: String): AskPcProposal? {
        if (!content.contains(FENCE_TAG, ignoreCase = true)) return null
        return scan(content).blocks.firstNotNullOfOrNull { block ->
            if (block.closed) proposalFrom(block.body) else null
        }
    }

    /** [content] without orbit-pc blocks (closed, or still streaming at the end). */
    fun displayText(content: String): String {
        val hasTag = content.contains(FENCE_TAG, ignoreCase = true)
        if (!hasTag && !content.contains("```")) return content
        val result = scan(content)
        val hidden = result.hiddenLines
        if (hidden.isEmpty()) return content
        val lines = result.lines
        val kept = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            if (i !in hidden) {
                kept += lines[i]
                i++
                continue
            }
            while (i < lines.size && i in hidden) i++
            // Don't leave a double blank line where the block was.
            if (kept.lastOrNull()?.isBlank() != false && i < lines.size && lines[i].isBlank()) i++
        }
        return kept.joinToString("\n").trimEnd()
    }

    /** Same cleaning as the companion: drop Cc/Cf/Cs/Zl/Zp, trim, keep 200 code points, empty -> null. */
    fun cleanReason(raw: String?): String? {
        if (raw == null) return null
        val filtered = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (!isUnsafeCodePoint(cp)) filtered.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        val trimmed = filtered.toString().trim()
        if (trimmed.isEmpty()) return null
        val count = trimmed.codePointCount(0, trimmed.length)
        if (count <= MAX_REASON_CODE_POINTS) return trimmed
        val end = trimmed.offsetByCodePoints(0, MAX_REASON_CODE_POINTS)
        return trimmed.substring(0, end).trimEnd().ifEmpty { null }
    }

    /** A clean drive-absolute path, or null when [raw] isn't one the card may show. */
    fun cleanPath(raw: String): String? {
        var path = raw.trim()
        if (path.length >= 2 && ((path.startsWith('"') && path.endsWith('"')) || (path.startsWith('`') && path.endsWith('`')))) {
            path = path.substring(1, path.length - 1).trim()
        }
        path = path.replace('/', '\\')
        if (path.isEmpty() || path.length > MAX_PATH_LENGTH) return null
        if (!DRIVE_ABSOLUTE.containsMatchIn(path)) return null
        var i = 0
        while (i < path.length) {
            val cp = path.codePointAt(i)
            if (isUnsafeCodePoint(cp)) return null
            i += Character.charCount(cp)
        }
        if (path.any { it in INVALID_PATH_CHARS }) return null
        // Only the drive letter may be followed by ':' (no alternate data streams).
        if (path.indexOf(':', startIndex = 2) >= 0) return null
        val rest = path.substring(3).trimEnd('\\')
        if (rest.isEmpty()) return path.substring(0, 3)
        val segments = rest.split('\\')
        for (segment in segments) {
            if (segment.isEmpty() || segment == "." || segment == "..") return null
            // Windows drops trailing dots/spaces, so "notes.txt." would name a different file than shown.
            if (segment.endsWith('.') || segment.endsWith(' ') || segment.startsWith(' ')) return null
        }
        return path.substring(0, 3) + rest
    }

    private fun proposalFrom(body: List<String>): AskPcProposal? {
        val fields = mutableMapOf<String, String>()
        for (line in body) {
            if (line.isBlank()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) return null
            val key = line.substring(0, colon).trim().lowercase()
            if (key !in setOf("action", "path", "reason")) continue
            if (key in fields) return null // Two paths or actions: too ambiguous to show.
            fields[key] = line.substring(colon + 1).trim()
        }
        val action = when (fields["action"]?.lowercase()?.replace('-', '_')) {
            "preview", "read", "read_file", "read_file_preview" -> WorkActionType.READ_FILE_PREVIEW
            "list", "list_folder", "list_directory" -> WorkActionType.LIST_DIRECTORY
            else -> return null
        }
        val path = cleanPath(fields["path"] ?: return null) ?: return null
        if (action == WorkActionType.READ_FILE_PREVIEW && path.length == 3) return null
        return AskPcProposal(actionType = action, targetPath = path, reason = cleanReason(fields["reason"]))
    }

    private fun isUnsafeCodePoint(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.SURROGATE,
        Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
        else -> false
    }

    private class Block(val body: List<String>, val closed: Boolean)

    private class ScanResult(val lines: List<String>, val blocks: List<Block>, val hiddenLines: Set<Int>)

    /** Line scan that tracks ordinary code fences so an orbit-pc example inside one isn't acted on. */
    private fun scan(content: String): ScanResult {
        val lines = content.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<Block>()
        val hidden = mutableSetOf<Int>()
        var outerFence: String? = null // Opening run of an ordinary fence we're inside.
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            val run = fenceRun(trimmed)
            if (outerFence != null) {
                if (run != null && run[0] == outerFence[0] && run.length >= outerFence.length && trimmed == run) {
                    outerFence = null
                }
                index++
                continue
            }
            if (run != null && run.startsWith("```") && trimmed.substring(run.length).trim().equals(FENCE_TAG, ignoreCase = true)) {
                val start = index
                val body = mutableListOf<String>()
                var closed = false
                index++
                while (index < lines.size) {
                    val inner = lines[index].trim()
                    val innerRun = fenceRun(inner)
                    if (innerRun != null && innerRun[0] == '`' && innerRun.length >= run.length && inner == innerRun) {
                        closed = true
                        break
                    }
                    body += lines[index]
                    index++
                }
                val end = if (closed) index else lines.lastIndex
                for (i in start..end) hidden += i
                blocks += Block(body, closed)
                index = end + 1
                continue
            }
            // While streaming, the last line may be the start of "```orbit-pc" before the whole tag arrives.
            if (index == lines.lastIndex && trimmed.length > 3 && "```$FENCE_TAG".startsWith(trimmed, ignoreCase = true)) {
                hidden += index
                break
            }
            if (run != null) {
                outerFence = run
                index++
                continue
            }
            index++
        }
        return ScanResult(lines, blocks, hidden)
    }

    /** The leading run of 3+ backticks or tildes, or null. */
    private fun fenceRun(trimmedLine: String): String? {
        if (trimmedLine.length < 3) return null
        val c = trimmedLine[0]
        if (c != '`' && c != '~') return null
        var n = 0
        while (n < trimmedLine.length && trimmedLine[n] == c) n++
        return if (n >= 3) trimmedLine.substring(0, n) else null
    }
}
