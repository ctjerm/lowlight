package io.github.ctjerm.lowlight.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalculatorTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")

    @Test
    fun sameDaySchedule_returnsUpcomingStartAndStop() {
        val schedule = schedule(
            start = LocalTime.of(21, 0),
            end = LocalTime.of(23, 0),
            days = setOf(DayOfWeek.MONDAY),
        )
        val now = Instant.parse("2026-08-31T18:00:00Z") // Monday 20:00 local.

        assertEquals(
            Instant.parse("2026-08-31T19:00:00Z"),
            ScheduleCalculator.nextStart(schedule, now, copenhagen),
        )
        assertEquals(
            Instant.parse("2026-08-31T21:00:00Z"),
            ScheduleCalculator.nextStop(schedule, now, copenhagen),
        )
        assertFalse(ScheduleCalculator.isActive(schedule, now, copenhagen))
    }

    @Test
    fun sessionStartingInTheSpringForwardGap_stillRunsUntilItsEnd() {
        val schedule = schedule(
            start = LocalTime.of(2, 45),
            end = LocalTime.of(3, 15),
            days = setOf(DayOfWeek.SUNDAY),
        )
        val sundayAtFiveAfterThree = Instant.parse("2027-03-28T01:05:00Z") // Clocks jump 02:00 -> 03:00.

        assertTrue(ScheduleCalculator.isActive(schedule, sundayAtFiveAfterThree, copenhagen))
    }

    @Test
    fun overnightSchedule_attributesMorningStopToPreviousSelectedDay() {
        val schedule = schedule(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY),
        )
        val tuesdayAtOne = Instant.parse("2026-08-31T23:00:00Z")

        assertTrue(ScheduleCalculator.isActive(schedule, tuesdayAtOne, copenhagen))
        assertEquals(
            Instant.parse("2026-09-01T05:00:00Z"),
            ScheduleCalculator.nextStop(schedule, tuesdayAtOne, copenhagen),
        )
        assertEquals(
            Instant.parse("2026-09-07T20:00:00Z"),
            ScheduleCalculator.nextStart(schedule, tuesdayAtOne, copenhagen),
        )
    }

    @Test
    fun weekdaySchedule_wrapsFromFridayNightToMonday() {
        val schedule = schedule(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        val saturdayAtEight = Instant.parse("2026-09-05T06:00:00Z")

        assertFalse(ScheduleCalculator.isActive(schedule, saturdayAtEight, copenhagen))
        assertEquals(
            Instant.parse("2026-09-07T20:00:00Z"),
            ScheduleCalculator.nextStart(schedule, saturdayAtEight, copenhagen),
        )
    }

    @Test
    fun daylightSavingGap_startsANonexistentStartAtTheTransition() {
        val schedule = schedule(
            start = LocalTime.of(2, 30),
            end = LocalTime.of(4, 0),
            days = setOf(DayOfWeek.SUNDAY),
        )
        val beforeTransition = Instant.parse("2026-03-28T23:00:00Z")

        assertEquals(
            Instant.parse("2026-03-29T01:00:00Z"),
            ScheduleCalculator.nextStart(schedule, beforeTransition, copenhagen),
        )
        assertEquals(
            Instant.parse("2026-03-29T02:00:00Z"),
            ScheduleCalculator.nextStop(schedule, beforeTransition, copenhagen),
        )
    }

    @Test
    fun daylightSavingOverlap_usesTheEarlierOffsetForAmbiguousStart() {
        val schedule = schedule(
            start = LocalTime.of(2, 30),
            end = LocalTime.of(3, 30),
            days = setOf(DayOfWeek.SUNDAY),
        )
        val beforeTransition = Instant.parse("2026-10-24T22:00:00Z")

        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            ScheduleCalculator.nextStart(schedule, beforeTransition, copenhagen),
        )
        assertEquals(
            Instant.parse("2026-10-25T02:30:00Z"),
            ScheduleCalculator.nextStop(schedule, beforeTransition, copenhagen),
        )
        assertTrue(
            ScheduleCalculator.isActive(
                schedule,
                Instant.parse("2026-10-25T01:30:00Z"),
                copenhagen,
            ),
        )
    }

    @Test
    fun userStopDuringSession_blocksAutoStartUntilNextSession() {
        val schedule = schedule(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY),
        )
        val stoppedMondayAtEleven = Instant.parse("2026-08-31T21:00:00Z")
        val tuesdayAtOne = Instant.parse("2026-08-31T23:00:00Z")
        val nextMondayAtHalfPastTen = Instant.parse("2026-09-07T20:30:00Z")

        assertFalse(
            ScheduleCalculator.canAutoStart(
                schedule,
                userStoppedAt = stoppedMondayAtEleven,
                at = tuesdayAtOne,
                zoneId = copenhagen,
            ),
        )
        assertTrue(
            ScheduleCalculator.canAutoStart(
                schedule,
                userStoppedAt = stoppedMondayAtEleven,
                at = nextMondayAtHalfPastTen,
                zoneId = copenhagen,
            ),
        )
    }

    @Test
    fun autoStart_requiresAnActiveSession() {
        val schedule = schedule(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY),
        )

        assertTrue(
            ScheduleCalculator.canAutoStart(
                schedule,
                userStoppedAt = null,
                at = Instant.parse("2026-08-31T23:00:00Z"),
                zoneId = copenhagen,
            ),
        )
        assertFalse(
            ScheduleCalculator.canAutoStart(
                schedule,
                userStoppedAt = null,
                at = Instant.parse("2026-08-31T18:00:00Z"),
                zoneId = copenhagen,
            ),
        )
    }

    @Test
    fun invalidEqualTimes_areRejected() {
        val schedule = schedule(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(22, 0),
            days = setOf(DayOfWeek.MONDAY),
        )

        assertFalse(schedule.isValid)
    }

    private fun schedule(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
    ) = WeeklySchedule(
        enabled = true,
        start = start,
        end = end,
        days = days,
    )
}
