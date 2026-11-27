package com.hydrogen.ai.network

import com.hydrogen.ai.data.ImageSendSizing
import com.hydrogen.ai.model.AttachmentManifestItem
import com.hydrogen.ai.model.ChatMessage
import com.hydrogen.ai.model.ImageLimits
import com.hydrogen.ai.model.ImageSheetPlanner
import com.hydrogen.ai.model.MessageRole
import com.hydrogen.ai.model.ProviderRoute
import com.hydrogen.ai.model.RelayConfig
import com.hydrogen.ai.ui.stagedOverLimitText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** More photos than a provider allows: per-provider limits, grid planning, shrinking and the packed request. */
class ImageOverflowTest {

    private fun image(i: Int) = AttachmentManifestItem("p$i", "p$i.jpg", "image/jpeg", 100L, "file:/tmp/p$i.jpg")

    private fun user(id: String, text: String, photos: List<AttachmentManifestItem>) =
        ChatMessage(id = id, correlationId = "c", role = MessageRole.USER, content = text, createdAtEpochMs = 1L,
            attachments = photos)

    private fun relay(url: String, model: String = "m", images: Int = ImageLimits.AUTOMATIC) =
        RelayConfig(route = ProviderRoute.GEMINI_HYDROGEN_RELAY, baseUrl = url, modelId = model, maxImagesPerRequest = images)

    // Provider limits

    @Test fun automaticUsesEachProvidersDocumentedLimit() {
        assertEquals(8, ImageLimits.effectiveCap(relay("https://api.mistral.ai/v1")))
        assertEquals(5, ImageLimits.effectiveCap(relay("https://api.groq.com/openai/v1")))
        assertEquals(20, ImageLimits.effectiveCap(relay("https://api.openai.com/v1")))
        assertEquals(20, ImageLimits.effectiveCap(RelayConfig(route = ProviderRoute.ANTHROPIC_MESSAGES, modelId = "claude")))
        assertEquals(20, ImageLimits.effectiveCap(RelayConfig(route = ProviderRoute.GOOGLE_AI_STUDIO_DIRECT)))
        assertEquals(8, ImageLimits.effectiveCap(relay("http://10.0.2.2:1234/v1")))
    }

    @Test fun openRouterFollowsTheModelFamily() {
        assertEquals(20, ImageLimits.effectiveCap(relay("https://openrouter.ai/api/v1", "anthropic/claude-sonnet")))
        assertEquals(20, ImageLimits.effectiveCap(relay("https://openrouter.ai/api/v1", "google/gemini-2.5-flash")))
        assertEquals(8, ImageLimits.effectiveCap(relay("https://openrouter.ai/api/v1", "mistralai/pixtral")))
        assertEquals(8, ImageLimits.effectiveCap(relay("https://openrouter.ai/api/v1", "openrouter/free")))
    }

    @Test fun anOwnersNumberWinsOverTheProviderDefault() {
        assertEquals(3, ImageLimits.effectiveCap(relay("https://api.openai.com/v1", images = 3)))
        assertEquals(30, ImageLimits.effectiveCap(relay("https://api.mistral.ai/v1", images = 30)))
        assertEquals(ImageLimits.MAX_SETTING, ImageLimits.effectiveCap(relay("https://x.example", images = 5000)))
    }

    @Test fun lookAlikeHostsDoNotGetALargeProvidersLimit() {
        assertEquals(8, ImageLimits.effectiveCap(relay("https://notopenai.com/v1")))
        assertEquals(20, ImageLimits.effectiveCap(relay("https://eu.api.openai.com/v1")))
    }

    @Test fun anAnthropicCompatibleServiceElsewhereIsNotGivenAnthropicsLimit() {
        val other = RelayConfig(route = ProviderRoute.ANTHROPIC_MESSAGES, baseUrl = "https://api.example.com", modelId = "x")
        assertEquals(8, ImageLimits.effectiveCap(other))
    }

    // Planning

    @Test fun photosThatFitAreSentAsTheyAre() {
        val plan = ImageSheetPlanner.plan((1..8).toList(), 8)
        assertEquals((1..8).toList(), plan.singles)
        assertTrue(plan.sheets.isEmpty())
        assertTrue(plan.dropped.isEmpty())
    }

    @Test fun nineOnEightCombinesOnlyTheLastTwo() {
        val plan = ImageSheetPlanner.plan((1..9).toList(), 8)
        assertEquals((1..7).toList(), plan.singles)
        assertEquals(listOf(listOf(8, 9)), plan.sheets)
        assertEquals(8, plan.sentCount)
    }

    @Test fun twentyOnEightUsesFourTileGridsAndKeepsOrder() {
        val plan = ImageSheetPlanner.plan((1..20).toList(), 8)
        assertEquals(8, plan.sentCount)
        assertTrue(plan.sheets.all { it.size <= 4 })
        assertTrue(plan.dropped.isEmpty())
        assertEquals((1..20).toList(), plan.singles + plan.sheets.flatten())
    }

    @Test fun aVeryLargeBatchStepsUpToNineTilesThenDropsTheRest() {
        val fortyFive = ImageSheetPlanner.plan((1..45).toList(), 5)
        assertEquals(5, fortyFive.sentCount)
        assertTrue(fortyFive.sheets.all { it.size <= 9 })
        assertTrue(fortyFive.dropped.isEmpty())
        assertEquals((1..45).toList(), fortyFive.singles + fortyFive.sheets.flatten())

        val tooMany = ImageSheetPlanner.plan((1..50).toList(), 5)
        assertEquals(45, tooMany.singles.size + tooMany.sheets.sumOf { it.size })
        assertEquals((46..50).toList(), tooMany.dropped)
    }

    @Test fun noSlotsMeansNothingIsSent() {
        val plan = ImageSheetPlanner.plan(listOf(1, 2), 0)
        assertEquals(0, plan.sentCount)
        assertEquals(listOf(1, 2), plan.dropped)
    }

    @Test fun gridLayoutStaysWithinTheEdgeAndCellsDoNotOverlap() {
        for (n in 1..9) {
            val layout = ImageSheetPlanner.layout(n)
            assertTrue(layout.width <= ImageSheetPlanner.MAX_EDGE_PX && layout.height <= ImageSheetPlanner.MAX_EDGE_PX)
            assertEquals(n, layout.cells.size)
            for (a in layout.cells.indices) for (b in a + 1 until layout.cells.size) {
                val p = layout.cells[a]
                val q = layout.cells[b]
                val overlap = p.x < q.x + q.width && q.x < p.x + p.width && p.y < q.y + q.height && q.y < p.y + p.height
                assertFalse("cells $a and $b overlap for $n", overlap)
            }
            layout.cells.forEach { c -> assertTrue(c.x + c.width <= layout.width && c.y + c.height <= layout.height) }
        }
        assertEquals(2, ImageSheetPlanner.layout(4).columns)
        assertEquals(3, ImageSheetPlanner.layout(6).columns)
        assertEquals(2, ImageSheetPlanner.layout(6).rows)
    }

    @Test fun fitInsideCentresWithoutStretching() {
        val cell = ImageSheetPlanner.Cell(10, 10, 100, 100)
        val wide = ImageSheetPlanner.fitInside(400, 200, cell)
        assertEquals(100, wide.width)
        assertEquals(50, wide.height)
        assertEquals(35, wide.y)
        val tall = ImageSheetPlanner.fitInside(100, 400, cell)
        assertEquals(25, tall.width)
        assertEquals(47, tall.x)
    }

    @Test fun theNoteNamesWhichPhotosAreInEachGrid() {
        val note = ImageSheetPlanner.note(totalPhotos = 9, singles = 7, sheetSizes = listOf(2), droppedCount = 0)
        assertTrue(note.contains("Photos 1 to 7 are attached as they are"))
        assertTrue(note.contains("photos 8 to 9"))
        assertEquals("", ImageSheetPlanner.note(3, 3, emptyList(), 0))
        assertTrue(ImageSheetPlanner.note(4, 1, listOf(3), 0).startsWith("[Orbit note: Photo 1 is attached as it is."))
    }

    @Test fun trayTextExplainsTheGridsInPlainWords() {
        assertEquals(
            "12 photos. This service takes 8 per message, so photos 5 to 12 will go in 4 numbered grids.",
            stagedOverLimitText(12, 8, 4, 4, 0)
        )
        assertTrue(stagedOverLimitText(50, 5, 0, 5, 5).contains("5 more won't fit"))
        listOf(stagedOverLimitText(12, 8, 4, 4, 0), stagedOverLimitText(50, 5, 0, 5, 5)).forEach { text ->
            assertFalse(text.contains("!") || text.contains("—") || text.contains("–") || text.contains("·"))
        }
    }

    // Shrinking before sending

    @Test fun largeOrSidewaysOrUnusualPhotosAreShrunkButSmallOnesGoUnchanged() {
        assertFalse(ImageSendSizing.needsReencode(400_000, 1080, 1440, "image/jpeg", rotatedOrFlipped = false))
        assertFalse(ImageSendSizing.needsReencode(900_000, 1080, 1568, "image/png", rotatedOrFlipped = false))
        assertTrue(ImageSendSizing.needsReencode(4_000_000, 4000, 3000, "image/jpeg", rotatedOrFlipped = false))
        assertTrue(ImageSendSizing.needsReencode(400_000, 1600, 1200, "image/jpeg", rotatedOrFlipped = false))
        assertTrue(ImageSendSizing.needsReencode(400_000, 800, 600, "image/jpeg", rotatedOrFlipped = true))
        assertTrue(ImageSendSizing.needsReencode(400_000, 800, 600, "image/heic", rotatedOrFlipped = false))
        // Unreadable size: send the original rather than fail.
        assertFalse(ImageSendSizing.needsReencode(4_000_000, 0, 0, "image/jpeg", rotatedOrFlipped = false))
    }

    @Test fun fitWithinKeepsAspectAndNeverEnlarges() {
        assertEquals(1568 to 1176, ImageSendSizing.fitWithin(4000, 3000))
        assertEquals(882 to 1568, ImageSendSizing.fitWithin(2250, 4000))
        assertEquals(800 to 600, ImageSendSizing.fitWithin(800, 600))
    }

    // The packed request

    private class FakeComposer : ImageSheetComposer {
        val made = mutableListOf<Pair<List<String>, Int>>()
        val discarded = mutableListOf<String>()
        override suspend fun compose(items: List<AttachmentManifestItem>, firstNumber: Int): AttachmentManifestItem {
            made += items.map { it.id } to firstNumber
            return AttachmentManifestItem("sheet${made.size}", "grid", "image/jpeg", 1L, "file:/cache/sheet${made.size}.jpg")
        }
        override fun discard(item: AttachmentManifestItem) { discarded += item.id }
    }

    private fun request(messages: List<ChatMessage>, omitted: Set<String> = emptySet()) = ChatDispatchRequest(
        correlationId = "c", messages = messages, attachments = messages.flatMap { it.attachments }
            .filter { it.id !in omitted }, omittedAttachmentIds = omitted
    )

    @Test fun overflowPhotosGoIntoNumberedGridsAndTheModelIsTold() = runTest {
        val composer = FakeComposer()
        val photos = (1..10).map(::image)
        val packed = DispatchImagePacker(composer).pack(request(listOf(user("u", "Compare these", photos))), 8)
        val sent = packed.request.messages.single()
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p6", "p7", "sheet1"), sent.attachments.map { it.id })
        assertEquals(listOf(listOf("p8", "p9", "p10") to 8), composer.made)
        assertTrue(sent.content.startsWith("Compare these\n\n[Orbit note:"))
        assertTrue(sent.content.contains("photos 8 to 10."))
        assertEquals(sent.attachments.map { it.id }, packed.request.attachments.map { it.id })
        assertTrue(packed.request.omittedAttachmentIds.isEmpty())
        DispatchImagePacker(composer).discard(packed)
        assertEquals(listOf("sheet1"), composer.discarded)
    }

    @Test fun earlierPhotosMakeRoomBeforeTheSentMessageIsCombined() = runTest {
        val composer = FakeComposer()
        val history = listOf(user("old", "Before", (1..3).map(::image)), user("u", "Now", (4..9).map(::image)))
        val packed = DispatchImagePacker(composer).pack(request(history), 8)
        // 3 earlier + 6 new = 9 > 8: the oldest earlier photo makes room, and nothing new is combined.
        assertTrue(composer.made.isEmpty())
        assertEquals(setOf("p1"), packed.request.omittedAttachmentIds)
        assertEquals((4..9).map { "p$it" }, packed.request.messages.last().attachments.map { it.id })
        assertEquals("Now", packed.request.messages.last().content)
    }

    @Test fun aRequestWithinTheLimitIsUntouched() = runTest {
        val composer = FakeComposer()
        val original = request(listOf(user("u", "Hi", (1..3).map(::image))))
        val packed = DispatchImagePacker(composer).pack(original, 8)
        assertTrue(packed.request === original)
        assertTrue(packed.temporary.isEmpty())
    }

    @Test fun aFailedGridIsReportedInsteadOfMisnumbered() = runTest {
        val failing = object : ImageSheetComposer {
            override suspend fun compose(items: List<AttachmentManifestItem>, firstNumber: Int): AttachmentManifestItem? = null
            override fun discard(item: AttachmentManifestItem) = Unit
        }
        val packed = DispatchImagePacker(failing).pack(request(listOf(user("u", "", (1..9).map(::image)))), 8)
        val sent = packed.request.messages.single()
        assertEquals((1..7).map { "p$it" }, sent.attachments.map { it.id })
        assertEquals("[Orbit note: 2 of the 9 photos could not be included.]", sent.content)
        assertEquals(setOf("p8", "p9"), packed.request.omittedAttachmentIds)
    }
}
