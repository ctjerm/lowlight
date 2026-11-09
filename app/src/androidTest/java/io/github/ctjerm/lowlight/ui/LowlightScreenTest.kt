package io.github.ctjerm.lowlight.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ctjerm.lowlight.ui.theme.LowlightTheme
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LowlightScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingOverlayPermission_explainsRecoveryAndDisablesSwitch() {
        composeRule.setContent {
            LowlightTheme {
                LowlightScreen(
                    state = LowlightUiState(hasOverlayPermission = false),
                    onEnabledChange = {},
                    onStrengthChange = {},
                    onOverlayPermissionClick = {},
                    onNotificationSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Permission needed").assertIsDisplayed()
        composeRule.onNodeWithTag("overlay_permission_button").assertIsEnabled()
        composeRule.onNodeWithTag("dimming_switch").assertIsNotEnabled()
    }

    @Test
    fun activeDimmer_showsDurableEnabledState() {
        composeRule.setContent {
            LowlightTheme {
                LowlightScreen(
                    state = LowlightUiState(
                        hasOverlayPermission = true,
                        enabled = true,
                        strengthPercent = 60,
                    ),
                    onEnabledChange = {},
                    onStrengthChange = {},
                    onOverlayPermissionClick = {},
                    onNotificationSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Screen dimming").assertIsDisplayed()
        composeRule.onNodeWithText("On").assertIsDisplayed()
        composeRule.onNodeWithText("60%").assertIsDisplayed()
        composeRule.onNodeWithTag("dimming_switch").assertIsEnabled()
        composeRule.onAllNodesWithText("Touches stay available underneath.").assertCountEquals(0)
    }

    @Test
    fun automaticSchedule_showsTimesAndSelectedStartDays() {
        composeRule.setContent {
            LowlightTheme {
                LowlightScreen(
                    state = LowlightUiState(
                        hasOverlayPermission = true,
                        scheduleEnabled = true,
                        scheduleStart = LocalTime.of(22, 0),
                        scheduleEnd = LocalTime.of(7, 0),
                        scheduleDays = setOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY,
                        ),
                        scheduleStatus = ScheduleUiStatus.AUTOMATIC,
                    ),
                    onEnabledChange = {},
                    onStrengthChange = {},
                    onOverlayPermissionClick = {},
                    onNotificationSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("schedule_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Automatic").assertIsDisplayed()
        composeRule.onNodeWithText("Weekdays").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_time_summary").assertIsDisplayed()
        composeRule.onAllNodesWithTag("schedule_start_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("schedule_day_monday").assertCountEquals(0)

        composeRule.onNodeWithTag("edit_schedule_button").performClick()
        composeRule.onNodeWithTag("schedule_start_button").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_end_button").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_day_monday").assertIsSelected()
        composeRule.onNodeWithTag("schedule_day_sunday").assertIsNotSelected()
        composeRule.onNodeWithTag("schedule_editor_done").assertIsDisplayed()
    }

    @Test
    fun reminderOnlySchedule_exposesAutomaticStartRecovery() {
        var permissionRequested = false
        composeRule.setContent {
            LowlightTheme {
                LowlightScreen(
                    state = LowlightUiState(
                        hasOverlayPermission = true,
                        scheduleEnabled = true,
                        scheduleStatus = ScheduleUiStatus.REMINDER_ONLY,
                    ),
                    onEnabledChange = {},
                    onStrengthChange = {},
                    onOverlayPermissionClick = {},
                    onNotificationSettingsClick = {},
                    onExactAlarmPermissionClick = { permissionRequested = true },
                )
            }
        }

        composeRule.onNodeWithText("Reminder").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("exact_alarm_permission_button").performClick()
        composeRule.runOnIdle { assertTrue(permissionRequested) }
    }

    @Test
    fun aboutButton_explainsPrivacyAndOverlayBoundary() {
        composeRule.setContent {
            LowlightTheme {
                LowlightScreen(
                    state = LowlightUiState(hasOverlayPermission = true),
                    onEnabledChange = {},
                    onStrengthChange = {},
                    onOverlayPermissionClick = {},
                    onNotificationSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("about_button").performClick()
        composeRule.onNodeWithText("About Lowlight").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Lowlight adds a touch-through shade over ordinary apps. " +
                "It never reads the screen and never connects to the internet.",
        ).assertIsDisplayed()
    }
}
