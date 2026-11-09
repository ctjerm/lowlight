package io.github.ctjerm.lowlight

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ctjerm.lowlight.dimmer.ActivationSource
import io.github.ctjerm.lowlight.dimmer.DimmerLevel
import io.github.ctjerm.lowlight.dimmer.DimmerStateStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dimmingChangedOutsideTheOpenScreen_updatesItsSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stateStore = DimmerStateStore(context)

        try {
            // The Quick Settings tile and the notification change the state while the screen stays open.
            stateStore.activate(DimmerLevel.from(DimmerLevel.DEFAULT_PERCENT), ActivationSource.MANUAL)
            waitForSwitchDescription(context.getString(R.string.stop_dimming))

            stateStore.deactivate()
            waitForSwitchDescription(context.getString(R.string.start_dimming))
        } finally {
            stateStore.deactivate()
        }
    }

    private fun waitForSwitchDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription(description))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
