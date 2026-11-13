package com.fakescreen.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakescreen.app.ui.screens.CompatibilityScreen
import com.fakescreen.app.ui.screens.HomeScreen
import com.fakescreen.app.ui.screens.ModeSelectScreen
import com.fakescreen.app.ui.screens.PinSetupScreen
import com.fakescreen.app.ui.screens.SettingsScreen
import com.fakescreen.app.ui.theme.Ink

private const val SCREEN_HOME = "home"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_MODES = "modes"
private const val SCREEN_PIN = "pin"
private const val SCREEN_COMPAT = "compat"

@Composable
fun FakeScreenRoot(vm: MainViewModel, activity: MainActivity) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var modesReturn by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var pinReturn by rememberSaveable { mutableStateOf(SCREEN_HOME) }

    val session by vm.session.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val pinLength by vm.pinLength.collectAsStateWithLifecycle()
    val shizukuStatus by vm.shizukuStatus.collectAsStateWithLifecycle()
    val shizukuDetail by vm.shizukuDetail.collectAsStateWithLifecycle()
    val deviceInfo by vm.deviceInfo.collectAsStateWithLifecycle()
    val lastError by vm.lastError.collectAsStateWithLifecycle()
    val recoveryMessage by vm.recoveryMessage.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshAll() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refreshAll() }

    val openOverlaySettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching { overlayLauncher.launch(intent) }
            .onFailure {
                runCatching { overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
            }
    }

    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
        }
    }

    val openBattery: () -> Unit = { vm.openBatteryOptimizationSettings() }
    val openWriteSettings: () -> Unit = { vm.openWriteSettingsPermission(context) }

    val openShizuku: () -> Unit = {
        if (!vm.openShizukuApp()) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                )
            }
        }
    }

    val startSession: () -> Unit = {
        when {
            !hasPin -> {
                pinReturn = SCREEN_HOME
                screen = SCREEN_PIN
            }

            !deviceInfo.overlayGranted -> {
                openOverlaySettings()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !deviceInfo.notificationsEnabled -> {
                requestNotifications()
            }

            else -> {
                vm.startSession()
            }
        }
    }

    BackHandler(enabled = screen != SCREEN_HOME) {
        screen = when (screen) {
            SCREEN_MODES -> modesReturn
            SCREEN_PIN -> pinReturn
            SCREEN_COMPAT -> SCREEN_SETTINGS
            else -> SCREEN_HOME
        }
    }

    LaunchedEffect(lastError) {
        val message = lastError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.clearError()
    }

    LaunchedEffect(recoveryMessage) {
        val message = recoveryMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(message, duration = SnackbarDuration.Long)
        vm.dismissRecoveryMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        when (screen) {
            SCREEN_SETTINGS -> SettingsScreen(
                settings = settings,
                hasPin = hasPin,
                shizukuStatus = shizukuStatus,
                shizukuDetail = shizukuDetail,
                deviceInfo = deviceInfo,
                onBack = { screen = SCREEN_HOME },
                onOpenModes = {
                    modesReturn = SCREEN_SETTINGS
                    screen = SCREEN_MODES
                },
                onChangePin = {
                    pinReturn = SCREEN_SETTINGS
                    screen = SCREEN_PIN
                },
                onMaxSessionChange = vm::setMaxSessionMinutes,
                onAutoRestoreChange = vm::setAutoRestore,
                onVolumeShortcutChange = vm::setAllowVolumeKeyShortcut,
                onOpenAccessibility = vm::openAccessibilitySettings,
                onExperimentalAudioChange = vm::setExperimentalAudio,
                onGlobalBrightnessChange = vm::setAllowGlobalBrightness,
                onOpenOverlayPermission = openOverlaySettings,
                onOpenNotificationPermission = requestNotifications,
                onOpenWriteSettings = openWriteSettings,
                onOpenBattery = openBattery,
                onRequestShizuku = { vm.requestShizukuPermission(activity) },
                onOpenShizukuApp = openShizuku,
                onOpenCompatibility = {
                    vm.refreshReportAsync()
                    screen = SCREEN_COMPAT
                },
            )

            SCREEN_MODES -> ModeSelectScreen(
                current = settings.defaultMode,
                onSelect = vm::setDefaultMode,
                onBack = { screen = modesReturn },
            )

            SCREEN_PIN -> PinSetupScreen(
                changeMode = hasPin,
                knownPinLength = pinLength,
                onBack = { screen = pinReturn },
                onFinished = {
                    screen = pinReturn
                    vm.refreshAll()
                },
                onSetPin = vm::setPin,
                onVerifyPin = vm::verifyPinForChange,
                onChangePin = vm::changePin,
            )

            SCREEN_COMPAT -> CompatibilityScreen(
                report = report,
                onBack = { screen = SCREEN_SETTINGS },
            )

            else -> HomeScreen(
                session = session,
                settings = settings,
                hasPin = hasPin,
                shizukuStatus = shizukuStatus,
                deviceInfo = deviceInfo,
                onStart = startSession,
                onOpenSettings = { screen = SCREEN_SETTINGS },
                onOpenModes = {
                    modesReturn = SCREEN_HOME
                    screen = SCREEN_MODES
                },
                onOpenPin = {
                    pinReturn = SCREEN_HOME
                    screen = SCREEN_PIN
                },
                onOpenShizuku = { screen = SCREEN_SETTINGS },
                onOpenOverlayPermission = openOverlaySettings,
                onOpenNotificationPermission = requestNotifications,
                onOpenBatterySettings = openBattery,
                onOpenWriteSettings = openWriteSettings,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}
