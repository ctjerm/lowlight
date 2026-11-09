package io.github.ctjerm.lowlight.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ScheduleCalculator {
    fun nextStart(
        schedule: WeeklySchedule,
        after: Instant,
        zoneId: ZoneId,
    ): Instant? = sessionsAround(schedule, after, zoneId)
        .map(Session::start)
        .filter { it.isAfter(after) }
        .minOrNull()

    fun nextStop(
        schedule: WeeklySchedule,
        after: Instant,
        zoneId: ZoneId,
    ): Instant? = sessionsAround(schedule, after, zoneId)
        .map(Session::end)
        .filter { it.isAfter(after) }
        .minOrNull()

    fun isActive(
        schedule: WeeklySchedule,
        at: Instant,
        zoneId: ZoneId,
    ): Boolean = activeSessionStart(schedule, at, zoneId) != null

    /** A user stop during a session keeps the schedule from restarting dimming until the next session. */
    fun canAutoStart(
        schedule: WeeklySchedule,
        userStoppedAt: Instant?,
        at: Instant,
        zoneId: ZoneId,
    ): Boolean {
        val sessionStart = activeSessionStart(schedule, at, zoneId) ?: return false
        return userStoppedAt == null || userStoppedAt.isBefore(sessionStart)
    }

    fun activeSessionStart(
        schedule: WeeklySchedule,
        at: Instant,
        zoneId: ZoneId,
    ): Instant? {
        if (!schedule.enabled || !schedule.isValid) return null

        val localDate = at.atZone(zoneId).toLocalDate()
        return listOf(localDate.minusDays(1), localDate)
            .asSequence()
            .filter { it.dayOfWeek in schedule.days }
            .map { sessionOn(schedule, it, zoneId) }
            .firstOrNull { session -> !at.isBefore(session.start) && at.isBefore(session.end) }
            ?.start
    }

    private fun sessionsAround(
        schedule: WeeklySchedule,
        instant: Instant,
        zoneId: ZoneId,
    ): Sequence<Session> {
        if (!schedule.enabled || !schedule.isValid) return emptySequence()

        val firstDate = instant.atZone(zoneId).toLocalDate().minusDays(1)
        return generateSequence(firstDate) { it.plusDays(1) }
            .take(10)
            .filter { it.dayOfWeek in schedule.days }
            .map { sessionOn(schedule, it, zoneId) }
    }

    private fun sessionOn(
        schedule: WeeklySchedule,
        startDate: LocalDate,
        zoneId: ZoneId,
    ): Session {
        val start = startDate.atTime(schedule.start).toInstant(zoneId)
        val stopDate = if (schedule.end > schedule.start) startDate else startDate.plusDays(1)
        val end = stopDate.atTime(schedule.end).toInstant(zoneId)
        return Session(start = start, end = end)
    }

    /**
     * A time the clocks skip happens at the jump. [LocalDateTime.atZone] moves it forward by the
     * jump's length instead, which put a session starting at 02:45 after its 03:15 end.
     */
    private fun LocalDateTime.toInstant(zoneId: ZoneId): Instant {
        val transition = zoneId.rules.getTransition(this)
        return if (transition?.isGap == true) transition.instant else atZone(zoneId).toInstant()
    }

    private data class Session(
        val start: Instant,
        val end: Instant,
    )
}
