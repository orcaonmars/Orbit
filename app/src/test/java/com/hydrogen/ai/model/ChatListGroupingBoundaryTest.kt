package com.hydrogen.ai.model

import com.hydrogen.ai.model.ChatListGrouping.Group
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChatListGroupingBoundaryTest {

    private fun epochMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        nano: Int = 0,
        zone: ZoneId
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, nano, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun futureBoundaryTimestampsGroupInTodayWithPreservedRelativeOrder() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 11, 23, 12, 0, zone = zone)

        val items = listOf(
            "future-tomorrow" to epochMs(2026, 11, 24, 0, 0, 1, zone = zone),
            "today-now" to now,
            "today-earlier" to epochMs(2026, 11, 23, 8, 0, zone = zone)
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(1, groups.size)
        assertEquals("Today", groups[0].title)
        assertEquals(listOf("future-tomorrow", "today-now", "today-earlier"), groups[0].conversationIds)
    }

    @Test
    fun exactDayThresholdBoundariesFallIntoDistinctSuccessiveBuckets() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 11, 23, 12, 0, zone = zone)

        val items = listOf(
            "today" to epochMs(2026, 11, 23, 0, 0, 0, zone = zone),
            "yesterday" to epochMs(2026, 11, 22, 23, 59, 59, zone = zone),
            "prev-7d" to epochMs(2026, 11, 16, 0, 0, 0, zone = zone), // 7 days ago
            "prev-30d" to epochMs(2026, 10, 24, 0, 0, 0, zone = zone) // 30 days ago
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(4, groups.size)
        assertEquals("Today", groups[0].title)
        assertEquals(listOf("today"), groups[0].conversationIds)
        assertEquals("Yesterday", groups[1].title)
        assertEquals(listOf("yesterday"), groups[1].conversationIds)
        assertEquals("Previous 7 days", groups[2].title)
        assertEquals(listOf("prev-7d"), groups[2].conversationIds)
        assertEquals("Previous 30 days", groups[3].title)
        assertEquals(listOf("prev-30d"), groups[3].conversationIds)
    }
}
