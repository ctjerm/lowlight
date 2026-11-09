package io.github.ctjerm.lowlight.dimmer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object DimmerCommands {
    fun enable(
        context: Context,
        level: DimmerLevel,
        source: ActivationSource = ActivationSource.MANUAL,
    ) {
        val appContext = context.applicationContext
        DimmerStateStore(appContext).activate(level, source)
        ContextCompat.startForegroundService(
            appContext,
            serviceIntent(appContext, ScreenDimmerService.ACTION_ENABLE, level),
        )
    }

    fun update(context: Context, level: DimmerLevel) {
        val appContext = context.applicationContext
        DimmerStateStore(appContext).setLevel(level)
        appContext.startService(
            serviceIntent(appContext, ScreenDimmerService.ACTION_UPDATE, level),
        )
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        DimmerStateStore(appContext).deactivate()
        // A service that isn't in the foreground yet stops itself once it sees the cleared state;
        // stopping it from here before then crashes the app.
        if (ScreenDimmerService.isRunning) {
            appContext.stopService(Intent(appContext, ScreenDimmerService::class.java))
        }
    }

    fun disableWithFade(context: Context) {
        val appContext = context.applicationContext
        DimmerStateStore(appContext).deactivate()
        appContext.startService(
            Intent(appContext, ScreenDimmerService::class.java).apply {
                action = ScreenDimmerService.ACTION_DISABLE
            },
        )
    }

    private fun serviceIntent(
        context: Context,
        actionName: String,
        level: DimmerLevel,
    ) = Intent(context, ScreenDimmerService::class.java).apply {
        action = actionName
        putExtra(ScreenDimmerService.EXTRA_LEVEL_PERCENT, level.percent)
    }
}
