package com.nuvio.app.features.player.desktop

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Algunos addons entregan subtítulos con las comillas escapadas (\" en lugar de ").
 * mpv muestra la barra invertida tal cual. Aquí se descarga el subtítulo, se corrige
 * y se devuelve una copia local; si no hay nada que corregir o algo falla, devuelve
 * null y se usa la URL original.
 */
internal object SubtitleTextSanitizer {
    private const val MAX_BYTES = 5_000_000
    private const val BACKSLASH: Byte = 0x5C
    private const val QUOTE: Byte = 0x22

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build()
    }

    fun cleanedLocalCopy(url: String): String? {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) return null
            val bytes = response.body()
            if (bytes.isEmpty() || bytes.size > MAX_BYTES || looksUtf16(bytes)) return null
            val cleaned = unescapeQuotes(bytes) ?: return null
            val file = File.createTempFile("nuvio-sub-", extensionFor(bytes))
            file.deleteOnExit()
            file.writeBytes(cleaned)
            file.absolutePath
        }.getOrNull()
    }

    private fun looksUtf16(bytes: ByteArray): Boolean =
        bytes.size >= 2 && (
            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            )

    /** Reemplaza \" por " byte a byte (seguro en UTF-8 y codificaciones ASCII). Null si no había ninguno. */
    private fun unescapeQuotes(bytes: ByteArray): ByteArray? {
        var count = 0
        var i = 0
        while (i < bytes.size - 1) {
            if (bytes[i] == BACKSLASH && bytes[i + 1] == QUOTE) {
                count++
                i += 2
            } else {
                i++
            }
        }
        if (count == 0) return null
        val out = ByteArray(bytes.size - count)
        var read = 0
        var write = 0
        while (read < bytes.size) {
            if (read < bytes.size - 1 && bytes[read] == BACKSLASH && bytes[read + 1] == QUOTE) {
                out[write++] = QUOTE
                read += 2
            } else {
                out[write++] = bytes[read++]
            }
        }
        return out
    }

    private fun extensionFor(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 300), Charsets.ISO_8859_1).lowercase()
        return when {
            "[script info]" in head -> ".ass"
            head.trimStart('\uFEFF', 'ï', '»', '¿', ' ', '\n', '\r').startsWith("webvtt") -> ".vtt"
            else -> ".srt"
        }
    }
}
