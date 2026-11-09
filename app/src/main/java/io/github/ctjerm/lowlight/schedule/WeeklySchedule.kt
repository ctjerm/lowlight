package io.github.ctjerm.lowlight.schedule

import java.time.DayOfWeek
import java.time.LocalTime

data class WeeklySchedule(
    val enabled: Boolean = false,
    val start: LocalTime = DEFAULT_START,
    val end: LocalTime = DEFAULT_END,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
) {
    val isValid: Boolean
        get() = days.isNotEmpty() && start != end

    companion object {
        val DEFAULT_START: LocalTime = LocalTime.of(22, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(7, 0)
    }
}
