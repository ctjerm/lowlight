package io.github.ctjerm.lowlight

import android.app.Application
import io.github.ctjerm.lowlight.dimmer.DimmerStateStore

class LowlightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The shade lives in this process, so a new process has none. Task Manager's Stop and force
        // stop skip onDestroy, so without this the app and tile would show dimming as on, and the
        // next launch would bring the shade back.
        DimmerStateStore(this).deactivate()
    }
}
