package com.nuvio.app.features.streams

import com.nuvio.app.features.autosync.EmbeddedSubtitleTimelineLoader
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Subtitle tracks muxed into a stream, as read from its own container. This is deliberately
 * separate from [StreamItem.externalSubtitles]: those come from add-ons, this is what the file
 * itself carries.
 */
internal sealed interface MuxedSubtitleResult {
    /** The container was read and it has no subtitle tracks. */
    data object NoTracks : MuxedSubtitleResult

    /** The container could not be read: no range support, unsupported format, timeout... */
    data object Unavailable : MuxedSubtitleResult

    /** Short language labels such as `ESP` or `ENG`, in the order the file declares them. */
    data class Tracks(val labels: List<String>) : MuxedSubtitleResult
}

/**
 * Reads the subtitle track list of one stream on demand, so nothing is downloaded until the user
 * asks for it from the stream row. Results are cached per URL for the session.
 */
internal object MuxedSubtitleRepository {
    private const val MAX_CACHE_ENTRIES = 400
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_LABELS = 8
    private const val UNKNOWN_LANGUAGE_LABEL = "SUB"
    private const val RESOLVE_TIMEOUT_MS = 25_000L

    private data class CacheEntry(
        val result: MuxedSubtitleResult,
        val createdAtMs: Long,
    )

    private val lock = SynchronizedObject()
    private val cache = LinkedHashMap<String, CacheEntry>()

    fun cached(stream: StreamItem): MuxedSubtitleResult? {
        val key = cacheKey(stream) ?: return null
        val now = epochMs()
        return synchronized(lock) {
            val entry = cache[key]
            if (entry == null) return@synchronized null
            if (now - entry.createdAtMs > CACHE_TTL_MS) {
                cache.remove(key)
                return@synchronized null
            }
            entry.result
        }
    }

    fun clearCache() {
        synchronized(lock) { cache.clear() }
    }

    suspend fun probe(stream: StreamItem, force: Boolean = false): MuxedSubtitleResult {
        val key = cacheKey(stream) ?: return MuxedSubtitleResult.Unavailable
        if (!force) cached(stream)?.let { return it }

        val target = try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveTarget(stream) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        } ?: return MuxedSubtitleResult.Unavailable

        val result = try {
            when (val tracks = EmbeddedSubtitleTimelineLoader.probeSubtitleTracks(target.url, target.headers)) {
                null -> MuxedSubtitleResult.Unavailable
                else -> tracksToResult(tracks)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            MuxedSubtitleResult.Unavailable
        }

        synchronized(lock) {
            cache[key] = CacheEntry(result = result, createdAtMs = epochMs())
            while (cache.size > MAX_CACHE_ENTRIES) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return result
    }

    /**
     * Finds the HTTP source to inspect: the stream's own link, the debrid link the list already
     * prepared, or one resolved on demand. Torrents with no debrid setup return null.
     */
    private suspend fun resolveTarget(stream: StreamItem): ProbeTarget? {
        stream.probeTarget()?.let { return it }

        val prepared = try {
            DirectDebridPlaybackResolver.cachedPlayableStream(stream, season = null, episode = null)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        prepared?.probeTarget()?.let { return it }

        if (!DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) return null
        val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
            stream = stream,
            season = null,
            episode = null,
        )
        val resolvedStream = (resolved as? DirectDebridPlayableResult.Success)?.stream ?: return null
        return resolvedStream.probeTarget()
    }

    private fun cacheKey(stream: StreamItem): String? {
        val identity = stream.p2pInfoHash
            ?: stream.clientResolve?.let { resolve ->
                listOf(
                    resolve.infoHash.orEmpty(),
                    resolve.magnetUri.orEmpty(),
                    resolve.torrentName.orEmpty(),
                    resolve.filename.orEmpty(),
                    resolve.fileIdx?.toString().orEmpty(),
                ).joinToString("|")
            }
            ?: stream.behaviorHints.filename
        if (!identity.isNullOrBlank()) {
            return "${stream.addonId}|${identity.trim().lowercase()}|${stream.fileIdx?.toString().orEmpty()}"
        }
        return stream.probeTarget()?.url
    }

    private fun tracksToResult(
        tracks: List<EmbeddedSubtitleTimelineLoader.EmbeddedSubtitleTrackInfo>,
    ): MuxedSubtitleResult {
        if (tracks.isEmpty()) return MuxedSubtitleResult.NoTracks
        val labels = tracks
            .asSequence()
            .filterNot { it.commentary }
            .map { track -> muxedSubtitleLanguageLabel(track.languageTag) ?: UNKNOWN_LANGUAGE_LABEL }
            .distinct()
            .take(MAX_LABELS)
            .toList()
        return if (labels.isEmpty()) MuxedSubtitleResult.NoTracks else MuxedSubtitleResult.Tracks(labels)
    }
}

/** True when the stream can be read over HTTP, directly or after resolving its debrid link. */
internal fun StreamItem.canProbeMuxedSubtitles(): Boolean =
    probeTarget() != null || DirectDebridPlaybackResolver.shouldResolveToPlayableStream(this)

private fun StreamItem.probeTarget(): ProbeTarget? {
    val url = playableDirectUrl?.takeIf { raw ->
        raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)
    } ?: return null
    return ProbeTarget(
        url = url,
        headers = behaviorHints.proxyHeaders?.request.orEmpty(),
    )
}

private data class ProbeTarget(
    val url: String,
    val headers: Map<String, String>,
)

/** Labels to show once the user filter of the settings is applied. */
internal fun MuxedSubtitleResult.visibleLabels(filter: Set<String>): List<String> = when (this) {
    is MuxedSubtitleResult.Tracks ->
        if (filter.isEmpty()) labels else labels.filter { label -> label in filter }
    else -> emptyList()
}

/** One entry of the language filter in the fork settings. */
internal data class MuxedSubtitleLanguageOption(
    val code: String,
    val label: String,
)

internal val MuxedSubtitleLanguageOptions: List<MuxedSubtitleLanguageOption> = listOf(
    MuxedSubtitleLanguageOption("ESP", "Español"),
    MuxedSubtitleLanguageOption("ENG", "Inglés"),
    MuxedSubtitleLanguageOption("POR", "Portugués"),
    MuxedSubtitleLanguageOption("FRA", "Francés"),
    MuxedSubtitleLanguageOption("DEU", "Alemán"),
    MuxedSubtitleLanguageOption("ITA", "Italiano"),
    MuxedSubtitleLanguageOption("JPN", "Japonés"),
    MuxedSubtitleLanguageOption("KOR", "Coreano"),
    MuxedSubtitleLanguageOption("CHI", "Chino"),
    MuxedSubtitleLanguageOption("RUS", "Ruso"),
    MuxedSubtitleLanguageOption("ARA", "Árabe"),
    MuxedSubtitleLanguageOption("HIN", "Hindi"),
    MuxedSubtitleLanguageOption("TUR", "Turco"),
    MuxedSubtitleLanguageOption("POL", "Polaco"),
    MuxedSubtitleLanguageOption("NLD", "Neerlandés"),
    MuxedSubtitleLanguageOption("SWE", "Sueco"),
    MuxedSubtitleLanguageOption("NOR", "Noruego"),
    MuxedSubtitleLanguageOption("DAN", "Danés"),
    MuxedSubtitleLanguageOption("FIN", "Finés"),
    MuxedSubtitleLanguageOption("CES", "Checo"),
    MuxedSubtitleLanguageOption("ELL", "Griego"),
    MuxedSubtitleLanguageOption("HUN", "Húngaro"),
    MuxedSubtitleLanguageOption("RON", "Rumano"),
    MuxedSubtitleLanguageOption("UKR", "Ucraniano"),
    MuxedSubtitleLanguageOption("HEB", "Hebreo"),
    MuxedSubtitleLanguageOption("THA", "Tailandés"),
    MuxedSubtitleLanguageOption("VIE", "Vietnamita"),
    MuxedSubtitleLanguageOption("IND", "Indonesio"),
    MuxedSubtitleLanguageOption("MSA", "Malayo"),
    MuxedSubtitleLanguageOption("TGL", "Filipino"),
    MuxedSubtitleLanguageOption("CAT", "Catalán"),
    MuxedSubtitleLanguageOption("GLG", "Gallego"),
    MuxedSubtitleLanguageOption("EUS", "Vasco"),
    MuxedSubtitleLanguageOption("HRV", "Croata"),
    MuxedSubtitleLanguageOption("BUL", "Búlgaro"),
    MuxedSubtitleLanguageOption("SRP", "Serbio"),
    MuxedSubtitleLanguageOption("SLK", "Eslovaco"),
    MuxedSubtitleLanguageOption("SLV", "Esloveno"),
)

/**
 * Turns an ISO 639-1 or 639-2 language tag, as stored in Matroska/MP4 tracks, into the short
 * uppercase label shown in the stream row. Unknown tags fall back to their own first letters.
 */
internal fun muxedSubtitleLanguageLabel(tag: String?): String? {
    val primary = tag
        ?.trim()
        ?.lowercase()
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    if (primary in UNKNOWN_LANGUAGE_TAGS) return null
    val label = when (primary) {
        "es", "spa" -> "ESP"
        "en", "eng" -> "ENG"
        "pt", "por" -> "POR"
        "fr", "fra", "fre" -> "FRA"
        "de", "deu", "ger" -> "DEU"
        "it", "ita" -> "ITA"
        "ja", "jpn" -> "JPN"
        "ko", "kor" -> "KOR"
        "zh", "zho", "chi" -> "CHI"
        "ru", "rus" -> "RUS"
        "ar", "ara" -> "ARA"
        "hi", "hin" -> "HIN"
        "tr", "tur" -> "TUR"
        "pl", "pol" -> "POL"
        "nl", "nld", "dut" -> "NLD"
        "sv", "swe" -> "SWE"
        "no", "nor", "nb", "nob", "nn", "nno" -> "NOR"
        "da", "dan" -> "DAN"
        "fi", "fin" -> "FIN"
        "cs", "ces", "cze" -> "CES"
        "el", "ell", "gre" -> "ELL"
        "hu", "hun" -> "HUN"
        "ro", "ron", "rum" -> "RON"
        "uk", "ukr" -> "UKR"
        "he", "heb" -> "HEB"
        "th", "tha" -> "THA"
        "vi", "vie" -> "VIE"
        "id", "ind" -> "IND"
        "ms", "msa", "may" -> "MSA"
        "tl", "tgl", "fil" -> "TGL"
        "ca", "cat" -> "CAT"
        "gl", "glg" -> "GLG"
        "eu", "eus", "baq" -> "EUS"
        "hr", "hrv" -> "HRV"
        "bg", "bul" -> "BUL"
        "sr", "srp" -> "SRP"
        "sk", "slk", "slo" -> "SLK"
        "sl", "slv" -> "SLV"
        "lt", "lit" -> "LIT"
        "lv", "lav" -> "LAV"
        "et", "est" -> "EST"
        "is", "isl", "ice" -> "ISL"
        "sq", "sqi", "alb" -> "SQI"
        "mk", "mkd", "mac" -> "MKD"
        "bs", "bos" -> "BOS"
        "be", "bel" -> "BEL"
        "ka", "kat", "geo" -> "KAT"
        "hy", "hye", "arm" -> "HYE"
        "az", "aze" -> "AZE"
        "kk", "kaz" -> "KAZ"
        "uz", "uzb" -> "UZB"
        "bn", "ben" -> "BEN"
        "ta", "tam" -> "TAM"
        "te", "tel" -> "TEL"
        "ml", "mal" -> "MAL"
        "kn", "kan" -> "KAN"
        "mr", "mar" -> "MAR"
        "gu", "guj" -> "GUJ"
        "pa", "pan" -> "PAN"
        "ur", "urd" -> "URD"
        "ne", "nep" -> "NEP"
        "si", "sin" -> "SIN"
        "km", "khm" -> "KHM"
        "lo", "lao" -> "LAO"
        "my", "mya", "bur" -> "MYA"
        "mn", "mon" -> "MON"
        "am", "amh" -> "AMH"
        "sw", "swa" -> "SWA"
        "zu", "zul" -> "ZUL"
        "af", "afr" -> "AFR"
        else -> null
    }
    return label ?: primary.uppercase().takeIf { it.length in 2..3 }
}

private val UNKNOWN_LANGUAGE_TAGS = setOf("und", "unk", "unknown", "mul", "zxx", "none", "mis")
