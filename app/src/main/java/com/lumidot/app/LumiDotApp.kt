package com.lumidot.app

import android.app.Application
import com.lumidot.app.data.SettingsRepository

class LumiDotApp : Application() {
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
    }
}

val android.content.Context.lumiSettings: SettingsRepository
    get() = (applicationContext as LumiDotApp).settings
