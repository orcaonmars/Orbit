package com.hydrogen.ai.model

import com.hydrogen.ai.model.ChatListGrouping.Group
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChatListGroupingTest {

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
    fun emptyInputReturnsEmptyList() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 11, 20, 12, 0, zone = zone)

        assertEquals(emptyList<Group>(), ChatListGrouping.group(emptyList(), now, zone))
    }

    @Test
    fun bucketBoundariesAndJustAfterLocalMidnight() {
        val zone = ZoneId.of("America/New_York")
        val now = epochMs(2026, 11, 20, 0, 0, 5, zone = zone)

        val items = listOf(
            "oct-31d" to epochMs(2026, 10, 20, 23, 59, 59, 999_000_000, zone),
            "p30-30d" to epochMs(2026, 10, 21, 0, 0, 0, zone = zone),
            "p30-8d" to epochMs(2026, 11, 12, 23, 59, 59, 999_000_000, zone),
            "p7-7d" to epochMs(2026, 11, 13, 0, 0, 0, zone = zone),
            "p7-2d" to epochMs(2026, 11, 18, 23, 59, 59, 999_000_000, zone),
            "yest-start" to epochMs(2026, 11, 19, 0, 0, 0, zone = zone),
            "yest-just-before-midnight" to epochMs(2026, 11, 19, 23, 59, 59, 999_000_000, zone),
            "today-midnight" to epochMs(2026, 11, 20, 0, 0, 0, zone = zone)
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(
            listOf(
                Group("Today", listOf("today-midnight")),
                Group("Yesterday", listOf("yest-just-before-midnight", "yest-start")),
                Group("Previous 7 days", listOf("p7-2d", "p7-7d")),
                Group("Previous 30 days", listOf("p30-8d", "p30-30d")),
                Group("October 2026", listOf("oct-31d"))
            ),
            groups
        )
    }

    @Test
    fun emptyBucketsAreOmitted() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 11, 20, 14, 0, zone = zone)

        val items = listOf(
            "old" to epochMs(2026, 10, 5, 10, 0, zone = zone),
            "today" to epochMs(2026, 11, 20, 9, 0, zone = zone)
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(
            listOf(
                Group("Today", listOf("today")),
                Group("October 2026", listOf("old"))
            ),
            groups
        )
    }

    @Test
    fun monthGroupsCrossYearChangeNewestMonthFirst() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 2, 15, 12, 0, zone = zone)

        val items = listOf(
            "dec-2024" to epochMs(2024, 12, 31, 23, 0, zone = zone),
            "dec-2025-early" to epochMs(2025, 12, 1, 8, 0, zone = zone),
            "jan-2026" to epochMs(2026, 1, 10, 12, 0, zone = zone),
            "nov-2025" to epochMs(2025, 11, 15, 16, 0, zone = zone),
            "dec-2025-late" to epochMs(2025, 12, 25, 18, 0, zone = zone)
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(
            listOf(
                Group("January 2026", listOf("jan-2026")),
                Group("December 2025", listOf("dec-2025-late", "dec-2025-early")),
                Group("November 2025", listOf("nov-2025")),
                Group("December 2024", listOf("dec-2024"))
            ),
            groups
        )
    }

    @Test
    fun futureTimestampsCountAsTodayAndSortNewestFirst() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 11, 20, 12, 0, zone = zone)

        val items = listOf(
            "earlier-today" to epochMs(2026, 11, 20, 8, 0, zone = zone),
            "far-future" to epochMs(2027, 1, 5, 9, 0, zone = zone),
            "next-day-future" to epochMs(2026, 11, 21, 1, 0, zone = zone),
            "same-day-future" to epochMs(2026, 11, 20, 15, 0, zone = zone)
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(
            listOf(
                Group(
                    "Today",
                    listOf("far-future", "next-day-future", "same-day-future", "earlier-today")
                )
            ),
            groups
        )
    }

    @Test
    fun nonUtcZoneUsesLocalCalendarDateWhenUtcDateDiffers() {
        val utc = ZoneId.of("UTC")
        val tokyo = ZoneId.of("Asia/Tokyo") // UTC+9

        // 2026-11-19 20:00 UTC is 2026-11-20 05:00 in Tokyo.
        val now = epochMs(2026, 11, 19, 20, 0, zone = utc)

        val items = listOf(
            // 2026-11-19 15:30 UTC == 2026-11-20 00:30 Tokyo -> Today in Tokyo
            "tokyo-today" to epochMs(2026, 11, 19, 15, 30, zone = utc),
            // 2026-11-19 14:30 UTC == 2026-11-19 23:30 Tokyo -> Yesterday in Tokyo (would be Today in UTC)
            "tokyo-yesterday" to epochMs(2026, 11, 19, 14, 30, zone = utc),
            // 2026-11-18 14:30 UTC == 2026-11-18 23:30 Tokyo -> Previous 7 days in Tokyo (2 days ago)
            "tokyo-prev7" to epochMs(2026, 11, 18, 14, 30, zone = utc)
        )

        val tokyoGroups = ChatListGrouping.group(items, now, tokyo)
        assertEquals(
            listOf(
                Group("Today", listOf("tokyo-today")),
                Group("Yesterday", listOf("tokyo-yesterday")),
                Group("Previous 7 days", listOf("tokyo-prev7"))
            ),
            tokyoGroups
        )

        val losAngeles = ZoneId.of("America/Los_Angeles") // UTC-8 in late Nov
        // 2026-11-20 02:00 UTC is 2026-11-19 18:00 in Los Angeles.
        val laNow = epochMs(2026, 11, 20, 2, 0, zone = utc)
        val laItems = listOf(
            // 2026-11-19 08:30 UTC == 2026-11-19 00:30 LA -> Today in LA (would be Yesterday in UTC)
            "la-today" to epochMs(2026, 11, 19, 8, 30, zone = utc),
            // 2026-11-19 07:30 UTC == 2026-11-18 23:30 LA -> Yesterday in LA
            "la-yesterday" to epochMs(2026, 11, 19, 7, 30, zone = utc)
        )

        val laGroups = ChatListGrouping.group(laItems, laNow, losAngeles)
        assertEquals(
            listOf(
                Group("Today", listOf("la-today")),
                Group("Yesterday", listOf("la-yesterday"))
            ),
            laGroups
        )
    }

    @Test
    fun equalTimestampsPreserveInputOrderAndNeverThrowOnExtremes() {
        val zone = ZoneId.of("UTC")
        val now = epochMs(2026, 11, 20, 12, 0, zone = zone)
        val sameToday = epochMs(2026, 11, 20, 10, 0, zone = zone)
        val sameMonth = epochMs(2026, 9, 15, 10, 0, zone = zone)

        val items = listOf(
            "t-first" to sameToday,
            "m-first" to sameMonth,
            "t-second" to sameToday,
            "min-long" to Long.MIN_VALUE,
            "max-long" to Long.MAX_VALUE,
            "t-third" to sameToday,
            "m-second" to sameMonth
        )

        val groups = ChatListGrouping.group(items, now, zone)

        assertEquals(
            listOf("max-long", "t-first", "t-second", "t-third"),
            groups.first { it.title == "Today" }.conversationIds
        )
        assertEquals(
            listOf("m-first", "m-second"),
            groups.first { it.title == "September 2026" }.conversationIds
        )
        assertEquals("min-long", groups.last().conversationIds.single())
    }
}

