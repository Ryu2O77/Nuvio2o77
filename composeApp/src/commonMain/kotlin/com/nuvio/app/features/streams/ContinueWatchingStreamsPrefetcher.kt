package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.features.fork.ForkSettingsRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.isCloudLibraryContinueWatchingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Precarga en segundo plano las respuestas de los addons de los primeros elementos
 * de "Continuar viendo" para que el panel de fuentes abra al instante.
 */
internal object ContinueWatchingStreamsPrefetcher {
    private val log = Logger.withTag("CWPrefetch")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null
    private var lastRequestKey: String? = null

    fun prefetch(items: List<ContinueWatchingItem>) {
        ForkSettingsRepository.ensureLoaded()
        val settings = ForkSettingsRepository.uiState.value
        if (!settings.continueWatchingPrefetchEnabled) {
            cancel()
            return
        }

        val maxAgeMs = settings.continueWatchingPrefetchTtlHours * 60L * 60L * 1_000L
        val targets = items
            .asSequence()
            .filter { !it.isCloudLibraryContinueWatchingItem() }
            .filter { it.parentMetaType.isPrefetchableMediaType() }
            .filter { it.videoId.isNotBlank() }
            .distinctBy { "${it.parentMetaType.lowercase()}|${it.parentMetaId}|${it.videoId}" }
            .take(settings.continueWatchingPrefetchCount)
            .toList()

        val requestKey = buildString {
            append(maxAgeMs)
            append('|')
            targets.forEach { item ->
                append(item.parentMetaType.lowercase())
                append('|')
                append(item.parentMetaId)
                append('|')
                append(item.videoId)
                append('|')
                append(item.seasonNumber)
                append('|')
                append(item.episodeNumber)
                append(';')
            }
        }
        if (requestKey == lastRequestKey && activeJob?.isActive == true) return
        lastRequestKey = requestKey
        activeJob?.cancel()
        if (targets.isEmpty()) return

        activeJob = scope.launch {
            if (targets.all { AddonStreamsCache.hasContent(it.cacheKey(), maxAgeMs) }) return@launch
            for (item in targets) {
                if (!currentCoroutineContext().isActive) break
                runCatchingUnlessCancelled {
                    StreamsRepository.prefetchAddonStreams(
                        type = item.parentMetaType,
                        videoId = item.videoId,
                        parentMetaId = item.parentMetaId,
                        season = item.seasonNumber,
                        episode = item.episodeNumber,
                        maxAgeMs = maxAgeMs,
                    )
                }.onFailure { error ->
                    log.d(error) { "Prefetch failed for ${item.videoId}" }
                }
                delay(250L)
            }
        }
    }

    fun clearCache() {
        activeJob?.cancel()
        activeJob = null
        lastRequestKey = null
        AddonStreamsCache.clear()
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        lastRequestKey = null
    }

    private fun ContinueWatchingItem.cacheKey(): String = StreamLinkCacheRepository.contentKey(
        type = parentMetaType,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = seasonNumber,
        episode = episodeNumber,
    )

    private fun String.isPrefetchableMediaType(): Boolean =
        equals("movie", ignoreCase = true) || equals("series", ignoreCase = true)
}
