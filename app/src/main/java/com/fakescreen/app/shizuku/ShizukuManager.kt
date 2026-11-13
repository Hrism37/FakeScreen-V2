package com.fakescreen.app.shizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import com.fakescreen.app.BuildConfig
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Optional Shizuku integration.
 *
 * Shizuku 13 removed the old `newProcess` helper, so privileged work goes
 * through the documented user-service API: a tiny AIDL service that runs with
 * the shell UID and can press the power key. It is used *only* after too many
 * wrong PINs, to really switch the display off.
 *
 * The core app (overlay, PIN, brightness) works without Shizuku.
 */
class ShizukuManager(private val context: Context) {

    enum class Status(val label: String) {
        NOT_INSTALLED("未安装"),
        NOT_RUNNING("服务未运行"),
        NO_PERMISSION("未授权"),
        READY("已就绪"),
        ERROR("不可用"),
    }

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(Status.NOT_INSTALLED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _detail = MutableStateFlow("")
    val detail: StateFlow<String> = _detail.asStateFlow()

    private var userService: IScreenOffService? = null
    private var pendingBind: CompletableDeferred<IScreenOffService?>? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = try {
                binder?.let { IScreenOffService.Stub.asInterface(it) }
            } catch (t: Throwable) {
                AppLog.w("Shizuku user service interface failed", t)
                null
            }
            userService = service
            pendingBind?.complete(service)
            pendingBind = null
            if (service != null) {
                AppLog.i("Shizuku user service connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            pendingBind?.complete(null)
            pendingBind = null
            AppLog.w("Shizuku user service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            userService = null
            pendingBind?.complete(null)
            pendingBind = null
            refresh()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        refresh()
    }
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == AppConstants.SHIZUKU_REQUEST_CODE) {
                AppLog.i("Shizuku permission result: $grantResult")
                refresh()
            }
        }

    init {
        runCatching {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }.onFailure { AppLog.w("Shizuku listeners unavailable: ${it.javaClass.simpleName}") }
        refresh()
    }

    fun isInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(AppConstants.SHIZUKU_PACKAGE, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun isReady(): Boolean = _status.value == Status.READY

    fun refresh() {
        _status.value = try {
            when {
                !isInstalled() -> Status.NOT_INSTALLED
                !Shizuku.pingBinder() -> Status.NOT_RUNNING
                Shizuku.isPreV11() -> Status.ERROR
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
                else -> Status.NO_PERMISSION
            }
        } catch (t: Throwable) {
            AppLog.w("Shizuku refresh failed", t)
            Status.ERROR
        }
        if (_status.value != Status.READY) {
            userService = null
        }
        _detail.value = when (_status.value) {
            Status.NOT_INSTALLED -> "安装 Shizuku 后可启用“错误过多自动熄屏”等增强能力。"
            Status.NOT_RUNNING -> "Shizuku 已安装但服务未启动，请先通过无线调试启动 Shizuku。"
            Status.NO_PERMISSION -> "Shizuku 正在运行，需要授权 FakeScreen 后才能使用增强能力。"
            Status.READY -> "增强能力可用：PIN 错误次数过多时可通过 Shizuku 用户服务真正关闭屏幕。"
            Status.ERROR -> "当前 Shizuku 版本或状态不受支持。"
        }
    }

    fun requestPermission(activity: Activity) {
        if (!isInstalled() || !runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            refresh()
            return
        }
        runCatching {
            Shizuku.requestPermission(AppConstants.SHIZUKU_REQUEST_CODE)
        }.onFailure {
            AppLog.w("Shizuku permission request failed", it)
        }
    }

    /**
     * Shizuku delivers the result through its own listener; this hook only
     * refreshes the cached state.
     */
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        refresh()
    }

    fun openShizukuApp(): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(AppConstants.SHIZUKU_PACKAGE)
            ?: return false
        return runCatching {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Really powers the display off (same effect as the power button).
     * Returns false when Shizuku is missing, not authorised, or the user
     * service could not start, so callers can degrade honestly.
     */
    suspend fun turnScreenOff(): Boolean {
        if (!isReady()) return false
        val service = userService ?: bindAndAwait()
        if (service == null) {
            AppLog.w("Shizuku user service unavailable")
            return false
        }
        val result = withContext(Dispatchers.IO) {
            runCatching { service.turnScreenOff() }.getOrDefault(false)
        }
        AppLog.i("Shizuku screen off: $result")
        return result
    }

    private suspend fun bindAndAwait(): IScreenOffService? {
        val inFlight = pendingBind
        if (inFlight != null) {
            return withTimeoutOrNull(BIND_TIMEOUT_MS) { inFlight.await() }
        }
        val deferred = CompletableDeferred<IScreenOffService?>()
        pendingBind = deferred

        val started = withContext(Dispatchers.Main) {
            runCatching {
                Shizuku.bindUserService(buildServiceArgs(), connection)
                true
            }.getOrElse {
                AppLog.w("Shizuku bindUserService failed", it)
                false
            }
        }
        if (!started) {
            pendingBind = null
            return null
        }
        return withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
    }

    private fun buildServiceArgs(): Shizuku.UserServiceArgs {
        serviceArgs?.let { return it }
        val debug = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ScreenOffUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("fakescreen")
            .debuggable(debug)
            .version(BuildConfig.VERSION_CODE)
        serviceArgs = args
        return args
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 6_000L
    }
}
