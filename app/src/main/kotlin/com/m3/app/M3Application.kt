package com.m3.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class M3Application : Application() {
    @Inject
    lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        startup.start()
    }
}
