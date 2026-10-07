package com.nuvio.app.features.player.desktop

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Resuelve el nombre de familia de fuente que se le pasa a mpv (sub-font).
 *
 * mpv no avisa cuando una fuente no existe: simplemente usa otra. Por eso
 * comprobamos primero si la fuente está instalada (o está en la carpeta de
 * fuentes propias del usuario) y, si no, caemos a una alternativa parecida.
 */
internal object SubtitleFontResolver {
    private val installedFamilies: Set<String> by lazy {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private val customCache = ConcurrentHashMap<String, Boolean>()
    private val fallbackChain = listOf("Helvetica Neue", "Arial")

    fun isInstalled(name: String): Boolean =
        name.isNotBlank() && name.lowercase() in installedFamilies

    private fun isInFontsDir(name: String, fontsDir: String): Boolean {
        if (fontsDir.isBlank()) return false
        return customCache.getOrPut("$fontsDir|${name.lowercase()}") {
            runCatching {
                File(fontsDir).listFiles().orEmpty().any { file ->
                    val ext = file.extension.lowercase()
                    (ext == "ttf" || ext == "otf") && (
                        file.nameWithoutExtension.equals(name, ignoreCase = true) ||
                            runCatching {
                                Font.createFont(Font.TRUETYPE_FONT, file).family.equals(name, ignoreCase = true)
                            }.getOrDefault(false)
                        )
                }
            }.getOrDefault(false)
        }
    }

    fun resolve(requested: String, fontsDir: String = ""): String {
        if (requested.isBlank()) return ""
        if (isInFontsDir(requested, fontsDir)) return requested
        if (isInstalled(requested)) return requested
        return fallbackChain.firstOrNull(::isInstalled) ?: ""
    }
}
