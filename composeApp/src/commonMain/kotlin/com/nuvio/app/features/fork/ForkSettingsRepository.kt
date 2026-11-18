package com.nuvio.app.features.fork

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ForkSettingsUiState(
    val subtitleDragDropEnabled: Boolean = true,
    val subtitleLoadFileEnabled: Boolean = true,
    val continueWatchingPrefetchEnabled: Boolean = true,
    val continueWatchingPrefetchCount: Int = ForkSettingsRepository.DefaultPrefetchCount,
    val continueWatchingPrefetchTtlHours: Int = ForkSettingsRepository.DefaultPrefetchTtlHours,
    val nextEpisodePrefetchEnabled: Boolean = true,
    val nextEpisodeVideoPrefetchEnabled: Boolean = false,
    val nextEpisodePrefetchMinutes: Int = ForkSettingsRepository.DefaultNextEpisodePrefetchMinutes,
    val nextEpisodeThumbnailHidden: Boolean = false,
    /** Shows the on-demand "SUBS" button in each stream row. */
    val muxedSubtitleBadgeEnabled: Boolean = true,
    /** Labels to keep once the tracks are read; empty means every language. */
    val muxedSubtitleLanguages: Set<String> = emptySet(),
)

/**
 * Ajustes propios de este fork (Anime4K vive en [com.nuvio.app.features.player.PlayerSettingsRepository]).
 */
object ForkSettingsRepository {
    const val DefaultPrefetchCount: Int = 10
    const val DefaultPrefetchTtlHours: Int = 12
    const val MinPrefetchCount: Int = 1
    const val MaxPrefetchCount: Int = 20
    const val MinPrefetchTtlHours: Int = 1
    const val MaxPrefetchTtlHours: Int = 72
    /** Minutes of current playback before starting the next-episode video download. */
    const val DefaultNextEpisodePrefetchMinutes: Int = 2
    const val MinNextEpisodePrefetchMinutes: Int = 0
    const val MaxNextEpisodePrefetchMinutes: Int = 30

    private val _uiState = MutableStateFlow(ForkSettingsUiState())
    val uiState: StateFlow<ForkSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.value = ForkSettingsUiState(
            subtitleDragDropEnabled = ForkSettingsStorage.loadSubtitleDragDropEnabled() ?: true,
            subtitleLoadFileEnabled = ForkSettingsStorage.loadSubtitleLoadFileEnabled() ?: true,
            continueWatchingPrefetchEnabled = ForkSettingsStorage.loadContinueWatchingPrefetchEnabled() ?: true,
            continueWatchingPrefetchCount = clampCount(
                ForkSettingsStorage.loadContinueWatchingPrefetchCount() ?: DefaultPrefetchCount,
            ),
            continueWatchingPrefetchTtlHours = clampTtl(
                ForkSettingsStorage.loadContinueWatchingPrefetchTtlHours() ?: DefaultPrefetchTtlHours,
            ),
            nextEpisodePrefetchEnabled = ForkSettingsStorage.loadNextEpisodePrefetchEnabled() ?: true,
            nextEpisodeVideoPrefetchEnabled = ForkSettingsStorage.loadNextEpisodeVideoPrefetchEnabled() ?: false,
            nextEpisodePrefetchMinutes = clampNextEpisodeMinutes(
                ForkSettingsStorage.loadNextEpisodePrefetchMinutes() ?: DefaultNextEpisodePrefetchMinutes,
            ),
            nextEpisodeThumbnailHidden = ForkSettingsStorage.loadNextEpisodeThumbnailHidden() ?: false,
            muxedSubtitleBadgeEnabled = ForkSettingsStorage.loadMuxedSubtitleBadgeEnabled() ?: true,
            muxedSubtitleLanguages = parseLanguageCodes(
                ForkSettingsStorage.loadMuxedSubtitleLanguages(),
            ),
        )
    }

    fun setSubtitleDragDropEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.subtitleDragDropEnabled == enabled) return
        _uiState.value = _uiState.value.copy(subtitleDragDropEnabled = enabled)
        ForkSettingsStorage.saveSubtitleDragDropEnabled(enabled)
    }

    fun setSubtitleLoadFileEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.subtitleLoadFileEnabled == enabled) return
        _uiState.value = _uiState.value.copy(subtitleLoadFileEnabled = enabled)
        ForkSettingsStorage.saveSubtitleLoadFileEnabled(enabled)
    }

    fun setContinueWatchingPrefetchEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.continueWatchingPrefetchEnabled == enabled) return
        _uiState.value = _uiState.value.copy(continueWatchingPrefetchEnabled = enabled)
        ForkSettingsStorage.saveContinueWatchingPrefetchEnabled(enabled)
    }

    fun setContinueWatchingPrefetchCount(count: Int) {
        ensureLoaded()
        val clamped = clampCount(count)
        if (_uiState.value.continueWatchingPrefetchCount == clamped) return
        _uiState.value = _uiState.value.copy(continueWatchingPrefetchCount = clamped)
        ForkSettingsStorage.saveContinueWatchingPrefetchCount(clamped)
    }

    fun setContinueWatchingPrefetchTtlHours(hours: Int) {
        ensureLoaded()
        val clamped = clampTtl(hours)
        if (_uiState.value.continueWatchingPrefetchTtlHours == clamped) return
        _uiState.value = _uiState.value.copy(continueWatchingPrefetchTtlHours = clamped)
        ForkSettingsStorage.saveContinueWatchingPrefetchTtlHours(clamped)
    }

    fun setNextEpisodePrefetchEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.nextEpisodePrefetchEnabled == enabled) return
        _uiState.value = _uiState.value.copy(nextEpisodePrefetchEnabled = enabled)
        ForkSettingsStorage.saveNextEpisodePrefetchEnabled(enabled)
    }

    fun setNextEpisodeVideoPrefetchEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.nextEpisodeVideoPrefetchEnabled == enabled) return
        _uiState.value = _uiState.value.copy(nextEpisodeVideoPrefetchEnabled = enabled)
        ForkSettingsStorage.saveNextEpisodeVideoPrefetchEnabled(enabled)
    }

    fun setNextEpisodePrefetchMinutes(minutes: Int) {
        ensureLoaded()
        val clamped = clampNextEpisodeMinutes(minutes)
        if (_uiState.value.nextEpisodePrefetchMinutes == clamped) return
        _uiState.value = _uiState.value.copy(nextEpisodePrefetchMinutes = clamped)
        ForkSettingsStorage.saveNextEpisodePrefetchMinutes(clamped)
    }

    fun setNextEpisodeThumbnailHidden(hidden: Boolean) {
        ensureLoaded()
        if (_uiState.value.nextEpisodeThumbnailHidden == hidden) return
        _uiState.value = _uiState.value.copy(nextEpisodeThumbnailHidden = hidden)
        ForkSettingsStorage.saveNextEpisodeThumbnailHidden(hidden)
    }

    fun setMuxedSubtitleBadgeEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.muxedSubtitleBadgeEnabled == enabled) return
        _uiState.value = _uiState.value.copy(muxedSubtitleBadgeEnabled = enabled)
        ForkSettingsStorage.saveMuxedSubtitleBadgeEnabled(enabled)
    }

    fun toggleMuxedSubtitleLanguage(code: String) {
        ensureLoaded()
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        val current = _uiState.value.muxedSubtitleLanguages
        val next = if (normalized in current) current - normalized else current + normalized
        _uiState.value = _uiState.value.copy(muxedSubtitleLanguages = next)
        ForkSettingsStorage.saveMuxedSubtitleLanguages(next.sorted().joinToString(","))
    }

    fun clearMuxedSubtitleLanguages() {
        ensureLoaded()
        if (_uiState.value.muxedSubtitleLanguages.isEmpty()) return
        _uiState.value = _uiState.value.copy(muxedSubtitleLanguages = emptySet())
        ForkSettingsStorage.saveMuxedSubtitleLanguages("")
    }

    private fun parseLanguageCodes(raw: String?): Set<String> =
        raw
            ?.split(',')
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private fun clampCount(count: Int): Int = count.coerceIn(MinPrefetchCount, MaxPrefetchCount)

    private fun clampTtl(hours: Int): Int = hours.coerceIn(MinPrefetchTtlHours, MaxPrefetchTtlHours)

    private fun clampNextEpisodeMinutes(minutes: Int): Int =
        minutes.coerceIn(MinNextEpisodePrefetchMinutes, MaxNextEpisodePrefetchMinutes)
}
