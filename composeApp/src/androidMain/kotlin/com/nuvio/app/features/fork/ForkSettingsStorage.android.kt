package com.nuvio.app.features.fork

import android.content.Context
import android.content.SharedPreferences

internal actual object ForkSettingsStorage {
    private const val preferencesName = "nuvio_fork_settings"
    private const val subtitleDragDropKey = "subtitle_drag_drop_enabled"
    private const val subtitleLoadFileKey = "subtitle_load_file_enabled"
    private const val prefetchEnabledKey = "continue_watching_prefetch_enabled"
    private const val prefetchCountKey = "continue_watching_prefetch_count"
    private const val prefetchTtlKey = "continue_watching_prefetch_ttl_hours"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadSubtitleDragDropEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(subtitleDragDropKey)) prefs.getBoolean(subtitleDragDropKey, true) else null
        }

    actual fun saveSubtitleDragDropEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(subtitleDragDropKey, enabled)?.apply()
    }

    actual fun loadSubtitleLoadFileEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(subtitleLoadFileKey)) prefs.getBoolean(subtitleLoadFileKey, true) else null
        }

    actual fun saveSubtitleLoadFileEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(subtitleLoadFileKey, enabled)?.apply()
    }

    actual fun loadContinueWatchingPrefetchEnabled(): Boolean? =
        preferences?.let { prefs ->
            if (prefs.contains(prefetchEnabledKey)) prefs.getBoolean(prefetchEnabledKey, true) else null
        }

    actual fun saveContinueWatchingPrefetchEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(prefetchEnabledKey, enabled)?.apply()
    }

    actual fun loadContinueWatchingPrefetchCount(): Int? =
        preferences?.let { prefs ->
            if (prefs.contains(prefetchCountKey)) prefs.getInt(prefetchCountKey, 10) else null
        }

    actual fun saveContinueWatchingPrefetchCount(count: Int) {
        preferences?.edit()?.putInt(prefetchCountKey, count)?.apply()
    }

    actual fun loadContinueWatchingPrefetchTtlHours(): Int? =
        preferences?.let { prefs ->
            if (prefs.contains(prefetchTtlKey)) prefs.getInt(prefetchTtlKey, 12) else null
        }

    actual fun saveContinueWatchingPrefetchTtlHours(hours: Int) {
        preferences?.edit()?.putInt(prefetchTtlKey, hours)?.apply()
    }
}
