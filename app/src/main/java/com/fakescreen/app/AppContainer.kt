package com.fakescreen.app

import android.content.Context
import com.fakescreen.app.brightness.BrightnessManager
import com.fakescreen.app.data.SessionStore
import com.fakescreen.app.data.SettingsRepository
import com.fakescreen.app.device.DeviceCompatibility
import com.fakescreen.app.recovery.RecoveryManager
import com.fakescreen.app.security.PasswordManager
import com.fakescreen.app.session.SessionStateHolder
import com.fakescreen.app.shizuku.ShizukuManager

/**
 * Manual dependency container. A DI framework would be overkill for an app of
 * this size, but every long lived component is still created exactly once.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val sessionStore = SessionStore(appContext)
    val passwordManager = PasswordManager(appContext)
    val brightnessManager = BrightnessManager(appContext)
    val shizukuManager = ShizukuManager(appContext)
    val sessionState = SessionStateHolder()
    val deviceCompatibility = DeviceCompatibility(appContext)
    val recoveryManager = RecoveryManager(
        context = appContext,
        sessionStore = sessionStore,
        brightnessManager = brightnessManager,
        settingsRepository = settingsRepository,
        sessionState = sessionState,
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FakeScreenApp).container
