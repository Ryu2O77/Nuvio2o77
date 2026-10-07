package com.nuvio.app.features.player

data class PickedSubtitleFont(
    val family: String,
    val directory: String,
)

/** Abre un selector de archivos para elegir una fuente (.ttf / .otf). Solo escritorio; en otras plataformas devuelve null. */
expect fun pickSubtitleFontFile(): PickedSubtitleFont?
