package com.nuvio.app.features.player

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

private val subtitleExtensions = setOf("ass", "ssa", "srt", "vtt", "sub")

actual fun pickSubtitleFile(): String? {
    // Los eventos del reproductor llegan desde otro hilo: el diálogo debe abrirse en el hilo de AWT.
    if (SwingUtilities.isEventDispatchThread()) return pickSubtitleOnAwtThread()
    var result: String? = null
    runCatching { SwingUtilities.invokeAndWait { result = pickSubtitleOnAwtThread() } }
    return result
}

private fun pickSubtitleOnAwtThread(): String? {
    val dialog = FileDialog(null as Frame?, "Seleccionar subtítulo (.ass / .srt / .vtt)", FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val file = File(dialog.directory, fileName)
    if (file.extension.lowercase() !in subtitleExtensions) return null
    return file.absolutePath
}
