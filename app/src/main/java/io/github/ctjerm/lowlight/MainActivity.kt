package io.github.ctjerm.lowlight

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import io.github.ctjerm.lowlight.dimmer.ActivationSource
import io.github.ctjerm.lowlight.dimmer.DimmerCommands
import io.github.ctjerm.lowlight.dimmer.DimmerLevel
import io.github.ctjerm.lowlight.dimmer.DimmerStateStore
import io.github.ctjerm.lowlight.schedule.ScheduleAlarmScheduler
import io.github.ctjerm.lowlight.schedule.ScheduleCoordinator
import io.github.ctjerm.lowlight.schedule.ScheduleNotifications
import io.github.ctjerm.lowlight.schedule.ScheduleStateStore
import io.github.ctjerm.lowlight.ui.LowlightScreen
import io.github.ctjerm.lowlight.ui.LowlightUiState
import io.github.ctjerm.lowlight.ui.ScheduleUiStatus
import io.github.ctjerm.lowlight.ui.theme.LowlightTheme
import java.time.DayOfWeek
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private lateinit var stateStore: DimmerStateStore
    private lateinit var scheduleStore: ScheduleStateStore
    private lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler
    private lateinit var scheduleCoordinator: ScheduleCoordinator
    private val uiState = mutableStateOf(LowlightUiState())
    private var pendingAction = PendingAction.NONE
    private var returningFromExactAlarmSettings = false
    private var stateObservation: AutoCloseable? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continuePendingAction() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stateStore = DimmerStateStore(this)
        scheduleStore = ScheduleStateStore(this)
        scheduleAlarmScheduler = ScheduleAlarmScheduler(this)
        scheduleCoordinator = ScheduleCoordinator(this)
        pendingAction = savedInstanceState
            ?.getString(KEY_PENDING_ACTION)
            ?.let { stored -> runCatching { PendingAction.valueOf(stored) }.getOrNull() }
            ?: PendingAction.NONE
        returningFromExactAlarmSettings =
            savedInstanceState?.getBoolean(KEY_RETURNING_FROM_EXACT_ALARM_SETTINGS) ?: false

        setContent {
            LowlightTheme {
                LowlightScreen(
                    state = uiState.value,
                    onEnabledChange = ::onEnabledChange,
                    onStrengthChange = ::onStrengthChange,
                    onOverlayPermissionClick = ::openOverlayPermission,
                    onNotificationSettingsClick = ::openNotificationSettings,
                    onScheduleEnabledChange = ::onScheduleEnabledChange,
                    onScheduleStartClick = { showTimePicker(selectingStart = true) },
                    onScheduleEndClick = { showTimePicker(selectingStart = false) },
                    onScheduleDayToggle = ::onScheduleDayToggle,
                    onExactAlarmPermissionClick = ::openExactAlarmPermission,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The Quick Settings tile and the notification change dimming while this screen is open.
        stateObservation = stateStore.observeChanges(::refreshUiState)
    }

    override fun onStop() {
        stateObservation?.close()
        stateObservation = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val hasOverlayPermission = Settings.canDrawOverlays(this)

        if (!hasOverlayPermission && stateStore.isEnabled()) {
            DimmerCommands.disable(this)
        }
        if (!hasOverlayPermission) {
            // Returning without the permission abandons the request, so a later grant made
            // outside Lowlight can't start dimming unexpectedly.
            pendingAction = PendingAction.NONE
        }

        scheduleCoordinator.reconcile(
            activateCurrentSession = returningFromExactAlarmSettings,
        )
        returningFromExactAlarmSettings = false

        if (pendingAction != PendingAction.NONE && hasOverlayPermission) {
            requestNotificationThenContinue()
        }

        refreshUiState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PENDING_ACTION, pendingAction.name)
        outState.putBoolean(
            KEY_RETURNING_FROM_EXACT_ALARM_SETTINGS,
            returningFromExactAlarmSettings,
        )
        super.onSaveInstanceState(outState)
    }

    private fun onEnabledChange(enabled: Boolean) {
        if (!enabled) {
            pendingAction = PendingAction.NONE
            DimmerCommands.disableWithFade(this)
            refreshUiState()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            pendingAction = PendingAction.ENABLE_DIMMER
            openOverlayPermission()
            return
        }

        pendingAction = PendingAction.ENABLE_DIMMER
        requestNotificationThenContinue()
    }

    private fun requestNotificationThenContinue() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continuePendingAction()
        }
    }

    private fun continuePendingAction() {
        val action = pendingAction
        pendingAction = PendingAction.NONE
        when (action) {
            PendingAction.ENABLE_DIMMER -> enableDimmer()
            PendingAction.ENABLE_SCHEDULE -> enableSchedule()
            PendingAction.NONE -> Unit
        }
    }

    private fun enableDimmer() {
        if (!Settings.canDrawOverlays(this)) {
            pendingAction = PendingAction.ENABLE_DIMMER
            refreshUiState()
            return
        }

        DimmerCommands.enable(this, stateStore.level())
        refreshUiState()
    }

    private fun onScheduleEnabledChange(enabled: Boolean) {
        if (!enabled) {
            pendingAction = PendingAction.NONE
            val schedule = scheduleStore.schedule()
            scheduleStore.save(schedule.copy(enabled = false))
            scheduleAlarmScheduler.cancelAll()
            ScheduleNotifications(this).cancelStartReminder()
            if (
                stateStore.isEnabled() &&
                stateStore.activationSource() == ActivationSource.SCHEDULE
            ) {
                DimmerCommands.disableWithFade(this)
            }
            refreshUiState()
            return
        }

        pendingAction = PendingAction.ENABLE_SCHEDULE
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermission()
        } else {
            requestNotificationThenContinue()
        }
    }

    private fun enableSchedule() {
        val schedule = scheduleStore.schedule().copy(enabled = true)
        scheduleCoordinator.save(schedule)
        refreshUiState()

        if (schedule.isValid && !scheduleAlarmScheduler.hasExactAlarmAccess()) {
            openExactAlarmPermission()
        }
    }

    private fun onScheduleDayToggle(day: DayOfWeek) {
        val schedule = scheduleStore.schedule()
        val updatedDays = if (day in schedule.days) schedule.days - day else schedule.days + day
        scheduleCoordinator.save(schedule.copy(days = updatedDays))
        refreshUiState()
    }

    private fun showTimePicker(selectingStart: Boolean) {
        val schedule = scheduleStore.schedule()
        val initial = if (selectingStart) schedule.start else schedule.end
        TimePickerDialog(
            this,
            { _, hour, minute ->
                updateScheduleTime(selectingStart, LocalTime.of(hour, minute))
            },
            initial.hour,
            initial.minute,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun updateScheduleTime(selectingStart: Boolean, time: LocalTime) {
        val schedule = scheduleStore.schedule()
        scheduleCoordinator.save(
            if (selectingStart) schedule.copy(start = time) else schedule.copy(end = time),
        )
        refreshUiState()
    }

    private fun onStrengthChange(percent: Int) {
        val level = DimmerLevel.from(percent)
        if (uiState.value.strengthPercent == level.percent) return

        if (stateStore.isEnabled()) {
            DimmerCommands.update(this, level)
        } else {
            stateStore.setLevel(level)
        }
        uiState.value = uiState.value.copy(strengthPercent = level.percent)
    }

    private fun refreshUiState() {
        val schedule = scheduleStore.schedule()
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        val hasNotificationPermission = hasNotificationPermission()
        val hasExactAlarmAccess = scheduleAlarmScheduler.hasExactAlarmAccess()
        uiState.value = LowlightUiState(
            hasOverlayPermission = hasOverlayPermission,
            hasNotificationPermission = hasNotificationPermission,
            enabled = stateStore.isEnabled(),
            strengthPercent = stateStore.level().percent,
            scheduleEnabled = schedule.enabled,
            scheduleStart = schedule.start,
            scheduleEnd = schedule.end,
            scheduleDays = schedule.days,
            scheduleStatus = when {
                !schedule.enabled -> ScheduleUiStatus.OFF
                !schedule.isValid -> ScheduleUiStatus.INVALID
                !hasOverlayPermission -> ScheduleUiStatus.OVERLAY_PERMISSION_NEEDED
                hasExactAlarmAccess -> ScheduleUiStatus.AUTOMATIC
                !hasNotificationPermission -> ScheduleUiStatus.NOTIFICATIONS_BLOCKED
                else -> ScheduleUiStatus.REMINDER_ONLY
            },
        )
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openOverlayPermission() {
        if (pendingAction == PendingAction.NONE) {
            pendingAction = PendingAction.ENABLE_DIMMER
        }
        val appSpecificIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )
        try {
            startActivity(appSpecificIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            },
        )
    }

    private fun openExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        returningFromExactAlarmSettings = true
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:$packageName".toUri(),
        )
        try {
            startActivity(requestIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
            )
        }
    }

    companion object {
        private const val KEY_PENDING_ACTION = "pending_action"
        private const val KEY_RETURNING_FROM_EXACT_ALARM_SETTINGS =
            "returning_from_exact_alarm_settings"
    }

    private enum class PendingAction {
        NONE,
        ENABLE_DIMMER,
        ENABLE_SCHEDULE,
    }
}
