package com.nuvio.app.features.fork

import com.nuvio.app.core.storage.DesktopStorage

internal actual object ForkSettingsStorage {
    private const val subtitleDragDropKey = "subtitle_drag_drop_enabled"
    private const val subtitleLoadFileKey = "subtitle_load_file_enabled"
    private const val prefetchEnabledKey = "continue_watching_prefetch_enabled"
    private const val prefetchCountKey = "continue_watching_prefetch_count"
    private const val prefetchTtlKey = "continue_watching_prefetch_ttl_hours"
    private const val nextEpisodeEnabledKey = "next_episode_prefetch_enabled"
    private const val nextEpisodeMinutesKey = "next_episode_prefetch_minutes"

    private val store = DesktopStorage.store("nuvio_fork_settings")

    actual fun loadSubtitleDragDropEnabled(): Boolean? =
        if (store.contains(subtitleDragDropKey)) store.getBoolean(subtitleDragDropKey) else null

    actual fun saveSubtitleDragDropEnabled(enabled: Boolean) {
        store.putBoolean(subtitleDragDropKey, enabled)
    }

    actual fun loadSubtitleLoadFileEnabled(): Boolean? =
        if (store.contains(subtitleLoadFileKey)) store.getBoolean(subtitleLoadFileKey) else null

    actual fun saveSubtitleLoadFileEnabled(enabled: Boolean) {
        store.putBoolean(subtitleLoadFileKey, enabled)
    }

    actual fun loadContinueWatchingPrefetchEnabled(): Boolean? =
        if (store.contains(prefetchEnabledKey)) store.getBoolean(prefetchEnabledKey) else null

    actual fun saveContinueWatchingPrefetchEnabled(enabled: Boolean) {
        store.putBoolean(prefetchEnabledKey, enabled)
    }

    actual fun loadContinueWatchingPrefetchCount(): Int? =
        if (store.contains(prefetchCountKey)) store.getInt(prefetchCountKey) else null

    actual fun saveContinueWatchingPrefetchCount(count: Int) {
        store.putInt(prefetchCountKey, count)
    }

    actual fun loadContinueWatchingPrefetchTtlHours(): Int? =
        if (store.contains(prefetchTtlKey)) store.getInt(prefetchTtlKey) else null

    actual fun saveContinueWatchingPrefetchTtlHours(hours: Int) {
        store.putInt(prefetchTtlKey, hours)
    }

    actual fun loadNextEpisodePrefetchEnabled(): Boolean? =
        if (store.contains(nextEpisodeEnabledKey)) store.getBoolean(nextEpisodeEnabledKey) else null

    actual fun saveNextEpisodePrefetchEnabled(enabled: Boolean) {
        store.putBoolean(nextEpisodeEnabledKey, enabled)
    }

    actual fun loadNextEpisodePrefetchMinutes(): Int? =
        if (store.contains(nextEpisodeMinutesKey)) store.getInt(nextEpisodeMinutesKey) else null

    actual fun saveNextEpisodePrefetchMinutes(minutes: Int) {
        store.putInt(nextEpisodeMinutesKey, minutes)
    }
}
