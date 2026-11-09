package io.github.ctjerm.lowlight.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId

class ScheduleAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun hasExactAlarmAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun reschedule(
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        synchronizeActiveSession: Boolean = false,
    ) {
        cancelAll()
        val schedule = ScheduleStateStore(appContext).schedule()
        if (!schedule.enabled || !schedule.isValid) {
            ScheduleNotifications(appContext).cancelStartReminder()
            return
        }

        val exact = hasExactAlarmAccess()
        val start = if (
            exact &&
            synchronizeActiveSession &&
            ScheduleCalculator.isActive(schedule, now, zoneId)
        ) {
            now.plusSeconds(1)
        } else {
            ScheduleCalculator.nextStart(schedule, now, zoneId)
        }
        val stop = ScheduleCalculator.nextStop(schedule, now, zoneId)

        start?.let { scheduleAlarm(it, AlarmKind.START, exact) }
        stop?.let { scheduleAlarm(it, AlarmKind.STOP, exact) }
    }

    fun cancelAll() {
        AlarmKind.entries.forEach { kind ->
            alarmManager.cancel(alarmPendingIntent(kind.action, kind.exactRequestCode))
            alarmManager.cancel(alarmPendingIntent(kind.action, kind.inexactRequestCode))
        }
    }

    private fun scheduleAlarm(at: Instant, kind: AlarmKind, exact: Boolean) {
        // Revoking exact-alarm access deletes every exact alarm and sends no broadcast, so an
        // inexact alarm always backs up the exact one. Delivering both is harmless: the receiver
        // reschedules on the first, which cancels the second.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilli(),
            alarmPendingIntent(kind.action, kind.inexactRequestCode),
        )
        if (!exact) return

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.toEpochMilli(),
                alarmPendingIntent(kind.action, kind.exactRequestCode),
            )
        } catch (_: SecurityException) {
            // Exact access can be revoked between the capability check and this call.
        }
    }

    private fun alarmPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, ScheduleAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private enum class AlarmKind(
        val action: String,
        val exactRequestCode: Int,
        val inexactRequestCode: Int,
    ) {
        START(ACTION_START, exactRequestCode = 5101, inexactRequestCode = 5103),
        STOP(ACTION_STOP, exactRequestCode = 5102, inexactRequestCode = 5104),
    }

    companion object {
        const val ACTION_START = "io.github.ctjerm.lowlight.schedule.START"
        const val ACTION_STOP = "io.github.ctjerm.lowlight.schedule.STOP"
        const val ACTION_START_FROM_REMINDER = "io.github.ctjerm.lowlight.schedule.START_FROM_REMINDER"
    }
}
