package xyz.devnerd.anmediaplayer

import android.app.Application
import xyz.devnerd.anmediaplayer.data.AppRepo

class AnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppRepo.init(this)
    }
}
