package com.hydrogen.ai.network

import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ImageSheetPlanner
import com.hydrogen.ai.model.MessageRole

/** Draws several photos into one numbered grid image. */
interface ImageSheetComposer {
    /**
     * Draws [items] as tiles numbered from [firstNumber], and returns the grid as a temporary image attachment, or
     * null when it couldn't be made.
     */
    suspend fun compose(items: List<AttachmentManifestItem>, firstNumber: Int): AttachmentManifestItem?

    /** Deletes a grid made by [compose] once its request is done. */
    fun discard(item: AttachmentManifestItem)
}

/**
 * Makes a request fit the service's image limit without losing the photos of the message being sent: any beyond the
 * limit go into numbered grid images, and the sent copy of the message says which grid holds which photo. The chat
 * itself keeps every original.
 */
class DispatchImagePacker(private val composer: ImageSheetComposer?) {

    class Packed(val request: ChatDispatchRequest, val temporary: List<AttachmentManifestItem>)

    suspend fun pack(request: ChatDispatchRequest, cap: Int): Packed {
        val omitted = request.omittedAttachmentIds
        val currentIndex = request.messages.indexOfLast { msg ->
            msg.role == MessageRole.USER && msg.attachments.any { it.id !in omitted }
        }
        if (currentIndex < 0) return Packed(request, emptyList())
        val current = request.messages[currentIndex]
        val images = current.attachments.filter { it.id !in omitted }
        val earlier = request.messages.filterIndexed { i, _ -> i != currentIndex }
            .flatMap { msg -> msg.attachments.filter { it.id !in omitted } }
        if (earlier.size + images.size <= cap) return Packed(request, emptyList())

        // The message being sent comes first: drop earlier photos, oldest first, before combining any of its own.
        val earlierRoom = (cap - images.size).coerceAtLeast(0)
        val earlierDropped = earlier.dropLast(earlierRoom).map { it.id }
        val slots = (cap - (earlier.size - earlierDropped.size)).coerceAtLeast(0)
        // Without a way to draw grids, send the first photos that fit.
        val plan = if (composer != null) ImageSheetPlanner.plan(images, slots)
            else ImageSheetPlanner.Plan(images.take(slots), emptyList(), images.drop(slots))

        val sheets = mutableListOf<AttachmentManifestItem>()
        val notSent = plan.dropped.map { it.id }.toMutableList()
        val sentSheetSizes = mutableListOf<Int>()
        var next = plan.singles.size + 1
        for (group in plan.sheets) {
            val sheet = composer?.compose(group, next)
            if (sheet != null) {
                sheets += sheet
                sentSheetSizes += group.size
            } else {
                notSent += group.map { it.id }
            }
            next += group.size
        }
        val note = if (sentSheetSizes.size == plan.sheets.size) {
            ImageSheetPlanner.note(images.size, plan.singles.size, sentSheetSizes, plan.dropped.size)
        } else {
            // A grid failed, so tile numbers no longer line up with the message order; just say what is missing.
            "[Orbit note: ${notSent.size} of the ${images.size} photos could not be included.]"
        }
        val sentAttachments = plan.singles + sheets
        val packedMessage = current.copy(
            attachments = sentAttachments,
            content = when {
                note.isEmpty() -> current.content
                current.content.isBlank() -> note
                else -> current.content + "\n\n" + note
            }
        )
        val messages = request.messages.toMutableList().also { it[currentIndex] = packedMessage }
        val removedIds = (earlierDropped + notSent + images.map { it.id }).toSet()
        return Packed(
            request = request.copy(
                messages = messages,
                attachments = request.attachments.filter { it.id !in removedIds } + sentAttachments,
                omittedAttachmentIds = omitted + earlierDropped + notSent
            ),
            temporary = sheets
        )
    }

    fun discard(packed: Packed) {
        val c = composer ?: return
        packed.temporary.forEach { runCatching { c.discard(it) } }
    }
}
