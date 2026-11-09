package io.github.ctjerm.lowlight.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ctjerm.lowlight.R
import io.github.ctjerm.lowlight.dimmer.DimmerLevel
import io.github.ctjerm.lowlight.ui.theme.LowlightAccent
import io.github.ctjerm.lowlight.ui.theme.LowlightNightTrack
import io.github.ctjerm.lowlight.ui.theme.LowlightOnAccent
import io.github.ctjerm.lowlight.ui.theme.LowlightOutline
import io.github.ctjerm.lowlight.ui.theme.LowlightPage
import io.github.ctjerm.lowlight.ui.theme.LowlightSurface
import io.github.ctjerm.lowlight.ui.theme.LowlightSurfaceRaised
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

private const val MOTION_FAST_MS = 140
private const val MOTION_STANDARD_MS = 220

enum class ScheduleUiStatus {
    OFF,
    AUTOMATIC,
    REMINDER_ONLY,
    NOTIFICATIONS_BLOCKED,
    OVERLAY_PERMISSION_NEEDED,
    INVALID,
}

data class LowlightUiState(
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val enabled: Boolean = false,
    val strengthPercent: Int = DimmerLevel.DEFAULT_PERCENT,
    val scheduleEnabled: Boolean = false,
    val scheduleStart: LocalTime = LocalTime.of(22, 0),
    val scheduleEnd: LocalTime = LocalTime.of(7, 0),
    val scheduleDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val scheduleStatus: ScheduleUiStatus = ScheduleUiStatus.OFF,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LowlightScreen(
    state: LowlightUiState,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onOverlayPermissionClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleEnabledChange: (Boolean) -> Unit = {},
    onScheduleStartClick: () -> Unit = {},
    onScheduleEndClick: () -> Unit = {},
    onScheduleDayToggle: (DayOfWeek) -> Unit = {},
    onExactAlarmPermissionClick: () -> Unit = {},
) {
    var showScheduleEditor by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LowlightPage,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = { LowlightTopBar(onAboutClick = { showAbout = true }) },
    ) { safePadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LowlightPage)
                .padding(safePadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                DimmerPanel(
                    state = state,
                    onEnabledChange = onEnabledChange,
                    onStrengthChange = onStrengthChange,
                )

                if (!state.hasOverlayPermission) {
                    Spacer(Modifier.height(20.dp))
                    OverlayPermissionPanel(onOverlayPermissionClick)
                }

                Spacer(Modifier.height(30.dp))
                ScheduleSummary(
                    state = state,
                    onEnabledChange = onScheduleEnabledChange,
                    onEditClick = { showScheduleEditor = true },
                    onExactAlarmPermissionClick = onExactAlarmPermissionClick,
                )

                if ((state.enabled || state.scheduleEnabled) && !state.hasNotificationPermission) {
                    Spacer(Modifier.height(16.dp))
                    NotificationPermissionPanel(onNotificationSettingsClick)
                }

                Spacer(Modifier.height(92.dp))
                Text(
                    text = stringResource(R.string.trust_compact),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showScheduleEditor) {
        ScheduleEditorSheet(
            state = state,
            onDismiss = { showScheduleEditor = false },
            onStartClick = onScheduleStartClick,
            onEndClick = onScheduleEndClick,
            onDayToggle = onScheduleDayToggle,
            onExactAlarmPermissionClick = onExactAlarmPermissionClick,
        )
    }

    if (showAbout) {
        AboutSheet(onDismiss = { showAbout = false })
    }
}

@Composable
private fun LowlightTopBar(onAboutClick: () -> Unit) {
    val aboutDescription = stringResource(R.string.more_options)
    Surface(color = LowlightPage, tonalElevation = 0.dp) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrescentMark()
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onAboutClick,
                    modifier = Modifier.testTag("about_button"),
                ) {
                    MoreOptionsGlyph(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = aboutDescription },
                    )
                }
            }
            HorizontalDivider(color = LowlightOutline.copy(alpha = 0.18f))
        }
    }
}

@Composable
private fun CrescentMark() {
    Canvas(modifier = Modifier.size(34.dp)) {
        drawCircle(color = LowlightAccent, radius = size.minDimension * 0.47f)
        drawCircle(
            color = LowlightPage,
            radius = size.minDimension * 0.39f,
            center = center + Offset(size.width * 0.19f, -size.height * 0.12f),
        )
    }
}

@Composable
private fun MoreOptionsGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.095f
        listOf(0.27f, 0.5f, 0.73f).forEach { y ->
            drawCircle(
                color = Color(0xFFD7D0C6),
                radius = radius,
                center = Offset(size.width / 2f, size.height * y),
            )
        }
    }
}

@Composable
private fun DimmerPanel(
    state: LowlightUiState,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
) {
    val statusText = when {
        !state.hasOverlayPermission -> stringResource(R.string.status_permission_needed)
        state.enabled -> stringResource(R.string.status_on_short)
        else -> stringResource(R.string.status_off_short)
    }
    val stopOrStartDescription =
        stringResource(if (state.enabled) R.string.stop_dimming else R.string.start_dimming)
    val shadeStrengthDescription = stringResource(R.string.shade_strength)
    val statusColor by animateColorAsState(
        targetValue = when {
            !state.hasOverlayPermission -> MaterialTheme.colorScheme.error
            state.enabled -> LowlightAccent
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "dimmer status color",
    )
    val percentageScale by animateFloatAsState(
        targetValue = if (state.enabled) 1f else 0.96f,
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "dimming percentage scale",
    )
    val percentageAlpha by animateFloatAsState(
        targetValue = if (state.enabled) 1f else 0.86f,
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "dimming percentage alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dimmer_panel"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.screen_dimming),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = statusText,
                    transitionSpec = {
                        fadeIn(tween(MOTION_FAST_MS)) togetherWith
                            fadeOut(tween(MOTION_FAST_MS))
                    },
                    label = "dimmer status",
                ) { visibleStatus ->
                    Text(
                        text = visibleStatus,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            LowlightSwitch(
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                enabled = state.hasOverlayPermission,
                modifier = Modifier
                    .testTag("dimming_switch")
                    .semantics {
                        contentDescription = stopOrStartDescription
                    },
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.dim_percent, state.strengthPercent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dimming_percentage")
                .graphicsLayer {
                    scaleX = percentageScale
                    scaleY = percentageScale
                    alpha = percentageAlpha
                },
            color = LowlightAccent,
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.shade_strength),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Slider(
            value = state.strengthPercent.toFloat(),
            onValueChange = { onStrengthChange(it.toInt()) },
            valueRange = DimmerLevel.MIN_PERCENT.toFloat()..DimmerLevel.MAX_PERCENT.toFloat(),
            steps = 13,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dim_level_slider")
                .semantics { contentDescription = shadeStrengthDescription },
            colors = SliderDefaults.colors(
                thumbColor = LowlightAccent,
                activeTrackColor = LowlightAccent,
                activeTickColor = LowlightOnAccent.copy(alpha = 0.55f),
                inactiveTrackColor = LowlightNightTrack,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.subtle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.deep),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun LowlightSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = LowlightOnAccent,
            checkedTrackColor = LowlightAccent,
            checkedBorderColor = LowlightAccent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = LowlightSurfaceRaised,
            uncheckedBorderColor = LowlightOutline,
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            disabledUncheckedTrackColor = LowlightSurfaceRaised.copy(alpha = 0.7f),
        ),
    )
}

@Composable
private fun ScheduleSummary(
    state: LowlightUiState,
    onEnabledChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onExactAlarmPermissionClick: () -> Unit,
) {
    val start = formattedTime(state.scheduleStart)
    val end = formattedTime(state.scheduleEnd)
    val scheduleSwitchDescription = stringResource(
        if (state.scheduleEnabled) R.string.turn_schedule_off else R.string.turn_schedule_on,
    )
    val exceptionalStatus = state.scheduleStatus !in setOf(
        ScheduleUiStatus.OFF,
        ScheduleUiStatus.AUTOMATIC,
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (state.scheduleEnabled) {
            LowlightAccent.copy(alpha = 0.22f)
        } else {
            LowlightOutline.copy(alpha = 0.24f)
        },
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "schedule border",
    )
    val cardElevation by animateDpAsState(
        targetValue = if (state.scheduleEnabled) 4.dp else 2.dp,
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "schedule elevation",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
            )
            .testTag("schedule_summary"),
        color = LowlightSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = cardElevation,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    if (maxWidth < 210.dp) {
                        Column {
                            Text(
                                text = stringResource(R.string.schedule_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            ScheduleStatusBadge(state.scheduleStatus)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.schedule_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(Modifier.width(10.dp))
                            ScheduleStatusBadge(state.scheduleStatus)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                LowlightSwitch(
                    checked = state.scheduleEnabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier
                        .testTag("schedule_switch")
                        .semantics {
                            contentDescription = scheduleSwitchDescription
                        },
                )
            }

            Spacer(Modifier.height(26.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val timeSize = when {
                    maxWidth < 250.dp -> 22.sp
                    maxWidth < 300.dp -> 26.sp
                    else -> 30.sp
                }
                Text(
                    text = stringResource(R.string.schedule_time_range, start, end),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_time_summary"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = timeSize,
                        lineHeight = timeSize * 1.2f,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = scheduleDaysSummary(state.scheduleDays),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )

            AnimatedVisibility(
                visible = exceptionalStatus,
                enter = fadeIn(tween(MOTION_FAST_MS)) +
                    expandVertically(tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(MOTION_FAST_MS)) +
                    shrinkVertically(tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = scheduleStatusBody(state.scheduleStatus),
                        color = if (state.scheduleStatus == ScheduleUiStatus.REMINDER_ONLY) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("schedule_status_detail"),
                    )
                }
            }

            if (
                state.scheduleStatus == ScheduleUiStatus.REMINDER_ONLY ||
                state.scheduleStatus == ScheduleUiStatus.NOTIFICATIONS_BLOCKED
            ) {
                TextButton(
                    onClick = onExactAlarmPermissionClick,
                    modifier = Modifier.testTag("exact_alarm_permission_button"),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.allow_automatic_starts))
                }
            } else {
                Spacer(Modifier.height(22.dp))
            }

            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("edit_schedule_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LowlightOutline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC7B3E5)),
            ) {
                EditGlyph(Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.edit_schedule),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ScheduleStatusBadge(status: ScheduleUiStatus) {
    val isProblem = status in setOf(
        ScheduleUiStatus.NOTIFICATIONS_BLOCKED,
        ScheduleUiStatus.OVERLAY_PERMISSION_NEEDED,
        ScheduleUiStatus.INVALID,
    )
    val badgeColor by animateColorAsState(
        targetValue = if (isProblem) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        } else {
            LowlightNightTrack.copy(alpha = 0.72f)
        },
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "schedule badge background",
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (isProblem) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFFD3C2E9)
        },
        animationSpec = tween(MOTION_STANDARD_MS, easing = FastOutSlowInEasing),
        label = "schedule badge content",
    )
    Surface(
        color = badgeColor,
        contentColor = badgeContentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        AnimatedContent(
            targetState = status,
            modifier = Modifier
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .testTag("schedule_status"),
            transitionSpec = {
                fadeIn(tween(MOTION_FAST_MS)) togetherWith
                    fadeOut(tween(MOTION_FAST_MS))
            },
            label = "schedule status",
        ) { visibleStatus ->
            Text(
                text = scheduleBadgeLabel(visibleStatus),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EditGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val start = Offset(size.width * 0.22f, size.height * 0.78f)
        val end = Offset(size.width * 0.78f, size.height * 0.22f)
        drawLine(
            color = Color(0xFFC7B3E5),
            start = start,
            end = end,
            strokeWidth = size.minDimension * 0.23f,
            cap = StrokeCap.Square,
        )
        drawCircle(
            color = Color(0xFFC7B3E5),
            radius = size.minDimension * 0.12f,
            center = end,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    state: LowlightUiState,
    onDismiss: () -> Unit,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onExactAlarmPermissionClick: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LowlightSurfaceRaised,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LowlightOutline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.edit_schedule),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                )
                ScheduleStatusBadge(state.scheduleStatus)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = scheduleStatusBody(state.scheduleStatus),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScheduleTimeButton(
                    label = stringResource(R.string.schedule_start),
                    time = state.scheduleStart,
                    onClick = onStartClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_start_button"),
                )
                ScheduleTimeButton(
                    label = stringResource(R.string.schedule_end),
                    time = state.scheduleEnd,
                    onClick = onEndClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("schedule_end_button"),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.schedule_days),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.scheduleDays,
                        onClick = { onDayToggle(day) },
                        label = { Text(dayLabel(day)) },
                        modifier = Modifier.testTag("schedule_day_${day.name.lowercase()}"),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.schedule_days_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (
                state.scheduleStatus == ScheduleUiStatus.REMINDER_ONLY ||
                state.scheduleStatus == ScheduleUiStatus.NOTIFICATIONS_BLOCKED
            ) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onExactAlarmPermissionClick) {
                    Text(stringResource(R.string.allow_automatic_starts))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("schedule_editor_done"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LowlightAccent,
                    contentColor = LowlightOnAccent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LowlightSurfaceRaised,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LowlightOutline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.about_lowlight),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.about_lowlight_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.capability_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LowlightAccent,
                    contentColor = LowlightOnAccent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun ScheduleTimeButton(
    label: String,
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeLabel = formattedTime(time)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 76.dp)
            .semantics { contentDescription = "$label $timeLabel" },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LowlightOutline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = timeLabel,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

@Composable
private fun OverlayPermissionPanel(onPermissionClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LowlightSurfaceRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, LowlightOutline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPermissionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("overlay_permission_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LowlightAccent,
                    contentColor = LowlightOnAccent,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.allow_screen_dimming))
            }
        }
    }
}

@Composable
private fun NotificationPermissionPanel(onNotificationSettingsClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LowlightSurfaceRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, LowlightOutline.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.notification_permission_body),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onNotificationSettingsClick) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@Composable
private fun formattedTime(time: LocalTime): String {
    val context = LocalContext.current
    val use24HourClock = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    return remember(time, use24HourClock, locale) {
        time.format(
            DateTimeFormatter.ofPattern(
                if (use24HourClock) "HH:mm" else "h:mm a",
                locale,
            ),
        )
    }
}

@Composable
private fun scheduleDaysSummary(days: Set<DayOfWeek>): String = when (days) {
    DayOfWeek.entries.toSet() -> stringResource(R.string.schedule_every_night)
    setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ) -> stringResource(R.string.schedule_weekdays)
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) ->
        stringResource(R.string.schedule_weekends)
    else -> days
        .sortedBy(DayOfWeek::getValue)
        .joinToString(" · ") { day ->
            day.getDisplayName(DayTextStyle.SHORT, Locale.getDefault())
        }
}

@Composable
private fun scheduleBadgeLabel(status: ScheduleUiStatus): String = when (status) {
    ScheduleUiStatus.OFF -> stringResource(R.string.schedule_status_off)
    ScheduleUiStatus.AUTOMATIC -> stringResource(R.string.schedule_status_automatic)
    ScheduleUiStatus.REMINDER_ONLY -> stringResource(R.string.schedule_badge_reminder)
    ScheduleUiStatus.NOTIFICATIONS_BLOCKED -> stringResource(R.string.schedule_badge_blocked)
    ScheduleUiStatus.OVERLAY_PERMISSION_NEEDED -> stringResource(R.string.schedule_badge_permission)
    ScheduleUiStatus.INVALID -> stringResource(R.string.schedule_badge_attention)
}

@Composable
private fun scheduleStatusBody(status: ScheduleUiStatus): String = when (status) {
    ScheduleUiStatus.OFF -> stringResource(R.string.schedule_status_off_body)
    ScheduleUiStatus.AUTOMATIC -> stringResource(R.string.schedule_status_automatic_body)
    ScheduleUiStatus.REMINDER_ONLY -> stringResource(R.string.schedule_status_reminder_body)
    ScheduleUiStatus.NOTIFICATIONS_BLOCKED ->
        stringResource(R.string.schedule_status_notifications_blocked_body)
    ScheduleUiStatus.OVERLAY_PERMISSION_NEEDED ->
        stringResource(R.string.schedule_status_overlay_needed_body)
    ScheduleUiStatus.INVALID -> stringResource(R.string.schedule_status_invalid_body)
}

@Composable
private fun dayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.day_monday
        DayOfWeek.TUESDAY -> R.string.day_tuesday
        DayOfWeek.WEDNESDAY -> R.string.day_wednesday
        DayOfWeek.THURSDAY -> R.string.day_thursday
        DayOfWeek.FRIDAY -> R.string.day_friday
        DayOfWeek.SATURDAY -> R.string.day_saturday
        DayOfWeek.SUNDAY -> R.string.day_sunday
    },
)
