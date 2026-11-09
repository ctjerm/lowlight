package io.github.ctjerm.lowlight.schedule

import android.content.Context
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class ScheduleStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun schedule(): WeeklySchedule = WeeklySchedule(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        start = LocalTime.ofSecondOfDay(
            preferences
                .getInt(KEY_START_MINUTE, WeeklySchedule.DEFAULT_START.minuteOfDay())
                .coerceIn(MIN_MINUTE, MAX_MINUTE) * 60L,
        ),
        end = LocalTime.ofSecondOfDay(
            preferences
                .getInt(KEY_END_MINUTE, WeeklySchedule.DEFAULT_END.minuteOfDay())
                .coerceIn(MIN_MINUTE, MAX_MINUTE) * 60L,
        ),
        days = decodeDays(preferences.getInt(KEY_DAYS, ALL_DAYS_MASK)),
    )

    fun save(schedule: WeeklySchedule) {
        preferences.edit {
            putBoolean(KEY_ENABLED, schedule.enabled)
            putInt(KEY_START_MINUTE, schedule.start.minuteOfDay())
            putInt(KEY_END_MINUTE, schedule.end.minuteOfDay())
            putInt(KEY_DAYS, encodeDays(schedule.days))
        }
    }

    /** Start of the last session whose start alarm or reminder was handled. */
    fun handledSessionStart(): Instant? =
        if (preferences.contains(KEY_HANDLED_SESSION_START)) {
            Instant.ofEpochMilli(preferences.getLong(KEY_HANDLED_SESSION_START, 0))
        } else {
            null
        }

    fun recordHandledSessionStart(sessionStart: Instant) {
        preferences.edit { putLong(KEY_HANDLED_SESSION_START, sessionStart.toEpochMilli()) }
    }

    private fun encodeDays(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day ->
        mask or (1 shl (day.value - 1))
    }

    private fun decodeDays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries
        .filterTo(linkedSetOf()) { day -> mask and (1 shl (day.value - 1)) != 0 }

    private fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

    companion object {
        private const val PREFERENCES_NAME = "schedule_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_DAYS = "days"
        private const val KEY_HANDLED_SESSION_START = "handled_session_start"
        private const val ALL_DAYS_MASK = 0b1111111
        private const val MIN_MINUTE = 0
        private const val MAX_MINUTE = 23 * 60 + 59
    }
}
