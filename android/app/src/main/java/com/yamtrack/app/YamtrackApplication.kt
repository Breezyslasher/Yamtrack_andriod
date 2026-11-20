package com.yamtrack.app

import android.app.Application
import com.yamtrack.app.data.repository.ArtworkOverrides
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YamtrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ArtworkOverrides.init(this)
    }
}
