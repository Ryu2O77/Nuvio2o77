package com.nuvio.app.features.streams

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Caché de las respuestas crudas de los addons de streams, indexada por contenido.
 *
 * Se guarda el JSON tal cual lo devuelve cada addon para poder reparsearlo con [StreamParser]
 * sin necesidad de serializar [StreamItem]. Una entrada por contenido con todos sus addons.
 */
internal object AddonStreamsCache {
    private const val indexStorageKey = "addon_stream_cache_index"
    private const val contentStoragePrefix = "addon_stream_content_"
    internal const val MaxCachedContents: Int = 12
    private const val MaxPayloadChars: Int = 250_000
    private const val MaxContentEntryChars: Int = 1_200_000

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = SynchronizedObject()

    fun getPayloads(contentKey: String, maxAgeMs: Long): Map<String, String> = synchronized(lock) {
        if (maxAgeMs <= 0L) return emptyMap()
        val storageKey = contentStorageKey(contentKey)
        val raw = StreamLinkCacheStorage.loadEntry(storageKey) ?: return emptyMap()
        val stored = runCatching { json.decodeFromString<StoredContentStreams>(raw) }.getOrNull()
            ?: run {
                StreamLinkCacheStorage.removeEntry(storageKey)
                return emptyMap()
            }
        val now = epochMs()
        if (stored.savedAtMs <= 0L || now - stored.savedAtMs > maxAgeMs) {
            StreamLinkCacheStorage.removeEntry(storageKey)
            removeFromIndex(contentKey)
            return emptyMap()
        }
        stored.payloads
            .asSequence()
            .filter { it.payload.isNotBlank() }
            .filter { it.savedAtMs <= 0L || now - it.savedAtMs <= maxAgeMs }
            .associate { it.addonId to it.payload }
    }

    fun saveContent(contentKey: String, payloads: Map<String, String>) = synchronized(lock) {
        val usable = payloads.filterValues { it.isNotBlank() && it.length <= MaxPayloadChars }
        if (usable.isEmpty()) return

        val now = epochMs()
        var stored = StoredContentStreams(
            savedAtMs = now,
            payloads = usable.map { (addonId, payload) ->
                StoredAddonStreamPayload(
                    addonId = addonId,
                    payload = payload,
                    savedAtMs = now,
                )
            },
        )
        var encoded = json.encodeToString(StoredContentStreams.serializer(), stored)
        if (encoded.length > MaxContentEntryChars) {
            val budgeted = mutableListOf<StoredAddonStreamPayload>()
            var total = 0
            stored.payloads
                .sortedBy { it.payload.length }
                .forEach { item ->
                    if (total + item.payload.length > MaxContentEntryChars) return@forEach
                    budgeted += item
                    total += item.payload.length
                }
            if (budgeted.isEmpty()) return
            stored = stored.copy(payloads = budgeted)
            encoded = json.encodeToString(StoredContentStreams.serializer(), stored)
        }

        val storageKey = contentStorageKey(contentKey)
        StreamLinkCacheStorage.saveEntry(storageKey, encoded)

        val index = loadIndex()
        val updatedEntries = buildList {
            add(
                AddonStreamsCacheIndexEntry(
                    contentKey = contentKey,
                    storageKey = storageKey,
                    savedAtMs = now,
                ),
            )
            index.entries.forEach { entry ->
                if (entry.contentKey == contentKey) return@forEach
                add(entry)
            }
        }
        val trimmed = updatedEntries.take(MaxCachedContents)
        updatedEntries.drop(MaxCachedContents).forEach { dropped ->
            StreamLinkCacheStorage.removeEntry(dropped.storageKey)
        }
        saveIndex(AddonStreamsCacheIndex(entries = trimmed))
    }

    fun hasContent(contentKey: String, maxAgeMs: Long): Boolean =
        getPayloads(contentKey, maxAgeMs).isNotEmpty()

    fun remove(contentKey: String) = synchronized(lock) {
        StreamLinkCacheStorage.removeEntry(contentStorageKey(contentKey))
        removeFromIndex(contentKey)
    }

    fun clear() = synchronized(lock) {
        val index = loadIndex()
        index.entries.forEach { entry ->
            StreamLinkCacheStorage.removeEntry(entry.storageKey)
        }
        StreamLinkCacheStorage.removeEntry(indexStorageKey)
    }

    fun cachedContentCount(): Int = synchronized(lock) {
        loadIndex().entries.size
    }

    private fun removeFromIndex(contentKey: String) {
        val index = loadIndex()
        val remaining = index.entries.filterNot { it.contentKey == contentKey }
        if (remaining.size != index.entries.size) {
            saveIndex(index.copy(entries = remaining))
        }
    }

    private fun loadIndex(): AddonStreamsCacheIndex {
        val raw = StreamLinkCacheStorage.loadEntry(indexStorageKey) ?: return AddonStreamsCacheIndex()
        return runCatching { json.decodeFromString<AddonStreamsCacheIndex>(raw) }.getOrNull()
            ?: AddonStreamsCacheIndex()
    }

    private fun saveIndex(index: AddonStreamsCacheIndex) {
        if (index.entries.isEmpty()) {
            StreamLinkCacheStorage.removeEntry(indexStorageKey)
            return
        }
        StreamLinkCacheStorage.saveEntry(
            indexStorageKey,
            json.encodeToString(AddonStreamsCacheIndex.serializer(), index),
        )
    }

    private fun contentStorageKey(contentKey: String): String {
        val hash = contentKey.fold(0L) { acc, c -> acc * 31 + c.code }.toULong()
        return "$contentStoragePrefix$hash"
    }
}

@Serializable
private data class StoredContentStreams(
    val savedAtMs: Long = 0L,
    val payloads: List<StoredAddonStreamPayload> = emptyList(),
)

@Serializable
private data class StoredAddonStreamPayload(
    val addonId: String,
    val payload: String,
    val savedAtMs: Long = 0L,
)

@Serializable
private data class AddonStreamsCacheIndex(
    val entries: List<AddonStreamsCacheIndexEntry> = emptyList(),
)

@Serializable
private data class AddonStreamsCacheIndexEntry(
    val contentKey: String,
    val storageKey: String,
    val savedAtMs: Long,
)
