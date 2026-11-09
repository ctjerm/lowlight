package io.github.ctjerm.lowlight.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.ctjerm.lowlight.dimmer.ActivationSource
import io.github.ctjerm.lowlight.dimmer.DimmerCommands
import io.github.ctjerm.lowlight.dimmer.DimmerStateStore
import java.time.Instant
import java.time.ZoneId

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val scheduler = ScheduleAlarmScheduler(appContext)
        val scheduleStore = ScheduleStateStore(appContext)
        val schedule = scheduleStore.schedule()

        if (!schedule.enabled || !schedule.isValid) {
            scheduler.cancelAll()
            ScheduleNotifications(appContext).cancelStartReminder()
            return
        }

        when (intent.action) {
            ScheduleAlarmScheduler.ACTION_START -> {
                val now = Instant.now()
                val zoneId = ZoneId.systemDefault()
                ScheduleCalculator.activeSessionStart(schedule, now, zoneId)
                    ?.let(scheduleStore::recordHandledSessionStart)
                val canAutoStart = ScheduleCalculator.canAutoStart(
                    schedule = schedule,
                    userStoppedAt = DimmerStateStore(appContext).userStoppedAt(),
                    at = now,
                    zoneId = zoneId,
                )
                if (!canAutoStart) {
                    ScheduleNotifications(appContext).cancelStartReminder()
                } else if (scheduler.hasExactAlarmAccess()) {
                    startScheduledDimming(appContext)
                } else {
                    ScheduleNotifications(appContext).postStartReminder()
                }
            }

            ScheduleAlarmScheduler.ACTION_START_FROM_REMINDER -> {
                ScheduleNotifications(appContext).cancelStartReminder()
                if (ScheduleCalculator.isActive(schedule, Instant.now(), ZoneId.systemDefault())) {
                    startScheduledDimming(appContext)
                }
            }

            ScheduleAlarmScheduler.ACTION_STOP -> {
                val dimmerState = DimmerStateStore(appContext)
                if (
                    dimmerState.isEnabled() &&
                    dimmerState.activationSource() == ActivationSource.SCHEDULE
                ) {
                    DimmerCommands.disable(appContext)
                }
                ScheduleNotifications(appContext).cancelStartReminder()
            }
        }

        scheduler.reschedule()
    }

    private fun startScheduledDimming(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            ScheduleNotifications(context).postOverlayPermissionNeeded()
            return
        }

        val dimmerState = DimmerStateStore(context)
        if (!dimmerState.isEnabled()) {
            DimmerCommands.enable(
                context = context,
                level = dimmerState.level(),
                source = ActivationSource.SCHEDULE,
            )
        }
    }
}
