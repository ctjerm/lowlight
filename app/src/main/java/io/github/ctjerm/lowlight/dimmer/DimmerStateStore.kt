package io.github.ctjerm.lowlight.dimmer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

class DimmerStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun level(): DimmerLevel = DimmerLevel.from(
        preferences.getInt(KEY_LEVEL_PERCENT, DimmerLevel.DEFAULT_PERCENT),
    )

    fun activationSource(): ActivationSource = preferences
        .getString(KEY_ACTIVATION_SOURCE, null)
        ?.let { stored -> runCatching { ActivationSource.valueOf(stored) }.getOrNull() }
        ?: ActivationSource.MANUAL

    fun activate(level: DimmerLevel, source: ActivationSource) {
        preferences.edit {
            putBoolean(KEY_ENABLED, true)
            putInt(KEY_LEVEL_PERCENT, level.percent)
            putString(KEY_ACTIVATION_SOURCE, source.name)
        }
    }

    fun deactivate() {
        preferences.edit { putBoolean(KEY_ENABLED, false) }
    }

    fun setLevel(level: DimmerLevel) {
        preferences.edit { putInt(KEY_LEVEL_PERCENT, level.percent) }
    }

    fun userStoppedAt(): Instant? = preferences
        .takeIf { it.contains(KEY_USER_STOPPED_AT_EPOCH_MS) }
        ?.getLong(KEY_USER_STOPPED_AT_EPOCH_MS, 0L)
        ?.let(Instant::ofEpochMilli)

    fun recordUserStop(at: Instant) {
        preferences.edit { putLong(KEY_USER_STOPPED_AT_EPOCH_MS, at.toEpochMilli()) }
    }

    /** Calls [onChange] on the main thread after every change until the result is closed. */
    fun observeChanges(onChange: () -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val PREFERENCES_NAME = "dimmer_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEVEL_PERCENT = "level_percent"
        private const val KEY_ACTIVATION_SOURCE = "activation_source"
        private const val KEY_USER_STOPPED_AT_EPOCH_MS = "user_stopped_at_epoch_ms"
    }
}
