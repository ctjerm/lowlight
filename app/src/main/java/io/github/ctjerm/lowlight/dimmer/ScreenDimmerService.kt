package io.github.ctjerm.lowlight.dimmer

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import io.github.ctjerm.lowlight.MainActivity
import io.github.ctjerm.lowlight.R
import java.time.Instant

class ScreenDimmerService : Service() {
    private lateinit var stateStore: DimmerStateStore
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayLayout: WindowManager.LayoutParams? = null
    private val overlayInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private lateinit var appOps: AppOpsManager
    private lateinit var mainHandler: Handler

    // Android only hides the overlay when the permission is revoked, so without this the service
    // and its "dimming" notification would outlive the shade.
    private val overlayPermissionListener = AppOpsManager.OnOpChangedListener { _, _ ->
        mainHandler.post {
            if (!Settings.canDrawOverlays(this)) stopDimming(animated = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = DimmerStateStore(this)
        windowManager = getSystemService(WindowManager::class.java)
        appOps = getSystemService(AppOpsManager::class.java)
        mainHandler = Handler(mainLooper)
        appOps.startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            packageName,
            overlayPermissionListener,
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            // Only user actions send ACTION_DISABLE: the in-app switch, the notification's stop button
            // and the Quick Settings tile.
            stateStore.recordUserStop(Instant.now())
            stopDimming(animated = true)
            return START_NOT_STICKY
        }

        if (!stateStore.isEnabled() || !Settings.canDrawOverlays(this)) {
            // ENABLE arrives through startForegroundService, and stopping before startForeground
            // crashes the app, which happens when dimming is turned off before the service starts.
            if (intent?.action == ACTION_ENABLE) startAsForeground(stateStore.level())
            stateStore.deactivate()
            stopDimming(animated = false)
            return START_NOT_STICKY
        }

        val requestedLevel = intent
            ?.takeIf { it.hasExtra(EXTRA_LEVEL_PERCENT) }
            ?.getIntExtra(EXTRA_LEVEL_PERCENT, stateStore.level().percent)
            ?.let(DimmerLevel::from)
            ?: stateStore.level()

        return try {
            stateStore.setLevel(requestedLevel)
            startAsForeground(requestedLevel)
            isRunning = true
            showOrUpdateOverlay(requestedLevel)
            // A restart would come in a new process, which LowlightApplication starts with dimming off.
            START_NOT_STICKY
        } catch (error: RuntimeException) {
            Log.e(LOG_TAG, "Unable to keep the screen dimmer active", error)
            stateStore.deactivate()
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        appOps.stopWatchingMode(overlayPermissionListener)
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOrUpdateOverlay(level: DimmerLevel) {
        val existingView = overlayView
        val existingLayout = overlayLayout
        if (existingView != null && existingLayout != null) {
            animateOverlayAlpha(
                targetAlpha = level.viewAlpha(),
                durationMillis = LEVEL_CHANGE_DURATION_MS,
            )
            return
        }

        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Android 12 blocks touches through an overlay whose window alpha is above 0.8, and
            // later versions clamp it to 0.8, which weakened every level by a fifth.
            alpha = WINDOW_ALPHA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowManager.addView(view, layout)
        overlayView = view
        overlayLayout = layout
        animateOverlayAlpha(
            targetAlpha = level.viewAlpha(),
            durationMillis = FADE_IN_DURATION_MS,
        )
    }

    /** Window and view alpha multiply, so the view sets the level's share of [WINDOW_ALPHA]. */
    private fun DimmerLevel.viewAlpha(): Float = opacity / WINDOW_ALPHA

    private fun removeOverlay() {
        overlayView?.let { view ->
            view.animate().cancel()
            runCatching { windowManager.removeViewImmediate(view) }
        }
        overlayView = null
        overlayLayout = null
    }

    private fun stopDimming(animated: Boolean) {
        stateStore.deactivate()
        if (!animated || overlayView == null) {
            finishStopping()
            return
        }

        animateOverlayAlpha(
            targetAlpha = 0f,
            durationMillis = FADE_OUT_DURATION_MS,
            onEnd = ::finishStopping,
        )
    }

    private fun animateOverlayAlpha(
        targetAlpha: Float,
        durationMillis: Long,
        onEnd: (() -> Unit)? = null,
    ) {
        val view = overlayView ?: run {
            onEnd?.invoke()
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(targetAlpha)
            .setDuration(durationMillis)
            .setInterpolator(overlayInterpolator)
            .withEndAction {
                if (overlayView === view) onEnd?.invoke()
            }
            .start()
    }

    private fun finishStopping() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(level: DimmerLevel) {
        val notification = createNotification(level)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(level: DimmerLevel): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP_DIMMING,
            Intent(this, ScreenDimmerService::class.java).apply {
                action = ACTION_DISABLE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content, level.percent))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(getColor(R.color.lowlight_accent))
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop_dimming),
                    stopIntent,
                ).build(),
            )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_ENABLE = "io.github.ctjerm.lowlight.action.ENABLE"
        const val ACTION_UPDATE = "io.github.ctjerm.lowlight.action.UPDATE"
        const val ACTION_DISABLE = "io.github.ctjerm.lowlight.action.DISABLE"
        const val EXTRA_LEVEL_PERCENT = "level_percent"

        private const val NOTIFICATION_CHANNEL_ID = "active_dimming"
        private const val NOTIFICATION_ID = 4101
        private const val REQUEST_OPEN_APP = 4102
        private const val REQUEST_STOP_DIMMING = 4103
        private const val LOG_TAG = "ScreenDimmerService"
        private const val FADE_IN_DURATION_MS = 220L
        private const val FADE_OUT_DURATION_MS = 180L
        private const val LEVEL_CHANGE_DURATION_MS = 90L
        private const val WINDOW_ALPHA = DimmerLevel.MAX_PERCENT / 100f

        /** True once the service is in the foreground, so it can be stopped from outside. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
