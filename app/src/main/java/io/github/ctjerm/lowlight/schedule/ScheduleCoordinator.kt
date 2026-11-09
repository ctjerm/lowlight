package io.github.ctjerm.lowlight.schedule

import android.content.Context
import io.github.ctjerm.lowlight.dimmer.ActivationSource
import io.github.ctjerm.lowlight.dimmer.DimmerCommands
import io.github.ctjerm.lowlight.dimmer.DimmerStateStore
import io.github.ctjerm.lowlight.dimmer.ScreenDimmerService
import java.time.Instant
import java.time.ZoneId

class ScheduleCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val alarmScheduler = ScheduleAlarmScheduler(appContext)

    /** Saves a schedule the user edited and applies it to the current session. */
    fun save(schedule: WeeklySchedule) {
        val scheduleStore = ScheduleStateStore(appContext)
        val dimmerState = DimmerStateStore(appContext)
        val previous = scheduleStore.schedule()
        val now = Instant.now()
        val zoneId = ZoneId.systemDefault()
        val userStoppedThisSession = ScheduleCalculator.isActive(previous, now, zoneId) &&
            !ScheduleCalculator.canAutoStart(previous, dimmerState.userStoppedAt(), now, zoneId)
        // A stop holds only for a session that started before it, so moving the start past the stop
        // would undo it. Dating the stop to the edit keeps it for the edited session.
        if (userStoppedThisSession) dimmerState.recordUserStop(now)

        scheduleStore.save(schedule)
        reconcile(now, zoneId, activateCurrentSession = true)
    }

    fun reconcile(
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        activateCurrentSession: Boolean,
    ) {
        val scheduleStore = ScheduleStateStore(appContext)
        val schedule = scheduleStore.schedule()
        val sessionStart = ScheduleCalculator.activeSessionStart(schedule, now, zoneId)
        val sessionIsActive = sessionStart != null
        val dimmerState = DimmerStateStore(appContext)
        val canAutoStart = ScheduleCalculator.canAutoStart(
            schedule = schedule,
            userStoppedAt = dimmerState.userStoppedAt(),
            at = now,
            zoneId = zoneId,
        )
        // Rescheduling cancels a start alarm that is due but delayed, as inexact alarms can be, so a
        // session whose start hasn't been handled is started from here. One already handled is left
        // alone, so dimming stopped since then stays stopped.
        val startIsOwed = sessionStart != null && sessionStart != scheduleStore.handledSessionStart()
        val activate = (activateCurrentSession || startIsOwed) && canAutoStart
        val hasExactAlarmAccess = alarmScheduler.hasExactAlarmAccess()
        val notifications = ScheduleNotifications(appContext)

        if (
            dimmerState.isEnabled() &&
            dimmerState.activationSource() == ActivationSource.SCHEDULE &&
            !sessionIsActive
        ) {
            DimmerCommands.disable(appContext)
        }

        if (activate && !hasExactAlarmAccess && !ScreenDimmerService.isRunning) {
            notifications.postStartReminder()
            scheduleStore.recordHandledSessionStart(checkNotNull(sessionStart))
        } else if (!canAutoStart || hasExactAlarmAccess) {
            notifications.cancelStartReminder()
        }

        alarmScheduler.reschedule(
            now = now,
            zoneId = zoneId,
            synchronizeActiveSession = activate,
        )
    }
}
