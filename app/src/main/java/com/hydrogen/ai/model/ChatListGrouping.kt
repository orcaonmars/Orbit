package com.hydrogen.ai.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TreeMap

/**
 * Pure Kotlin helper that groups conversations into date-based drawer sections
 * (Today, Yesterday, Previous 7 days, Previous 30 days, and monthly buckets).
 * Self-contained with zero Android dependencies; never throws.
 */
object ChatListGrouping {

    private const val TITLE_TODAY = "Today"
    private const val TITLE_YESTERDAY = "Yesterday"
    private const val TITLE_PREVIOUS_7_DAYS = "Previous 7 days"
    private const val TITLE_PREVIOUS_30_DAYS = "Previous 30 days"

    data class Group(
        val title: String,
        val conversationIds: List<String>
    )

    fun group(
        items: List<Pair<String, Long>>,
        nowEpochMs: Long,
        zone: ZoneId
    ): List<Group> {
        if (items.isEmpty()) return emptyList()

        val todayDate = toLocalDateSafe(nowEpochMs, zone)

        val sorted = items.withIndex().sortedWith(
            compareByDescending<IndexedValue<Pair<String, Long>>> { it.value.second }
                .thenBy { it.index }
        )

        val todayIds = mutableListOf<String>()
        val yesterdayIds = mutableListOf<String>()
        val previous7Ids = mutableListOf<String>()
        val previous30Ids = mutableListOf<String>()
        val monthBuckets = TreeMap<YearMonth, MutableList<String>>(reverseOrder())

        for (entry in sorted) {
            val (id, updatedAtEpochMs) = entry.value
            val itemDate = toLocalDateSafe(updatedAtEpochMs, zone)
            val daysAgo = daysBetweenSafe(itemDate, todayDate)

            when {
                daysAgo <= 0L -> todayIds.add(id)
                daysAgo == 1L -> yesterdayIds.add(id)
                daysAgo in 2L..7L -> previous7Ids.add(id)
                daysAgo in 8L..30L -> previous30Ids.add(id)
                else -> {
                    val ym = YearMonth.from(itemDate)
                    monthBuckets.getOrPut(ym) { mutableListOf() }.add(id)
                }
            }
        }

        val groups = ArrayList<Group>(4 + monthBuckets.size)
        if (todayIds.isNotEmpty()) groups.add(Group(TITLE_TODAY, todayIds))
        if (yesterdayIds.isNotEmpty()) groups.add(Group(TITLE_YESTERDAY, yesterdayIds))
        if (previous7Ids.isNotEmpty()) groups.add(Group(TITLE_PREVIOUS_7_DAYS, previous7Ids))
        if (previous30Ids.isNotEmpty()) groups.add(Group(TITLE_PREVIOUS_30_DAYS, previous30Ids))

        for ((yearMonth, ids) in monthBuckets) {
            groups.add(Group(formatMonthTitle(yearMonth), ids))
        }

        return groups
    }

    private fun toLocalDateSafe(epochMs: Long, zone: ZoneId): LocalDate =
        try {
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        } catch (_: RuntimeException) {
            if (epochMs < 0L) LocalDate.MIN else LocalDate.MAX
        }

    private fun daysBetweenSafe(start: LocalDate, end: LocalDate): Long =
        try {
            ChronoUnit.DAYS.between(start, end)
        } catch (_: RuntimeException) {
            if (start.isAfter(end)) -1L else Long.MAX_VALUE
        }

    private fun formatMonthTitle(yearMonth: YearMonth): String {
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "$monthName ${yearMonth.year}"
    }
}
