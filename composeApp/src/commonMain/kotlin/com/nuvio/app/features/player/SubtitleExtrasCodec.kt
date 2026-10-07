package com.nuvio.app.features.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Campos extra del estilo de subtítulos (sombra y fuente), guardados juntos como JSON. */
internal fun SubtitleStyleState.encodeExtras(): String = buildJsonObject {
    put("shadowEnabled", shadowEnabled)
    put("shadowOffset", shadowOffset)
    put("shadowColor", shadowColor.toStorageHexString())
    put("fontName", fontName)
    put("fontsDir", fontsDir)
}.toString()

internal fun SubtitleStyleState.withStoredExtras(json: String?): SubtitleStyleState {
    if (json.isNullOrBlank()) return this
    val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return this
    return copy(
        shadowEnabled = obj["shadowEnabled"]?.jsonPrimitive?.booleanOrNull ?: shadowEnabled,
        shadowOffset = (obj["shadowOffset"]?.jsonPrimitive?.intOrNull ?: shadowOffset).coerceIn(1, 8),
        shadowColor = subtitleColorFromStorage(obj["shadowColor"]?.jsonPrimitive?.contentOrNull) ?: shadowColor,
        fontName = obj["fontName"]?.jsonPrimitive?.contentOrNull ?: fontName,
        fontsDir = obj["fontsDir"]?.jsonPrimitive?.contentOrNull ?: fontsDir,
    )
}
