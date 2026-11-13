package com.fakescreen.app.device

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.fakescreen.app.core.AppSettings
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.service.VolumeKeyAccessibilityService
import java.util.Locale

/**
 * Read-only device inspection plus a human readable compatibility report.
 *
 * Nothing here claims that a feature "works" on a device: it only reports what
 * the platform API says, so unsupported behaviour stays visible instead of
 * being faked.
 */
class DeviceCompatibility(private val context: Context) {

    data class Info(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val device: String,
        val product: String,
        val androidRelease: String,
        val sdkInt: Int,
        val securityPatch: String,
        val magicOsVersion: String?,
        val displayId: String,
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val smallestWidthDp: Int,
        val overlayGranted: Boolean,
        val writeSettingsGranted: Boolean,
        val notificationsEnabled: Boolean,
        val batteryOptimizationIgnored: Boolean,
        val volumeAccessibilityEnabled: Boolean,
        val isHonor: Boolean,
    )

    fun snapshot(): Info {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val powerManager = context.getSystemService(PowerManager::class.java)
        val ignored = try {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (t: Throwable) {
            false
        }
        return Info(
            manufacturer = Build.MANUFACTURER ?: "unknown",
            brand = Build.BRAND ?: "unknown",
            model = Build.MODEL ?: "unknown",
            device = Build.DEVICE ?: "unknown",
            product = Build.PRODUCT ?: "unknown",
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "?",
            magicOsVersion = readMagicOsVersion(),
            displayId = Build.DISPLAY ?: "?",
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            smallestWidthDp = config.smallestScreenWidthDp,
            overlayGranted = Settings.canDrawOverlays(context),
            writeSettingsGranted = Settings.System.canWrite(context),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryOptimizationIgnored = ignored,
            volumeAccessibilityEnabled = isVolumeKeyServiceEnabled(),
            isHonor = isHonorDevice(),
        )
    }

    fun isHonorDevice(): Boolean {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.ROOT)
        val brand = (Build.BRAND ?: "").lowercase(Locale.ROOT)
        return manufacturer.contains("honor") || brand.contains("honor")
    }

    fun buildReport(
        settings: AppSettings,
        shizukuStatus: String,
        pinConfigured: Boolean,
        sessionActive: Boolean,
    ): String {
        val info = snapshot()
        return buildString {
            appendLine("FakeScreen V2 · 设备兼容性报告")
            appendLine("生成时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())}")
            appendLine()
            appendLine("【设备】")
            appendLine("厂商：${info.manufacturer}")
            appendLine("品牌：${info.brand}")
            appendLine("型号：${info.model}")
            appendLine("设备代号：${info.device} / ${info.product}")
            appendLine("系统：Android ${info.androidRelease} (API ${info.sdkInt})")
            appendLine("安全补丁：${info.securityPatch}")
            appendLine("MagicOS：${info.magicOsVersion ?: "未检测到（非荣耀设备或属性不可读）"}")
            appendLine("版本号：${info.displayId}")
            appendLine(
                "屏幕：${info.widthPx}×${info.heightPx} px，" +
                    "${info.densityDpi} dpi，最小宽度 ${info.smallestWidthDp}dp"
            )
            appendLine()
            appendLine("【权限与状态】")
            appendLine("悬浮窗权限：${granted(info.overlayGranted)}")
            appendLine("修改系统设置（亮度）：${granted(info.writeSettingsGranted)}")
            appendLine("通知权限：${granted(info.notificationsEnabled)}")
            appendLine("已忽略电池优化：${granted(info.batteryOptimizationIgnored)}")
            appendLine("音量键无障碍服务：${granted(info.volumeAccessibilityEnabled)}")
            appendLine("Shizuku：$shizukuStatus")
            appendLine("PIN：${if (pinConfigured) "已设置" else "未设置"}")
            appendLine("当前会话：${if (sessionActive) "运行中" else "无"}")
            appendLine()
            appendLine("【配置】")
            appendLine("默认模式：${settings.defaultMode.title}")
            appendLine("最长会话：${settings.maxSessionMinutes} 分钟")
            appendLine("系统级亮度备用：${if (settings.allowGlobalBrightnessFallback) "开启" else "关闭"}")
            appendLine("实验性音频保活：${if (settings.experimentalAudioKeepAwake) "开启" else "关闭"}")
            appendLine()
            appendLine("【已知限制】")
            appendLine("· 悬浮窗亮度控制由系统实现，部分机型可能不生效；此时请只在必要时启用系统级亮度备用。")
            appendLine("· 本应用不会真正关闭屏幕；唯一例外是 Shizuku 可用时，错误次数过多会调用系统电源键。")
            appendLine("· 荣耀 MagicOS 的后台限制可能结束前台服务，应用通过持久化会话记录与重启恢复降低风险，但不承诺进程永不被杀。")
            appendLine("· FLAG_KEEP_SCREEN_ON 会让屏幕保持点亮，因此比真正熄屏更耗电。")
            appendLine("· UI 自动化测试需要在真机/模拟器上执行；本报告不能替代真机验证。")
        }
    }

    fun isVolumeKeyServiceEnabled(): Boolean {
        val enabled = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        } catch (t: Throwable) {
            null
        } ?: return false
        val expectedPackage = context.packageName
        val expectedClass = VolumeKeyAccessibilityService::class.java.name
        return enabled.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry)
            component != null &&
                component.packageName == expectedPackage &&
                component.className == expectedClass
        }
    }

    fun openAccessibilitySettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { AppLog.w("Cannot open accessibility settings", it) }
    }

    fun openBatteryOptimizationSettings() {
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(request) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { t -> AppLog.w("Cannot open battery settings", t) }
            }
    }

    private fun granted(value: Boolean): String = if (value) "已授权" else "未授权"

    private fun readMagicOsVersion(): String? {
        val keys = listOf(
            "ro.build.version.magic",
            "ro.build.magic_version",
            "ro.hihonor.build.version.magic",
            "ro.build.version.magicui",
        )
        for (key in keys) {
            val value = readSystemProperty(key)
            if (!value.isNullOrBlank() && value != "unknown") return value
        }
        return null
    }

    private fun readSystemProperty(key: String): String? {
        // Reflection is a greylisted hidden API; fall back to getprop when blocked.
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            val value = get.invoke(null, key) as? String
            if (!value.isNullOrBlank()) return value
        } catch (t: Throwable) {
            // ignored, fall through
        }
        return try {
            val process = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            text.ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }
}
