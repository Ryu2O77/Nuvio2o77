package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.isSupportedDownloadUrl
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
    private const val MaxVideoPrefetchCandidates = 12
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

    /**
     * Precarga las respuestas de los addons del próximo episodio mientras todavía se está
     * reproduciendo el actual. Así, al pasar de episodio, las fuentes ya están en caché.
     */
    fun prefetchNextEpisode(
        type: String,
        videoId: String,
        parentMetaId: String,
        season: Int,
        episode: Int,
        currentVideoId: String? = null,
    ) {
        ForkSettingsRepository.ensureLoaded()
        val settings = ForkSettingsRepository.uiState.value
        val prefetchStreams = settings.nextEpisodePrefetchEnabled
        val prefetchVideo = settings.nextEpisodeVideoPrefetchEnabled
        if (!prefetchStreams && !prefetchVideo) return
        val maxAgeMs = settings.continueWatchingPrefetchTtlHours * 60L * 60L * 1_000L
        scope.launch {
            if (prefetchStreams || prefetchVideo) {
                runCatchingUnlessCancelled {
                    StreamsRepository.prefetchAddonStreams(
                        type = type,
                        videoId = videoId,
                        parentMetaId = parentMetaId,
                        season = season,
                        episode = episode,
                        maxAgeMs = maxAgeMs,
                    )
                }.onFailure { error ->
                    log.d(error) { "Next episode prefetch failed for $videoId" }
                }
            }
            if (prefetchVideo) {
                runCatchingUnlessCancelled {
                    cacheNextEpisodeVideo(
                        type = type,
                        videoId = videoId,
                        parentMetaId = parentMetaId,
                        season = season,
                        episode = episode,
                        currentVideoId = currentVideoId,
                        maxAgeMs = maxAgeMs,
                    )
                }.onFailure { error ->
                    log.d(error) { "Next episode video prefetch failed for $videoId" }
                }
            }
        }
    }

    /**
     * Descarga en segundo plano el video del próximo episodio usando el primer enlace
     * reproducible disponible, igual que si el usuario hubiera pulsado ese enlace.
     */
    private suspend fun cacheNextEpisodeVideo(
        type: String,
        videoId: String,
        parentMetaId: String,
        season: Int,
        episode: Int,
        currentVideoId: String?,
        maxAgeMs: Long,
    ) {
        if (DownloadsRepository.hasDownloadFor(parentMetaId, season, episode)) return

        val streams = StreamsRepository.prefetchedStreams(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = season,
            episode = episode,
            maxAgeMs = maxAgeMs,
        )
        if (streams.isEmpty()) return

        var candidate: StreamItem? = null
        for (stream in streams.take(MaxVideoPrefetchCandidates)) {
            val resolved = when (val result = DirectDebridPlaybackResolver.resolveToPlayableStream(stream, season, episode)) {
                is DirectDebridPlayableResult.Success -> result.stream
                else -> null
            }
            if (resolved?.playableDirectUrl?.isSupportedDownloadUrl() == true) {
                candidate = resolved
                break
            }
        }
        val stream = candidate ?: return

        val meta = MetaDetailsRepository.peek(type, parentMetaId)
        val episodeVideo = meta?.videos?.firstOrNull { it.id == videoId }
            ?: meta?.videos?.firstOrNull { it.season == season && it.episode == episode }

        DownloadsRepository.cleanupPrefetchCache(
            keepVideoIds = listOfNotNull(currentVideoId?.takeIf { it.isNotBlank() }, videoId).toSet(),
        )

        DownloadsRepository.enqueueFromStream(
            contentType = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            parentMetaType = type,
            title = meta?.name ?: parentMetaId,
            logo = meta?.logo,
            poster = meta?.poster,
            background = meta?.background,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeVideo?.title,
            episodeThumbnail = episodeVideo?.thumbnail,
            stream = stream,
            isPrefetchCache = true,
        )
    }

    fun clearCache() {        activeJob?.cancel()
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
