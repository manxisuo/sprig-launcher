package io.github.manxisuo.spriglauncher.domain

object SessionExtractor {
    const val TYPE_RESUMED = 1
    const val TYPE_PAUSED = 2
    const val TYPE_SCREEN_OFF = 3
    const val MERGE_WINDOW_MS = 30_000L

    fun extract(
        events: List<RawUsageEvent>,
        initial: SessionParserState,
        allowedPackages: Set<String>,
        ownPackage: String,
    ): SessionParseResult {
        var state = initial
        val sessions = mutableListOf<Pair<String, Long>>()
        events.sortedBy { it.timestamp }.forEach { event ->
            when (event.type) {
                TYPE_RESUMED -> if (event.packageName != ownPackage && event.packageName in allowedPackages) {
                    val sameOpenApp = state.foregroundPackage == event.packageName
                    val quickReturn = state.lastSessionPackage == event.packageName &&
                        event.timestamp - state.lastSessionAt in 0..MERGE_WINDOW_MS
                    if (!sameOpenApp && !quickReturn) sessions += event.packageName to event.timestamp
                    state = state.copy(
                        foregroundPackage = event.packageName,
                        lastSessionPackage = event.packageName,
                        lastSessionAt = if (sameOpenApp || quickReturn) state.lastSessionAt else event.timestamp,
                    )
                }
                TYPE_PAUSED -> if (state.foregroundPackage == event.packageName) {
                    state = state.copy(foregroundPackage = null)
                }
                TYPE_SCREEN_OFF -> state = state.copy(foregroundPackage = null)
            }
        }
        return SessionParseResult(sessions, state)
    }
}
