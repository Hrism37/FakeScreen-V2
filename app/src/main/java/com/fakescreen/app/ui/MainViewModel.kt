package com.fakescreen.app.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fakescreen.app.appContainer
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.AppSettings
import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.device.DeviceCompatibility
import com.fakescreen.app.security.VerifyResult
import com.fakescreen.app.service.FakeScreenService
import com.fakescreen.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    val session = container.sessionState.state
    val lastError = container.sessionState.lastError
    val shizukuStatus: StateFlow<ShizukuManager.Status> = container.shizukuManager.status
    val shizukuDetail: StateFlow<String> = container.shizukuManager.detail

    private val _hasPin = MutableStateFlow(false)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _pinLength = MutableStateFlow(AppConstants.PIN_MIN_LENGTH)
    val pinLength: StateFlow<Int> = _pinLength.asStateFlow()

    private val _recoveryMessage = MutableStateFlow<String?>(null)
    val recoveryMessage: StateFlow<String?> = _recoveryMessage.asStateFlow()

    private val _deviceInfo = MutableStateFlow(container.deviceCompatibility.snapshot())
    val deviceInfo: StateFlow<DeviceCompatibility.Info> = _deviceInfo.asStateFlow()

    private val _report = MutableStateFlow("")
    val report: StateFlow<String> = _report.asStateFlow()

    init {
        refreshAll()
    }

    fun onResume() {
        refreshAll()
        checkRecovery()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _hasPin.value = container.passwordManager.hasPin()
            _pinLength.value = container.passwordManager.pinLength()
            container.shizukuManager.refresh()
            _deviceInfo.value = container.deviceCompatibility.snapshot()
            refreshReport()
        }
    }

    fun checkRecovery() {
        viewModelScope.launch {
            if (session.value.active || session.value.serviceRunning) return@launch
            try {
                val outcome = container.recoveryManager.reconcile("activity")
                if (outcome != null) {
                    _recoveryMessage.value = outcome.message
                    refreshAll()
                }
            } catch (t: Throwable) {
                AppLog.e("Launch recovery failed", t)
            }
        }
    }

    fun dismissRecoveryMessage() {
        _recoveryMessage.value = null
    }

    fun clearError() = container.sessionState.clearError()

    fun setDefaultMode(mode: ScreenMode) {
        viewModelScope.launch {
            container.settingsRepository.setDefaultMode(mode)
            refreshReport()
        }
    }

    fun setMaxSessionMinutes(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.setMaxSessionMinutes(minutes)
            refreshReport()
        }
    }

    fun setExperimentalAudio(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setExperimentalAudio(enabled)
            refreshReport()
        }
    }

    fun setAllowGlobalBrightness(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAllowGlobalBrightness(enabled)
            refreshReport()
        }
    }

    fun setAutoRestore(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAutoRestore(enabled)
            refreshReport()
        }
    }

    fun setPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                container.passwordManager.setInitialPin(pin)
            } catch (t: Throwable) {
                AppLog.e("setPin failed", t)
                false
            }
            if (ok) {
                _hasPin.value = true
                _pinLength.value = pin.length
            }
            onResult(ok)
        }
    }

    fun verifyPinForChange(pin: String, onResult: (VerifyResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                container.passwordManager.verify(pin)
            } catch (t: Throwable) {
                AppLog.e("verifyPin failed", t)
                VerifyResult.NotConfigured
            }
            onResult(result)
        }
    }

    fun changePin(current: String, newPin: String, onResult: (VerifyResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                container.passwordManager.changePin(current, newPin)
            } catch (t: Throwable) {
                AppLog.e("changePin failed", t)
                VerifyResult.NotConfigured
            }
            if (result is VerifyResult.Success) {
                _pinLength.value = newPin.length
                _hasPin.value = true
            }
            onResult(result)
        }
    }

    fun startSession(): Boolean {
        val app = getApplication<Application>()
        if (!Settings.canDrawOverlays(app)) {
            container.sessionState.postError("请先授予悬浮窗权限")
            return false
        }
        if (!_hasPin.value) {
            container.sessionState.postError("请先设置 PIN 密码")
            return false
        }
        val intent = Intent(app, FakeScreenService::class.java).apply {
            action = AppConstants.ACTION_START_SESSION
            putExtra(AppConstants.EXTRA_MODE, settings.value.defaultMode.name)
        }
        return try {
            ContextCompat.startForegroundService(app, intent)
            true
        } catch (t: Throwable) {
            AppLog.e("Unable to start session service", t)
            container.sessionState.postError("无法启动前台服务：${t.javaClass.simpleName}")
            false
        }
    }

    fun requestShizukuPermission(activity: Activity) {
        container.shizukuManager.requestPermission(activity)
    }

    fun onShizukuPermissionResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        container.shizukuManager.handlePermissionResult(requestCode, permissions, grantResults)
    }

    fun openShizukuApp(): Boolean = container.shizukuManager.openShizukuApp()

    fun openBatteryOptimizationSettings() {
        container.deviceCompatibility.openBatteryOptimizationSettings()
    }

    fun openWriteSettingsPermission(context: Context) {
        container.brightnessManager.openWriteSettingsPermission(context)
    }

    fun refreshReport() {
        _report.value = container.deviceCompatibility.buildReport(
            settings = settings.value,
            shizukuStatus = shizukuStatus.value.label,
            pinConfigured = _hasPin.value,
            sessionActive = session.value.active,
        )
    }

    fun refreshReportAsync() {
        viewModelScope.launch(Dispatchers.Default) {
            _report.value = container.deviceCompatibility.buildReport(
                settings = settings.value,
                shizukuStatus = shizukuStatus.value.label,
                pinConfigured = _hasPin.value,
                sessionActive = session.value.active,
            )
        }
    }

    fun buildReportNow(): String = container.deviceCompatibility.buildReport(
        settings = settings.value,
        shizukuStatus = shizukuStatus.value.label,
        pinConfigured = _hasPin.value,
        sessionActive = session.value.active,
    )

    suspend fun currentPinLength(): Int = withContext(Dispatchers.IO) {
        container.passwordManager.pinLength()
    }
}
