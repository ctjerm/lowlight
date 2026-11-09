# Development

This page covers how Lowlight works, how to build and sign it, and how to test it. For F-Droid releases, see [f-droid.md](f-droid.md).

## How it works

`MainActivity` owns permission requests and the manual controls. `ScreenDimmerService` owns one `TYPE_APPLICATION_OVERLAY` window and the required foreground-service notification. `DimmerStateStore` keeps the current dimmer state and records whether a manual action or the schedule owns the active shade. `DimmerTileService` adds a Quick Settings tile that turns dimming on and off like the in-app switch and opens Lowlight when display-over-other-apps access is missing. The shade lives in the app's process, so when Android ends that process (Task Manager's Stop, force stop, a reboot), manually started dimming ends with it and the next launch starts with dimming off.

The focused `schedule` package stores the weekly schedule, calculates local-time sessions, registers the next start and stop with `AlarmManager`, handles alarm events, and rebuilds them after reboot, time or timezone changes, app updates, and exact-alarm access changes. Only the next start and stop are registered. Each one has an inexact backup alarm, because revoking exact-alarm access deletes every exact alarm without notifying the app; the backup then delivers the reminder fallback. Overnight sessions belong to the selected evening. A start or stop time that the clocks skip when daylight saving begins happens at the jump, and an ambiguous time when it ends uses the earlier offset.

Automatic starts use the user-granted `SCHEDULE_EXACT_ALARM` special access. Lowlight intentionally does not request the restricted `USE_EXACT_ALARM` permission. Without exact-alarm access, Android uses inexact alarms that power management may delay; the user must tap the reminder's Start dimming action. Lowlight ignores a delayed start or stale reminder after that session has ended. The paired stop can also arrive late in this fallback mode. A scheduled stop affects only a shade started by the schedule and never turns off manually started dimming. Likewise, when the user stops dimming during a session, the schedule leaves it off until the next session, even across schedule edits, reboots, app updates, and time changes.

The overlay stops at 75%. Android blocks touch forwarding when a single application overlay exceeds the platform's 0.8 maximum obscuring opacity. Critical system windows such as the status bar, keyboard, and permission screens sit above application overlays and may stay brighter.

Android may also show its own silent "displaying over other apps" notice while the shade is active. Lowlight cannot remove that system-managed safety notice. Its separate foreground-service notification provides the dim level and the required stop action.

## Build

Requirements:

- JDK 17 or newer
- Android SDK 36

The AndroidX UI versions are pinned to the newest line verified with compile SDK 36. Lint reports newer Compose 1.12 and Core 1.19 releases, but those require compile SDK 37 and Android Gradle Plugin 9.1. Move that whole platform boundary together when API 37 becomes the release target.

From the project root:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Release builds are unsigned unless you configure a private signing key.

For a signed release, set these environment variables before running `assembleRelease` or `bundleRelease`:

```powershell
$env:LOWLIGHT_UPLOAD_KEYSTORE = '<path-to-upload-keystore.jks>'
$env:LOWLIGHT_UPLOAD_STORE_PASSWORD = '<store password>'
$env:LOWLIGHT_UPLOAD_KEY_ALIAS = 'lowlight-upload'
$env:LOWLIGHT_UPLOAD_KEY_PASSWORD = '<key password>'
.\gradlew.bat assembleRelease
```

Keep the keystore and passwords outside the project and back them up securely.

## Verify on an emulator

Instrumentation tests run on a connected device or emulator. Set `ANDROID_SERIAL` first if more than one is connected, because Gradle installs on every connected device.

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
adb shell am start -n io.github.ctjerm.lowlight/.MainActivity
```

Walk through these states:

1. The first screen explains why overlay access is required.
2. "Allow screen dimming" opens Android's display-over-other-apps list. Choose Lowlight, then enable the access.
3. Returning to Lowlight asks for notification permission on Android 13 or newer.
4. The shade starts and touch input still reaches every control.
5. The slider changes the shade immediately.
6. The notification's "Stop dimming" action removes the shade and service.
7. Add the Lowlight tile to Quick Settings and confirm it turns dimming on and off and follows changes made in the app.
8. Enable Schedule, select start and end times, and choose the start days.
9. Grant "Alarms & reminders" to reach the `Automatic` state, then verify the shade starts and stops with Lowlight in the background.
10. Revoke that access and verify the UI changes to `Reminder` and the start notification exposes a working `Start dimming` action.
11. Reboot during an active scheduled session and confirm Lowlight restores the schedule and shade after Android finishes booting.

## Platform references

- [Android overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW)
- [Application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Special-use foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [Android exact alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Quick Settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles)
