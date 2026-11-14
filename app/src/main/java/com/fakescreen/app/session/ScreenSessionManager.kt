package com.fakescreen.app.session

import android.app.Service
import android.provider.Settings
import com.fakescreen.app.AppContainer
import com.fakescreen.app.brightness.BrightnessManager
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.AppSettings
import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.data.SessionRecord
import com.fakescreen.app.keepawake.KeepAwakeManager
import com.fakescreen.app.overlay.OverlayManager
import com.fakescreen.app.overlay.PinOverlayState
import com.fakescreen.app.security.LockoutPolicy
import com.fakescreen.app.security.VerifyResult
import com.fakescreen.app.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The session state machine that runs inside the foreground service.
 *
 * Invariants:
 *  - a session record is written before any UI/brightness side effect;
 *  - every exit path restores the captured brightness before hiding the overlay;
 *  - ending a session clears the record only after the brightness was restored.
 */
class ScreenSessionManager(
    private val service: Service,
    private val container: AppContainer,
) {

    enum class StopReason {
        PIN,
        VOLUME_SHORTCUT,
        TIMEOUT,
        OVERLAY_LOST,
        SERVICE_COMMAND,
        RESUME_EXPIRED,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val overlay = OverlayManager(service)
    private val keepAwake = KeepAwakeManager(service)
    private val pinState = MutableStateFlow(PinOverlayState())

    private var record: SessionRecord? = null
    private var autoStopJob: Job? = null
    private var stopping = false
    private var started = false
    private var privacyLockRunning = false
    private var volumeStopEnabled = true

    fun isActive(): Boolean = started && record != null

    suspend fun start(mode: ScreenMode): Boolean {
        if (started) return true
        val appContext = service.applicationContext
        if (!Settings.canDrawOverlays(appContext)) {
            fail("缺少悬浮窗权限，无法开始模拟息屏")
            return false
        }
        if (!container.passwordManager.hasPin()) {
            fail("请先设置 PIN 密码")
            return false
        }

        val settings = container.settingsRepository.settings.first()
        val pinLength = container.passwordManager.pinLength()
        volumeStopEnabled = settings.volumeShortcutStopEnabled
        val snapshot = container.brightnessManager.readSnapshot()

        var actualMode = mode
        var downgraded = false
        if (mode == ScreenMode.TRUE_OFF && !container.shizukuManager.isReady()) {
            actualMode = ScreenMode.PURE_BLACK
            downgraded = true
        }

        var newRecord = SessionRecord.newSession(
            mode = actualMode,
            originalBrightness = snapshot.raw,
            originalBrightnessMode = snapshot.mode,
        )
        if (actualMode == ScreenMode.DIM) {
            newRecord = newRecord.copy(
                appliedWindowBrightness = BrightnessManager.WINDOW_MIN_BRIGHTNESS
            )
        }
        if (!container.sessionStore.save(newRecord)) {
            fail("无法写入会话状态，已取消")
            return false
        }
        record = newRecord
        started = true

        pinState.value = PinOverlayState(pinLength = pinLength)
        val shown = overlay.show(
            pinState = pinState,
            onSubmit = { pin -> submitPin(pin) },
            windowBrightness = newRecord.appliedWindowBrightness,
            keepScreenOn = actualMode != ScreenMode.TRUE_OFF,
            onOverlayLost = { onOverlayLost() },
            onVolumeShortcut = { maybeVolumeStop() },
            volumeShortcutEnabled = volumeStopEnabled,
            onPinVisibilityChanged = { visible ->
                if (actualMode == ScreenMode.DIM) {
                    overlay.updateBrightness(
                        if (visible) OverlayManager.READABLE_WINDOW_BRIGHTNESS
                        else BrightnessManager.WINDOW_MIN_BRIGHTNESS
                    )
                }
            },
        )
        if (!shown) {
            cleanupOnDestroy()
            fail("悬浮窗创建失败，请检查悬浮窗权限")
            return false
        }

        keepAwake.start(settings.experimentalAudioKeepAwake)
        if (actualMode == ScreenMode.DIM) {
            applyOptionalGlobalBrightness(newRecord, snapshot, settings)
        }

        record?.let { container.sessionState.setActive(it) }
        scheduleAutoStop(settings)

        if (downgraded) {
            NotificationHelper.notifyAlert(
                service,
                "Shizuku 未就绪",
                "无法使用真熄屏模式，已自动降级为纯黑覆盖。",
            )
        }
        if (actualMode == ScreenMode.TRUE_OFF) {
            // Give the overlay one frame to appear before switching the panel off.
            delay(350L)
            val screenOff = container.shizukuManager.turnScreenOff()
            if (!screenOff) {
                val fallback = record?.copy(mode = ScreenMode.PURE_BLACK)
                if (fallback != null) {
                    container.sessionStore.save(fallback)
                    record = fallback
                    overlay.setKeepScreenOn(true)
                    overlay.updateBrightness(null)
                    container.sessionState.setActive(fallback)
                }
                NotificationHelper.notifyAlert(
                    service,
                    "真熄屏失败",
                    "Shizuku 无法关闭屏幕，已自动降级为纯黑覆盖。",
                )
            }
        }
        AppLog.i("Session started id=${newRecord.id} mode=$actualMode")
        return true
    }

    /** Used when the service was restarted by the system with a null intent. */
    suspend fun resumeExisting(): Boolean {
        if (started) return true
        val saved = container.sessionStore.load() ?: return false
        val age = System.currentTimeMillis() - saved.startedAt
        val settings = container.settingsRepository.settings.first()

        val noLimit = settings.noMaxSessionDuration || settings.maxSessionMinutes <= 0
        val maxAge = if (noLimit) {
            AppConstants.STALE_SESSION_MILLIS
        } else {
            minOf(
                AppConstants.STALE_SESSION_MILLIS,
                settings.maxSessionMinutes * 60_000L + AppConstants.SESSION_GRACE_MILLIS,
            )
        }
        if (age > maxAge || !container.passwordManager.hasPin()) {
            AppLog.w("Refusing to resume stale session (age=${age}ms)")
            restoreIfPossible(saved)
            container.sessionStore.clear()
            return false
        }

        volumeStopEnabled = settings.volumeShortcutStopEnabled
        val pinLength = container.passwordManager.pinLength()
        record = saved
        pinState.value = PinOverlayState(pinLength = pinLength, message = "模拟息屏会话已恢复")
        val shown = overlay.show(
            pinState = pinState,
            onSubmit = { pin -> submitPin(pin) },
            windowBrightness = saved.appliedWindowBrightness,
            keepScreenOn = saved.mode != ScreenMode.TRUE_OFF,
            onOverlayLost = { onOverlayLost() },
            onVolumeShortcut = { maybeVolumeStop() },
            volumeShortcutEnabled = volumeStopEnabled,
            onPinVisibilityChanged = { visible ->
                if (saved.mode == ScreenMode.DIM) {
                    overlay.updateBrightness(
                        if (visible) OverlayManager.READABLE_WINDOW_BRIGHTNESS
                        else BrightnessManager.WINDOW_MIN_BRIGHTNESS
                    )
                }
            },
        )
        if (!shown) {
            restoreIfPossible(saved)
            container.sessionStore.clear()
            record = null
            fail("恢复会话失败，已还原亮度")
            return false
        }
        started = true
        keepAwake.start(settings.experimentalAudioKeepAwake)
        container.sessionState.setActive(saved)
        if (!noLimit) {
            val remainingMillis =
                settings.maxSessionMinutes * 60_000L - (System.currentTimeMillis() - saved.startedAt)
            scheduleAutoStopMillis(remainingMillis)
        }
        AppLog.i("Session resumed id=${saved.id}")
        return true
    }

    fun submitPin(pin: String) {
        if (stopping) return
        scope.launch {
            pinState.update { it.copy(verifying = true, message = null, isError = false) }
            try {
                when (val result = container.passwordManager.verify(pin)) {
                    is VerifyResult.Success -> {
                        pinState.update {
                            it.copy(verifying = false, message = "验证通过，正在退出", isError = false)
                        }
                        stop(StopReason.PIN)
                    }

                    is VerifyResult.Wrong -> {
                        val now = System.currentTimeMillis()
                        pinState.update { state ->
                            state.copy(
                                verifying = false,
                                isError = true,
                                failures = result.failures,
                                clearTick = state.clearTick + 1,
                                lockedUntil = if (result.lockMillis > 0) now + result.lockMillis else 0L,
                                message = wrongMessage(result),
                            )
                        }
                        if (result.privacyLock) engagePrivacyLock()
                    }

                    is VerifyResult.Locked -> {
                        val remaining = result.remainingMillis
                        pinState.update { state ->
                            state.copy(
                                verifying = false,
                                isError = true,
                                failures = result.failures,
                                clearTick = state.clearTick + 1,
                                lockedUntil = System.currentTimeMillis() + remaining,
                                message = "尝试次数过多，请稍后重试",
                            )
                        }
                    }

                    is VerifyResult.NotConfigured -> {
                        pinState.update {
                            it.copy(
                                verifying = false,
                                isError = true,
                                message = "PIN 数据不可用，请重启应用后重新设置",
                            )
                        }
                    }

                    is VerifyResult.InvalidFormat -> {
                        pinState.update {
                            it.copy(verifying = false, isError = true, message = "请输入 4–8 位数字 PIN")
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLog.e("PIN verification failed", t)
                pinState.update {
                    it.copy(verifying = false, isError = true, message = "验证失败，请重试")
                }
            }
        }
    }

    suspend fun stop(reason: StopReason) {
        if (stopping) return
        stopping = true
        autoStopJob?.cancel()
        autoStopJob = null
        container.sessionState.setStopping()

        val current = record
        runCatching { keepAwake.stop() }
        // Restore brightness while the black overlay still hides the transition.
        if (current?.globalBrightnessApplied == true) {
            val restored = runCatching {
                container.brightnessManager.restoreGlobal(
                    current.originalBrightness,
                    current.originalBrightnessMode,
                )
            }.getOrElse {
                AppLog.e("Brightness restore failed", it)
                false
            }
            if (!restored) {
                NotificationHelper.notifyAlert(
                    service,
                    "亮度恢复失败",
                    "无法写回系统亮度，可能缺少“修改系统设置”权限。请手动检查亮度设置。",
                )
            }
        }
        runCatching { overlay.hide() }
        container.sessionStore.clear()
        record = null
        started = false
        container.sessionState.reset()
        pinState.value = PinOverlayState()

        if (reason == StopReason.TIMEOUT) {
            NotificationHelper.notifyAlert(
                service,
                "模拟息屏已自动结束",
                "已达到最长会话时长，屏幕亮度已恢复。",
            )
        }
        AppLog.i("Session stopped: $reason")
        stopping = false
        service.stopSelf()
    }

    private fun maybeVolumeStop() {
        if (!started || stopping || !volumeStopEnabled) return
        scope.launch { stop(StopReason.VOLUME_SHORTCUT) }
    }

    /** Called by [OverlayManager] when the window was removed by the system. */
    fun onOverlayLost() {
        if (!started || stopping) return
        container.sessionState.postError("悬浮窗被系统移除，已结束本次会话")
        scope.launch { stop(StopReason.OVERLAY_LOST) }
    }

    /** Synchronous last-resort cleanup used from Service.onDestroy. */
    fun cleanupOnDestroy() {
        if (!started && record == null) return
        autoStopJob?.cancel()
        autoStopJob = null
        runCatching { keepAwake.stop() }
        val current = record
        if (current?.globalBrightnessApplied == true) {
            runCatching {
                container.brightnessManager.restoreGlobal(
                    current.originalBrightness,
                    current.originalBrightnessMode,
                )
            }
        }
        runCatching { overlay.hide() }
        container.sessionStore.clear()
        record = null
        started = false
        container.sessionState.reset()
    }

    fun release() {
        scope.cancel()
    }

    private fun applyOptionalGlobalBrightness(
        sessionRecord: SessionRecord,
        snapshot: BrightnessManager.Snapshot,
        settings: AppSettings,
    ) {
        if (!settings.allowGlobalBrightnessFallback) return
        if (!container.brightnessManager.canWriteGlobal()) return
        if (snapshot.raw !in 1..255) return
        val target = container.brightnessManager.targetGlobalRaw(snapshot.raw)
        if (target >= snapshot.raw) return

        val pending = sessionRecord.copy(
            globalBrightnessApplied = true,
            appliedGlobalBrightness = target,
            appliedGlobalMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        // Persist the intent *before* writing, so a crash leaves a recoverable trail.
        container.sessionStore.save(pending)
        record = pending

        if (container.brightnessManager.applyGlobal(target)) {
            container.sessionState.setActive(pending)
            AppLog.i("Optional global brightness applied: $target")
        } else {
            val reverted = pending.copy(
                globalBrightnessApplied = false,
                appliedGlobalBrightness = -1,
                appliedGlobalMode = -1,
            )
            container.sessionStore.save(reverted)
            record = reverted
            AppLog.w("Global brightness fallback failed, staying with window-level brightness")
        }
    }

    private fun wrongMessage(result: VerifyResult.Wrong): String = when {
        result.privacyLock -> "错误次数过多，正在关闭屏幕"
        result.lockMillis > 0 -> "PIN 错误，请稍后重试"
        else -> {
            val remaining = (LockoutPolicy.PRIVACY_LOCK_THRESHOLD - result.failures)
                .coerceAtLeast(0)
            "PIN 错误，还可尝试 $remaining 次"
        }
    }

    private fun engagePrivacyLock() {
        if (privacyLockRunning) return
        privacyLockRunning = true
        scope.launch {
            val screenOff = container.shizukuManager.turnScreenOff()
            if (screenOff) {
                pinState.update { it.copy(message = "错误次数过多，屏幕已关闭。") }
            } else {
                pinState.update { it.copy(message = "错误次数过多，请等待 5 分钟。") }
                NotificationHelper.notifyAlert(
                    service,
                    "PIN 错误次数过多",
                    "未检测到可用的 Shizuku，无法真正关闭屏幕；已锁定输入 5 分钟。",
                )
            }
            privacyLockRunning = false
        }
    }

    private fun scheduleAutoStop(settings: AppSettings) {
        if (settings.noMaxSessionDuration || settings.maxSessionMinutes <= 0) {
            autoStopJob?.cancel()
            autoStopJob = null
            AppLog.i("Automatic session timeout disabled")
            return
        }
        scheduleAutoStopMillis(settings.maxSessionMinutes * 60_000L)
    }

    private fun scheduleAutoStopMillis(millis: Long) {
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            if (millis > 0) delay(millis)
            if (started && !stopping) {
                AppLog.i("Safety timer expired, stopping session")
                stop(StopReason.TIMEOUT)
            }
        }
    }

    private fun restoreIfPossible(saved: SessionRecord) {
        if (saved.globalBrightnessApplied) {
            runCatching {
                container.brightnessManager.restoreGlobal(
                    saved.originalBrightness,
                    saved.originalBrightnessMode,
                )
            }
        }
    }

    private fun fail(message: String) {
        AppLog.w("Session start failed: $message")
        container.sessionState.postError(message)
        pinState.value = PinOverlayState()
    }
}
