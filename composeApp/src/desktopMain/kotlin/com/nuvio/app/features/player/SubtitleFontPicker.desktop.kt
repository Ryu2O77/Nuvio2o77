package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import java.awt.Font
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.SwingUtilities

actual fun pickSubtitleFontFile(): PickedSubtitleFont? {
    // Los eventos del reproductor llegan desde otro hilo: el diálogo debe abrirse en el hilo de AWT.
    if (SwingUtilities.isEventDispatchThread()) return pickOnAwtThread()
    var result: PickedSubtitleFont? = null
    runCatching { SwingUtilities.invokeAndWait { result = pickOnAwtThread() } }
    return result
}

private fun pickOnAwtThread(): PickedSubtitleFont? {
    val dialog = FileDialog(null as Frame?, "Seleccionar fuente (.ttf / .otf)", FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val source = File(dialog.directory, fileName)
    val extension = source.extension.lowercase()
    if (extension != "ttf" && extension != "otf") return null

    return runCatching {
        // Nombre de familia que mpv necesita para sub-font (no es el nombre del archivo).
        val family = runCatching { Font.createFont(Font.TRUETYPE_FONT, source).family }
            .getOrDefault(source.nameWithoutExtension)
        val fontsDir = DesktopStorage.rootDir.resolve("subtitle-fonts")
        Files.createDirectories(fontsDir)
        Files.copy(source.toPath(), fontsDir.resolve(source.name), StandardCopyOption.REPLACE_EXISTING)
        PickedSubtitleFont(family = family, directory = fontsDir.toString())
    }.getOrNull()
}
