package com.nuvio.app.features.player

import androidx.compose.ui.graphics.Color
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
    put("shadowSoftness", shadowSoftness)
    put("shadowOpacity", shadowOpacity)
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
        shadowSoftness = (obj["shadowSoftness"]?.jsonPrimitive?.intOrNull ?: shadowSoftness).coerceIn(0, 10),
        shadowOpacity = (obj["shadowOpacity"]?.jsonPrimitive?.intOrNull ?: shadowOpacity).coerceIn(0, 100),
        shadowColor = subtitleColorFromStorage(obj["shadowColor"]?.jsonPrimitive?.contentOrNull) ?: shadowColor,
        fontName = obj["fontName"]?.jsonPrimitive?.contentOrNull ?: fontName,
        fontsDir = obj["fontsDir"]?.jsonPrimitive?.contentOrNull ?: fontsDir,
    )
}

/** Colores de sombra ofrecidos en el panel del reproductor (el primero es el valor por defecto). La opacidad se ajusta aparte. */
internal val SubtitleShadowColorSwatches = listOf(
    Color.Black,
    Color(0xFF1F2937),
    Color(0xFF7F1D1D),
    Color(0xFF1E3A8A),
    Color(0xFF064E3B),
    Color.White,
)

/** Fuentes ofrecidas en el panel del reproductor. "" = predeterminada. */
internal val SubtitleFontChoices = listOf(
    "", "Netflix Sans", "Arial", "Helvetica Neue", "Segoe UI", "Verdana",
    "Tahoma", "Georgia", "Trebuchet MS", "Comic Sans MS", "Impact",
)
