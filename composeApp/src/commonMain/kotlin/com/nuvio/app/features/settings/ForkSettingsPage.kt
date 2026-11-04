package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.fork.ForkSettingsRepository
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.streams.ContinueWatchingStreamsPrefetcher
import com.nuvio.app.isWindows
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_done
import org.jetbrains.compose.resources.stringResource

private val prefetchCountOptions = listOf(5, 10, 15, 20)
private val prefetchTtlOptions = listOf(3, 6, 12, 24, 48)
private val nextEpisodeMinutesOptions = listOf(1, 3, 5, 10, 15)

internal fun LazyListScope.forkSettingsContent(isTablet: Boolean) {
    item {
        ForkSettingsSectionContent(isTablet = isTablet)
    }
}

@Composable
private fun ForkSettingsSectionContent(isTablet: Boolean) {
    LaunchedEffect(Unit) {
        ForkSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.ensureLoaded()
    }

    val forkSettings by ForkSettingsRepository.uiState.collectAsStateWithLifecycle()
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    var showAnimeUpscalerDialog by remember { mutableStateOf(false) }
    var showPrefetchCountDialog by remember { mutableStateOf(false) }
    var showPrefetchTtlDialog by remember { mutableStateOf(false) }
    var showNextEpisodeMinutesDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 12.dp)) {
        if (isWindows) {
            SettingsSection(title = "Anime4K", isTablet = isTablet) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = "Activar Anime4K",
                        description = "Mejora la imagen del anime con shaders. Usa más la GPU.",
                        checked = playerSettings.animeUpscalerEnabled,
                        isTablet = isTablet,
                        onCheckedChange = PlayerSettingsRepository::setAnimeUpscalerEnabled,
                    )
                    if (playerSettings.animeUpscalerEnabled) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = "Modo de Anime4K",
                            description = playerSettings.animeUpscalerMode.label,
                            isTablet = isTablet,
                            onClick = { showAnimeUpscalerDialog = true },
                        )
                    }
                }
            }
        }

        SettingsSection(title = "Subtítulos externos", isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Arrastrar y soltar subtítulos",
                    description = "Permite soltar archivos de subtítulos sobre el reproductor.",
                    checked = forkSettings.subtitleDragDropEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ForkSettingsRepository::setSubtitleDragDropEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Botón para cargar subtítulo",
                    description = "Muestra el botón de cargar subtítulo desde archivo en los controles.",
                    checked = forkSettings.subtitleLoadFileEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ForkSettingsRepository::setSubtitleLoadFileEnabled,
                )
            }
        }

        SettingsSection(title = "Continuar viendo", isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Precargar streams de Continuar viendo",
                    description = "Descarga en segundo plano las fuentes de los primeros títulos para que abran más rápido.",
                    checked = forkSettings.continueWatchingPrefetchEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ForkSettingsRepository::setContinueWatchingPrefetchEnabled,
                )
                if (forkSettings.continueWatchingPrefetchEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = "Cantidad de títulos",
                        description = "${forkSettings.continueWatchingPrefetchCount} títulos",
                        isTablet = isTablet,
                        onClick = { showPrefetchCountDialog = true },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = "Duración de la caché",
                        description = "${forkSettings.continueWatchingPrefetchTtlHours} horas",
                        isTablet = isTablet,
                        onClick = { showPrefetchTtlDialog = true },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Borrar caché de streams",
                    description = "Elimina las respuestas de addons guardadas.",
                    isTablet = isTablet,
                    onClick = {
                        ContinueWatchingStreamsPrefetcher.clearCache()
                        NuvioToastController.show("Caché de streams borrada")
                    },
                )
            }
        }
        SettingsSection(title = "Siguiente episodio", isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Precargar el siguiente episodio",
                    description = "Empieza a descargar las fuentes del próximo episodio antes de que termine el actual.",
                    checked = forkSettings.nextEpisodePrefetchEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ForkSettingsRepository::setNextEpisodePrefetchEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Cachear también el video",
                    description = "Descarga el video del próximo episodio usando el primer enlace disponible. Puede ocupar varios GB.",
                    checked = forkSettings.nextEpisodeVideoPrefetchEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ForkSettingsRepository::setNextEpisodeVideoPrefetchEnabled,
                )
                if (forkSettings.nextEpisodePrefetchEnabled || forkSettings.nextEpisodeVideoPrefetchEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = "Minutos antes del final",
                        description = "${forkSettings.nextEpisodePrefetchMinutes} minutos",
                        isTablet = isTablet,
                        onClick = { showNextEpisodeMinutesDialog = true },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Borrar caché de video",
                    description = "Elimina los videos precargados del próximo episodio.",
                    isTablet = isTablet,
                    onClick = {
                        DownloadsRepository.clearPrefetchCache()
                        NuvioToastController.show("Caché de video borrada")
                    },
                )
            }
        }
    }

    if (showNextEpisodeMinutesDialog) {
        DialogSurface(
            onDismissRequest = { showNextEpisodeMinutesDialog = false },
            title = "Minutos antes del final",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nextEpisodeMinutesOptions.forEach { minutes ->
                    DialogOption(
                        text = "$minutes minutos",
                        selected = forkSettings.nextEpisodePrefetchMinutes == minutes,
                        onClick = {
                            ForkSettingsRepository.setNextEpisodePrefetchMinutes(minutes)
                            showNextEpisodeMinutesDialog = false
                        },
                    )
                }
            }

            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_done),
                    onClick = { showNextEpisodeMinutesDialog = false },
                )
            }
        }
    }

    if (showAnimeUpscalerDialog) {
        DialogSurface(
            onDismissRequest = { showAnimeUpscalerDialog = false },
            title = "Modo de Anime4K",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimeUpscalerMode.entries.forEach { mode ->
                    DialogOption(
                        text = mode.label,
                        description = mode.description,
                        selected = playerSettings.animeUpscalerMode == mode,
                        onClick = {
                            PlayerSettingsRepository.setAnimeUpscalerMode(mode)
                            showAnimeUpscalerDialog = false
                        },
                    )
                }
            }

            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_done),
                    onClick = { showAnimeUpscalerDialog = false },
                )
            }
        }
    }

    if (showPrefetchCountDialog) {
        DialogSurface(
            onDismissRequest = { showPrefetchCountDialog = false },
            title = "Cantidad de títulos",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                prefetchCountOptions.forEach { count ->
                    DialogOption(
                        text = "$count títulos",
                        selected = forkSettings.continueWatchingPrefetchCount == count,
                        onClick = {
                            ForkSettingsRepository.setContinueWatchingPrefetchCount(count)
                            showPrefetchCountDialog = false
                        },
                    )
                }
            }

            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_done),
                    onClick = { showPrefetchCountDialog = false },
                )
            }
        }
    }

    if (showPrefetchTtlDialog) {
        DialogSurface(
            onDismissRequest = { showPrefetchTtlDialog = false },
            title = "Duración de la caché",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                prefetchTtlOptions.forEach { hours ->
                    DialogOption(
                        text = "$hours horas",
                        selected = forkSettings.continueWatchingPrefetchTtlHours == hours,
                        onClick = {
                            ForkSettingsRepository.setContinueWatchingPrefetchTtlHours(hours)
                            showPrefetchTtlDialog = false
                        },
                    )
                }
            }

            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_done),
                    onClick = { showPrefetchTtlDialog = false },
                )
            }
        }
    }
}
