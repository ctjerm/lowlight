package io.github.ctjerm.lowlight.dimmer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.ctjerm.lowlight.MainActivity
import io.github.ctjerm.lowlight.R

class DimmerTileService : TileService() {
    // The notification's stop button sits in the same panel, so the tile follows changes while visible.
    private var stateObservation: AutoCloseable? = null

    override fun onStartListening() {
        super.onStartListening()
        stateObservation = DimmerStateStore(this).observeChanges(::updateTile)
        updateTile()
    }

    override fun onStopListening() {
        stateObservation?.close()
        stateObservation = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val stateStore = DimmerStateStore(this)
        when {
            stateStore.isEnabled() -> DimmerCommands.disableWithFade(this)
            Settings.canDrawOverlays(this) -> DimmerCommands.enable(this, stateStore.level())
            else -> openApp()
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val stateStore = DimmerStateStore(this)
        val isEnabled = stateStore.isEnabled()
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) {
                getString(R.string.dim_percent, stateStore.level().percent)
            } else {
                getString(R.string.status_off_short)
            }
        }
        tile.updateTile()
    }

    // The overlay permission can only be granted from the app, so a tap without it opens Lowlight.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, REQUEST_OPEN_APP, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val REQUEST_OPEN_APP = 4201
    }
}
