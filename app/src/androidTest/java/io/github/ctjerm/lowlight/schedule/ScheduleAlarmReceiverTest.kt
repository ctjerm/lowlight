package io.github.ctjerm.lowlight.schedule

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ctjerm.lowlight.dimmer.ActivationSource
import io.github.ctjerm.lowlight.dimmer.DimmerCommands
import io.github.ctjerm.lowlight.dimmer.DimmerStateStore
import io.github.ctjerm.lowlight.dimmer.ScreenDimmerService
import io.github.ctjerm.lowlight.R
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleAlarmReceiverTest {
    @Test
    fun inexactStart_postsReminderWhoseActionStartsScheduleOwnedDimming() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = ScheduleAlarmScheduler(context)
        assumeFalse(scheduler.hasExactAlarmAccess())
        assumeTrue(Settings.canDrawOverlays(context))
        assumeTrue(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )

        val scheduleStore = ScheduleStateStore(context)
        val originalSchedule = scheduleStore.schedule()
        val dimmerStore = DimmerStateStore(context)

        try {
            DimmerCommands.disable(context)
            dimmerStore.recordUserStop(Instant.EPOCH)
            scheduleStore.save(activeSchedule())

            ScheduleAlarmReceiver().onReceive(
                context,
                Intent().setAction(ScheduleAlarmScheduler.ACTION_START),
            )

            val reminder = activeReminder(context)
            assertNotNull(reminder)
            val startAction = checkNotNull(reminder).notification.actions.single()
            assertEquals(context.getString(R.string.start_dimming), startAction.title.toString())

            startAction.actionIntent.send()
            waitFor { dimmerStore.isEnabled() }

            assertTrue(dimmerStore.isEnabled())
            assertEquals(ActivationSource.SCHEDULE, dimmerStore.activationSource())
        } finally {
            DimmerCommands.disable(context)
            ScheduleNotifications(context).cancelStartReminder()
            scheduleStore.save(originalSchedule)
            scheduler.reschedule()
        }
    }

    @Test
    fun staleStartAndReminderAction_doNotStartDimmingOutsideScheduledWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = ScheduleAlarmScheduler(context)
        assumeFalse(scheduler.hasExactAlarmAccess())

        val scheduleStore = ScheduleStateStore(context)
        val originalSchedule = scheduleStore.schedule()
        val dimmerStore = DimmerStateStore(context)
        val futureStart = ZonedDateTime.now().plusHours(2)
        val futureEnd = futureStart.plusHours(1)

        try {
            DimmerCommands.disable(context)
            ScheduleNotifications(context).cancelStartReminder()
            scheduleStore.save(
                WeeklySchedule(
                    enabled = true,
                    start = futureStart.toLocalTime(),
                    end = futureEnd.toLocalTime(),
                    days = setOf(futureStart.dayOfWeek),
                ),
            )

            ScheduleAlarmReceiver().onReceive(
                context,
                Intent().setAction(ScheduleAlarmScheduler.ACTION_START),
            )
            ScheduleAlarmReceiver().onReceive(
                context,
                Intent().setAction(ScheduleAlarmScheduler.ACTION_START_FROM_REMINDER),
            )

            val reminder = activeReminder(context)
            assertNull(reminder)
            assertFalse(dimmerStore.isEnabled())
        } finally {
            DimmerCommands.disable(context)
            ScheduleNotifications(context).cancelStartReminder()
            scheduleStore.save(originalSchedule)
            scheduler.reschedule()
        }
    }

    @Test
    fun userStopDuringActiveSession_staysStoppedAfterScheduleReconcile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = ScheduleAlarmScheduler(context)
        assumeTrue(scheduler.hasExactAlarmAccess())
        assumeTrue(Settings.canDrawOverlays(context))

        val scheduleStore = ScheduleStateStore(context)
        val originalSchedule = scheduleStore.schedule()
        val dimmerStore = DimmerStateStore(context)

        try {
            scheduleStore.save(activeSchedule())
            DimmerCommands.enable(context, dimmerStore.level(), ActivationSource.SCHEDULE)
            waitFor { ScreenDimmerService.isRunning }
            assertTrue(ScreenDimmerService.isRunning)

            DimmerCommands.disableWithFade(context)
            waitFor { !ScreenDimmerService.isRunning }

            // MainActivity makes this call after a schedule edit; boot and time changes do too.
            ScheduleCoordinator(context).reconcile(activateCurrentSession = true)
            Thread.sleep(10_000)

            assertFalse(dimmerStore.isEnabled())
        } finally {
            DimmerCommands.disable(context)
            dimmerStore.recordUserStop(Instant.EPOCH)
            scheduleStore.save(originalSchedule)
            scheduler.reschedule()
        }
    }

    @Test
    fun openingTheAppBeforeADelayedStart_stillRemindsOnceForThatSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = ScheduleAlarmScheduler(context)
        assumeFalse(scheduler.hasExactAlarmAccess())
        assumeTrue(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )

        val scheduleStore = ScheduleStateStore(context)
        val originalSchedule = scheduleStore.schedule()
        val dimmerStore = DimmerStateStore(context)

        try {
            DimmerCommands.disable(context)
            ScheduleNotifications(context).cancelStartReminder()
            dimmerStore.recordUserStop(Instant.EPOCH)
            scheduleStore.recordHandledSessionStart(Instant.EPOCH)
            // Its inexact start alarm hasn't arrived yet, as when power management delays it.
            scheduleStore.save(activeSchedule())

            // MainActivity.onResume makes this call.
            ScheduleCoordinator(context).reconcile(activateCurrentSession = false)
            assertNotNull(activeReminder(context))

            ScheduleNotifications(context).cancelStartReminder()
            ScheduleCoordinator(context).reconcile(activateCurrentSession = false)
            assertNull(activeReminder(context))
        } finally {
            ScheduleNotifications(context).cancelStartReminder()
            scheduleStore.save(originalSchedule)
            scheduler.reschedule()
        }
    }

    @Test
    fun movingTheStartAfterAUserStop_keepsDimmingStopped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(Settings.canDrawOverlays(context))

        val scheduleStore = ScheduleStateStore(context)
        val originalSchedule = scheduleStore.schedule()
        val dimmerStore = DimmerStateStore(context)
        val now = ZonedDateTime.now()

        try {
            DimmerCommands.disable(context)
            ScheduleNotifications(context).cancelStartReminder()
            scheduleStore.save(activeSchedule().copy(start = now.minusMinutes(3).toLocalTime()))
            dimmerStore.recordUserStop(now.minusMinutes(2).toInstant())

            ScheduleCoordinator(context).save(
                scheduleStore.schedule().copy(start = now.minusMinutes(1).toLocalTime()),
            )
            Thread.sleep(10_000)

            assertFalse(dimmerStore.isEnabled())
            assertNull(activeReminder(context))
        } finally {
            DimmerCommands.disable(context)
            ScheduleNotifications(context).cancelStartReminder()
            dimmerStore.recordUserStop(Instant.EPOCH)
            scheduleStore.save(originalSchedule)
            ScheduleAlarmScheduler(context).reschedule()
        }
    }

    private fun activeReminder(context: Context): StatusBarNotification? = context
        .getSystemService(NotificationManager::class.java)
        .activeNotifications
        .firstOrNull { notification ->
            notification.notification.extras.getString(Notification.EXTRA_TITLE) ==
                context.getString(R.string.schedule_reminder_title)
        }

    private fun activeSchedule(): WeeklySchedule {
        val now = ZonedDateTime.now()
        val start = now.minusMinutes(1)
        val end = now.plusMinutes(5)
        return WeeklySchedule(
            enabled = true,
            start = start.toLocalTime(),
            end = end.toLocalTime(),
            days = setOf(start.dayOfWeek),
        )
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(20) {
            if (condition()) return
            Thread.sleep(100)
        }
    }
}
