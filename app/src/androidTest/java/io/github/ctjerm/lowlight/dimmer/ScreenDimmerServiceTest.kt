package io.github.ctjerm.lowlight.dimmer

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenDimmerServiceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun revokingOverlayPermission_stopsDimmingAndItsNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        setOverlayPermission(allowed = true)
        waitFor { Settings.canDrawOverlays(context) }
        assumeTrue(Settings.canDrawOverlays(context))
        val stateStore = DimmerStateStore(context)

        try {
            DimmerCommands.enable(context, DimmerLevel.from(DimmerLevel.DEFAULT_PERCENT))
            waitFor { ScreenDimmerService.isRunning }

            setOverlayPermission(allowed = false)
            waitFor { !ScreenDimmerService.isRunning }

            assertFalse(ScreenDimmerService.isRunning)
            assertFalse(stateStore.isEnabled())
        } finally {
            setOverlayPermission(allowed = true)
            DimmerCommands.disable(context)
        }
    }

    @Test
    fun turningOffBeforeTheServiceStarts_doesNotCrash() {
        assertOffBeforeStartDoesNotCrash { context -> DimmerCommands.disableWithFade(context) }
    }

    @Test
    fun scheduleStopBeforeTheServiceStarts_doesNotCrash() {
        assertOffBeforeStartDoesNotCrash { context -> DimmerCommands.disable(context) }
    }

    @Test
    fun shadeDarkensTheScreenByTheChosenStrength() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        setOverlayPermission(allowed = true)
        waitFor { Settings.canDrawOverlays(context) }
        assumeTrue(Settings.canDrawOverlays(context))
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(ComposeColor.White))
        }
        composeRule.waitForIdle()
        val before = instrumentation.uiAutomation.takeScreenshot()

        try {
            DimmerCommands.enable(context, DimmerLevel.from(50))
            waitFor { ScreenDimmerService.isRunning }
            Thread.sleep(1_000)
            val after = instrumentation.uiAutomation.takeScreenshot()

            val ratios = (0 until before.width step 20).flatMap { x ->
                (0 until before.height step 20).mapNotNull { y ->
                    val lit = Color.red(before.getPixel(x, y))
                    if (lit < 200) null else Color.red(after.getPixel(x, y)) / lit.toFloat()
                }
            }.sorted()
            assertEquals(0.5f, ratios[ratios.size / 2], 0.04f)
        } finally {
            DimmerCommands.disable(context)
            waitFor { !ScreenDimmerService.isRunning }
        }
    }

    private fun assertOffBeforeStartDoesNotCrash(turnOff: (Context) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        setOverlayPermission(allowed = true)
        waitFor { Settings.canDrawOverlays(context) }
        assumeTrue(Settings.canDrawOverlays(context))

        // On the main thread the service can't handle the start before it is turned off.
        instrumentation.runOnMainSync {
            DimmerCommands.enable(context, DimmerLevel.from(DimmerLevel.DEFAULT_PERCENT))
            turnOff(context)
        }
        // Android crashes the app as soon as the service stops without reaching the foreground.
        Thread.sleep(3_000)

        assertFalse(ScreenDimmerService.isRunning)
        assertFalse(DimmerStateStore(context).isEnabled())
    }

    private fun setOverlayPermission(allowed: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = if (allowed) "allow" else "deny"
        instrumentation.uiAutomation
            .executeShellCommand(
                "appops set ${instrumentation.targetContext.packageName} SYSTEM_ALERT_WINDOW $mode",
            )
            .close()
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
    }
}
