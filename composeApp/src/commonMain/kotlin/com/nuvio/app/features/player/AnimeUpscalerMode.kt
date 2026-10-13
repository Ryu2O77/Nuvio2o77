package com.nuvio.app.features.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Modos de Anime4K (shaders de mejora de imagen para anime). */
enum class AnimeUpscalerMode(
    val index: Int,
    val storageKey: String,
    val label: String,
    val description: String,
) {
    FAST(
        index = 0,
        storageKey = "fast",
        label = "Rápido",
        description = "Para PCs modestos y portátiles",
    ),
    LINE_RECOVERY(
        index = 1,
        storageKey = "line_recovery",
        label = "Suave y claro",
        description = "Líneas suaves y detalle equilibrado",
    ),
    SHARP_DETAIL(
        index = 2,
        storageKey = "sharp_detail",
        label = "Nítido y detallado",
        description = "Máxima claridad y líneas finas",
    );

    companion object {
        fun fromIndex(index: Int): AnimeUpscalerMode =
            entries.firstOrNull { it.index == index } ?: FAST

        fun fromStorageKey(key: String?): AnimeUpscalerMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: FAST
    }
}

internal fun encodeAnimeUpscaler(enabled: Boolean, mode: AnimeUpscalerMode): String =
    buildJsonObject {
        put("enabled", enabled)
        put("mode", mode.storageKey)
    }.toString()

internal fun decodeAnimeUpscaler(json: String?): Pair<Boolean, AnimeUpscalerMode> {
    if (json.isNullOrBlank()) return false to AnimeUpscalerMode.FAST
    val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        ?: return false to AnimeUpscalerMode.FAST
    val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false
    val mode = AnimeUpscalerMode.fromStorageKey(obj["mode"]?.jsonPrimitive?.contentOrNull)
    return enabled to mode
}
