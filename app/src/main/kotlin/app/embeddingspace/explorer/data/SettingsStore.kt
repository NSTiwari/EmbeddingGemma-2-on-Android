package app.embeddingspace.explorer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Settings kept in DataStore. Light is the default theme. */
class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val viewMode = stringPreferencesKey("view_mode")
        val hintDismissed = booleanPreferencesKey("demo_hint_dismissed")
        val demoSeeded = booleanPreferencesKey("demo_seeded")
        val additions = intPreferencesKey("additions_since_relayout")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT,
        viewMode = this[K.viewMode]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.SPACE,
        demoHintDismissed = this[K.hintDismissed] ?: false,
        additionsSinceRelayout = this[K.additions] ?: 0,
    )

    suspend fun setTheme(m: ThemeMode) = context.dataStore.edit { it[K.theme] = m.name }
    suspend fun setViewMode(m: ViewMode) = context.dataStore.edit { it[K.viewMode] = m.name }
    suspend fun setHintDismissed(v: Boolean) = context.dataStore.edit { it[K.hintDismissed] = v }
    suspend fun setAdditions(n: Int) = context.dataStore.edit { it[K.additions] = n }

    /** True once the demo has been put on the map. It is never put there again. */
    suspend fun demoSeeded(): Boolean = context.dataStore.data.first()[K.demoSeeded] ?: false

    suspend fun setDemoSeeded() = context.dataStore.edit { it[K.demoSeeded] = true }
}
