package com.nuvio.app.features.fork

/**
 * Ajustes exclusivos de este fork. Se guardan fuera de [com.nuvio.app.features.player.PlayerSettingsStorage]
 * para no mezclarlos con las opciones del proyecto original.
 */
internal expect object ForkSettingsStorage {
    fun loadSubtitleDragDropEnabled(): Boolean?
    fun saveSubtitleDragDropEnabled(enabled: Boolean)

    fun loadSubtitleLoadFileEnabled(): Boolean?
    fun saveSubtitleLoadFileEnabled(enabled: Boolean)

    fun loadContinueWatchingPrefetchEnabled(): Boolean?
    fun saveContinueWatchingPrefetchEnabled(enabled: Boolean)

    fun loadContinueWatchingPrefetchCount(): Int?
    fun saveContinueWatchingPrefetchCount(count: Int)

    fun loadContinueWatchingPrefetchTtlHours(): Int?
    fun saveContinueWatchingPrefetchTtlHours(hours: Int)

    fun loadNextEpisodePrefetchEnabled(): Boolean?
    fun saveNextEpisodePrefetchEnabled(enabled: Boolean)

    fun loadNextEpisodePrefetchMinutes(): Int?
    fun saveNextEpisodePrefetchMinutes(minutes: Int)
}
