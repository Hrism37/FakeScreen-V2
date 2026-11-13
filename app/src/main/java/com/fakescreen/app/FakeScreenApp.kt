package com.fakescreen.app

import android.app.Application
import com.fakescreen.app.core.AppLog

class FakeScreenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.fakescreen.app.service.NotificationHelper.createChannels(this)
        AppLog.i("FakeScreen application created")
    }
}
