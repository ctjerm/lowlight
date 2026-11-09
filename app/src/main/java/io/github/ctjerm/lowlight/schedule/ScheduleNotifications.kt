package io.github.ctjerm.lowlight.schedule

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.ctjerm.lowlight.MainActivity
import io.github.ctjerm.lowlight.R

class ScheduleNotifications(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    fun postStartReminder() {
        if (!canPostNotifications()) return
        createChannel()

        val startIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_START_FROM_REMINDER,
            Intent(appContext, ScheduleAlarmReceiver::class.java)
                .setAction(ScheduleAlarmScheduler.ACTION_START_FROM_REMINDER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            baseNotification(
                title = appContext.getString(R.string.schedule_reminder_title),
                body = appContext.getString(R.string.schedule_reminder_body),
            )
                .addAction(
                    Notification.Action.Builder(
                        null,
                        appContext.getString(R.string.start_dimming),
                        startIntent,
                    ).build(),
                )
                .build(),
        )
    }

    fun postOverlayPermissionNeeded() {
        if (!canPostNotifications()) return
        createChannel()
        notificationManager.notify(
            NOTIFICATION_ID,
            baseNotification(
                title = appContext.getString(R.string.schedule_permission_title),
                body = appContext.getString(R.string.schedule_permission_body),
            ).build(),
        )
    }

    fun cancelStartReminder() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun baseNotification(title: String, body: String): Notification.Builder {
        val openApp = PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN_APP,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(appContext.getColor(R.color.lowlight_accent))
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.schedule_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.schedule_notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "schedule_reminders"
        private const val NOTIFICATION_ID = 5201
        private const val REQUEST_OPEN_APP = 5202
        private const val REQUEST_START_FROM_REMINDER = 5203
    }
}
