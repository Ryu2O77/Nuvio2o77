package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.downloads.DownloadStatus
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
    private var videoPrefetchJob: Job? = null
    @Volatile
    private var activeVideoPrefetchVideoId: String? = null
    @Volatile
    private var playbackPressure = false
    @Volatile
    private var pausedByPlaybackPressure = false

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
     * Precarga las respuestas de los addons del próximo episodio en cuanto se conoce,
     * sin esperar a los últimos minutos. Son peticiones pequeñas y así el panel de
     * fuentes ya está listo cuando el usuario cambia de episodio.
     */
    fun prefetchNextEpisodeStreams(
        type: String,
        videoId: String,
        parentMetaId: String,
        season: Int,
        episode: Int,
    ) {
        ForkSettingsRepository.ensureLoaded()
        val settings = ForkSettingsRepository.uiState.value
        if (!settings.nextEpisodePrefetchEnabled) return
        val maxAgeMs = settings.continueWatchingPrefetchTtlHours * 60L * 60L * 1_000L
        scope.launch {
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
                log.d(error) { "Next episode streams prefetch failed for $videoId" }
            }
        }
    }

    /**
     * Descarga el video del próximo episodio en segundo plano. La descarga se puede
     * pausar y reanudar según el buffer del episodio actual.
     */
    fun prefetchNextEpisodeVideo(
        type: String,
        videoId: String,
        parentMetaId: String,
        season: Int,
        episode: Int,
        currentVideoId: String? = null,
    ) {
        ForkSettingsRepository.ensureLoaded()
        val settings = ForkSettingsRepository.uiState.value
        if (!settings.nextEpisodeVideoPrefetchEnabled) return
        val maxAgeMs = settings.continueWatchingPrefetchTtlHours * 60L * 60L * 1_000L
        if (activeVideoPrefetchVideoId != videoId) {
            pausedByPlaybackPressure = false
        }
        activeVideoPrefetchVideoId = videoId
        videoPrefetchJob?.cancel()
        videoPrefetchJob = scope.launch {
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
            // The download may have been enqueued after the latest pressure update.
            applyPlaybackPressure()
        }
    }

    /**
     * Pausa la descarga de fondo mientras el episodio actual está rebuffereando o
     * tiene poco buffer por delante. Se reanuda cuando la reproducción vuelve a ir
     * cómoda, o cuando el usuario pausa el episodio.
     */
    fun setPlaybackPressure(pressure: Boolean) {
        if (playbackPressure == pressure) return
        playbackPressure = pressure
        applyPlaybackPressure()
    }

    /** Cancela la resolución/descarga en curso y olvida el episodio activo. */
    fun cancelVideoPrefetch() {
        videoPrefetchJob?.cancel()
        videoPrefetchJob = null
        activeVideoPrefetchVideoId = null
        pausedByPlaybackPressure = false
    }

    /** El próximo episodio cambió: descarta una precarga antigua que ya no sirve. */
    fun onNextEpisodeChanged(videoId: String?) {
        val normalized = videoId?.trim().orEmpty()
        val active = activeVideoPrefetchVideoId ?: return
        if (normalized == active) return
        discardIncompletePrefetch(active)
        activeVideoPrefetchVideoId = null
        pausedByPlaybackPressure = false
    }

    /**
     * El episodio precargado pasó a ser el episodio actual. Si todavía estaba
     * descargándose ya no se puede usar para esta reproducción, así que se elimina
     * para que no compita con el stream que se está viendo.
     */
    fun onActiveVideoChanged(videoId: String?) {
        val normalized = videoId?.trim().orEmpty()
        if (normalized.isEmpty()) return
        if (activeVideoPrefetchVideoId != normalized) return
        discardIncompletePrefetch(normalized)
        activeVideoPrefetchVideoId = null
        pausedByPlaybackPressure = false
    }

    private fun discardIncompletePrefetch(videoId: String) {
        val item = DownloadsRepository.findPrefetchDownloadByVideoId(videoId) ?: return
        if (item.status != DownloadStatus.Completed) {
            DownloadsRepository.cancelDownload(item.id)
        }
    }

    private fun applyPlaybackPressure() {
        val videoId = activeVideoPrefetchVideoId ?: return
        val item = DownloadsRepository.findPrefetchDownloadByVideoId(videoId) ?: return
        if (playbackPressure) {
            if (item.status == DownloadStatus.Downloading) {
                DownloadsRepository.pauseDownload(item.id)
                pausedByPlaybackPressure = true
            }
            return
        }
        if (pausedByPlaybackPressure && item.status == DownloadStatus.Paused) {
            pausedByPlaybackPressure = false
            DownloadsRepository.resumeDownload(item.id)
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
        // The user may have moved to another next episode while the stream URLs were
        // being resolved. Do not enqueue a download that is already obsolete.
        if (activeVideoPrefetchVideoId != videoId) return

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
