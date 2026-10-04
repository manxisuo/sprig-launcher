package io.github.manxisuo.spriglauncher.domain

import java.text.Collator
import java.util.Locale
import kotlin.math.pow

object RankingEngine {
    const val HALF_LIFE_DAYS = 7.0
    const val WINDOW_DAYS = 90L
    private const val DAY_MS = 86_400_000L

    fun score(points: List<UsagePoint>, nowMillis: Long): Double = points.sumOf { point ->
        val midpoint = point.utcDay * DAY_MS + DAY_MS / 2
        val ageDays = ((nowMillis - midpoint).coerceAtLeast(0)).toDouble() / DAY_MS
        point.count * 2.0.pow(-ageDays / HALF_LIFE_DAYS)
    }

    fun rank(input: RankingInput, limit: Int = 12): List<RankedEntry> {
        val pinned = input.pinnedEntryIds.toSet()
        val previous = input.previousOrder.withIndex().associate { it.value to it.index }
        val points = input.usage.groupBy { it.packageName }
        val collator = Collator.getInstance(Locale.getDefault())
        val representatives = input.entries
            .asSequence()
            .filterNot { it.id in pinned || it.packageName in input.excludedPackages }
            .groupBy { it.packageName }
            .map { (_, values) -> values.minWith(compareBy<LaunchableEntry> { it.label }.thenBy { it.id }) }
            .map { RankedEntry(it, score(points[it.packageName].orEmpty(), input.nowMillis)) }

        return representatives.sortedWith { a, b ->
            val byScore = b.score.compareTo(a.score)
            if (byScore != 0) byScore else {
                val ai = previous[a.entry.id]
                val bi = previous[b.entry.id]
                when {
                    ai != null && bi != null -> ai.compareTo(bi)
                    ai != null -> -1
                    bi != null -> 1
                    else -> collator.compare(a.entry.label, b.entry.label).takeIf { it != 0 }
                        ?: a.entry.id.compareTo(b.entry.id)
                }
            }
        }.take(limit)
    }
}
