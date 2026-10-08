package com.nuvio.app.features.player

/** Abre un selector de archivos para elegir un subtítulo local (.ass/.ssa/.srt/.vtt). Solo escritorio; en otras plataformas devuelve null. */
expect fun pickSubtitleFile(): String?

private val localSubtitleLanguageCodes = setOf(
    "es", "en", "fr", "pt", "it", "de", "ja", "ko", "zh", "ru", "ar", "pl",
    "tr", "nl", "sv", "id", "vi", "th", "hi", "el", "he", "uk", "cs", "ro", "hu",
)

/** Intenta deducir el idioma del nombre del archivo (por ejemplo "..._es.ass" o "... es-419.ass"). */
internal fun guessLocalSubtitleLanguage(fileName: String): String {
    val token = fileName.substringBeforeLast('.').split(' ', '_', '-', '.').lastOrNull().orEmpty().lowercase()
    val withRegion = fileName.substringBeforeLast('.').takeLast(6).lowercase()
    val region = Regex("(?:^|[ _.])([a-z]{2}-[0-9a-z]{2,4})$").find(withRegion)?.groupValues?.get(1)
    if (region != null && region.substringBefore('-') in localSubtitleLanguageCodes) return region
    return if (token in localSubtitleLanguageCodes) token else "und"
}

/** Convierte un archivo local en una entrada de la lista de subtítulos para que aparezca marcada como externa. */
internal fun localSubtitleFor(path: String): AddonSubtitle {
    val name = path.substringAfterLast('\\').substringAfterLast('/')
    return AddonSubtitle(
        id = "local:$path",
        url = path,
        language = guessLocalSubtitleLanguage(name),
        display = "$name (External)",
        addonName = "Archivo local",
        isSelected = true,
    )
}
