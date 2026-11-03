package com.nuvio.app.features.fork

import platform.Foundation.NSUserDefaults

internal actual object ForkSettingsStorage {
    private const val subtitleDragDropKey = "nuvio_fork_subtitle_drag_drop_enabled"
    private const val subtitleLoadFileKey = "nuvio_fork_subtitle_load_file_enabled"
    private const val prefetchEnabledKey = "nuvio_fork_continue_watching_prefetch_enabled"
    private const val prefetchCountKey = "nuvio_fork_continue_watching_prefetch_count"
    private const val prefetchTtlKey = "nuvio_fork_continue_watching_prefetch_ttl_hours"

    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    private fun hasKey(key: String): Boolean = defaults.objectForKey(key) != null

    actual fun loadSubtitleDragDropEnabled(): Boolean? =
        if (hasKey(subtitleDragDropKey)) defaults.boolForKey(subtitleDragDropKey) else null

    actual fun saveSubtitleDragDropEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = subtitleDragDropKey)
    }

    actual fun loadSubtitleLoadFileEnabled(): Boolean? =
        if (hasKey(subtitleLoadFileKey)) defaults.boolForKey(subtitleLoadFileKey) else null

    actual fun saveSubtitleLoadFileEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = subtitleLoadFileKey)
    }

    actual fun loadContinueWatchingPrefetchEnabled(): Boolean? =
        if (hasKey(prefetchEnabledKey)) defaults.boolForKey(prefetchEnabledKey) else null

    actual fun saveContinueWatchingPrefetchEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = prefetchEnabledKey)
    }

    actual fun loadContinueWatchingPrefetchCount(): Int? =
        if (hasKey(prefetchCountKey)) defaults.integerForKey(prefetchCountKey).toInt() else null

    actual fun saveContinueWatchingPrefetchCount(count: Int) {
        defaults.setInteger(count.toLong(), forKey = prefetchCountKey)
    }

    actual fun loadContinueWatchingPrefetchTtlHours(): Int? =
        if (hasKey(prefetchTtlKey)) defaults.integerForKey(prefetchTtlKey).toInt() else null

    actual fun saveContinueWatchingPrefetchTtlHours(hours: Int) {
        defaults.setInteger(hours.toLong(), forKey = prefetchTtlKey)
    }
}
