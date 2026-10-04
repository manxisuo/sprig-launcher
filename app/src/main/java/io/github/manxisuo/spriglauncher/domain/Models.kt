package io.github.manxisuo.spriglauncher.domain

data class LaunchableEntry(
    val id: String,
    val packageName: String,
    val componentName: String,
    val userSerial: Long,
    val label: String,
)

enum class UsageSource { LAUNCHER, SYSTEM }

data class UsagePoint(val packageName: String, val utcDay: Long, val count: Int)

data class RankingInput(
    val entries: List<LaunchableEntry>,
    val pinnedEntryIds: List<String>,
    val excludedPackages: Set<String>,
    val usage: List<UsagePoint>,
    val previousOrder: List<String>,
    val nowMillis: Long,
)

data class RankedEntry(val entry: LaunchableEntry, val score: Double)

data class RawUsageEvent(val timestamp: Long, val packageName: String, val type: Int, val stableId: String)

data class SessionParserState(
    val foregroundPackage: String? = null,
    val lastSessionPackage: String? = null,
    val lastSessionAt: Long = 0,
)

data class SessionParseResult(
    val sessions: List<Pair<String, Long>>,
    val state: SessionParserState,
)
