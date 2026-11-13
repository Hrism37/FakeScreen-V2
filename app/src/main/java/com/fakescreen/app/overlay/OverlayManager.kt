package com.fakescreen.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fakescreen.app.core.AppLog
import com.fakescreen.app.core.VolumeComboDetector
import com.fakescreen.app.ui.theme.FakeScreenTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the full screen overlay window.
 *
 * The window is focusable on purpose: it must swallow the back key and it must
 * be able to hide the status/navigation bars. It is *not* a system lock screen
 * and it never prevents the power button, notification shade or emergency
 * dialer from working.
 */
class OverlayManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(WindowManager::class.java)
    private val displayManager: DisplayManager? =
        appContext.getSystemService(DisplayManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var hiding = false
    private var lostReported = false
    private val volumeDetector = VolumeComboDetector()

    var isShowing: Boolean = false
        private set

    fun show(
        pinState: StateFlow<PinOverlayState>,
        onSubmit: (String) -> Unit,
        windowBrightness: Float?,
        keepScreenOn: Boolean,
        onOverlayLost: () -> Unit,
        onPinVisibilityChanged: (Boolean) -> Unit = {},
        onVolumeShortcut: () -> Unit = {},
        volumeShortcutEnabled: Boolean = true,
    ): Boolean {
        if (isShowing) return true
        if (!Settings.canDrawOverlays(appContext)) {
            AppLog.w("Overlay permission missing")
            return false
        }

        hiding = false
        lostReported = false
        val owner = OverlayLifecycleOwner()
        owner.onCreate()

        val view = ComposeView(appContext).apply {
            setBackgroundColor(Color.BLACK)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            isFocusable = true
            isFocusableInTouchMode = true
            setContent {
                FakeScreenTheme {
                    val state by pinState.collectAsStateWithLifecycle()
                    OverlayScreen(
                        state = state,
                        onSubmit = onSubmit,
                        onRevealChanged = onPinVisibilityChanged,
                    )
                }
            }
            setOnKeyListener { _, keyCode, event ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> true
                    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (event.repeatCount > 0) {
                            false
                        } else {
                            val triggered = volumeDetector.onKey(
                                keyCode = keyCode,
                                action = event.action,
                                now = event.eventTime,
                            )
                            if (triggered && volumeShortcutEnabled) {
                                onVolumeShortcut()
                                true
                            } else {
                                false
                            }
                        }
                    }
                    else -> false
                }
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    hideSystemBars(v)
                    v.requestFocus()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    if (hiding || lostReported) return
                    lostReported = true
                    mainHandler.postDelayed({
                        if (!hiding) {
                            AppLog.w("Overlay window disappeared unexpectedly")
                            isShowing = false
                            onOverlayLost()
                        }
                    }, 400L)
                }
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "FakeScreenOverlay"
            screenBrightness = windowBrightness ?: BRIGHTNESS_NONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        return try {
            windowManager.addView(view, params)
            composeView = view
            layoutParams = params
            lifecycleOwner = owner
            isShowing = true
            mainHandler.post {
                hideSystemBars(view)
                view.requestFocus()
            }
            registerDisplayListener()
            AppLog.i("Overlay shown (brightness=${windowBrightness ?: "default"})")
            true
        } catch (t: Throwable) {
            AppLog.e("Unable to add overlay window", t)
            runCatching { windowManager.removeViewImmediate(view) }
            owner.onDestroy()
            false
        }
    }

    fun updateBrightness(value: Float?) {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        lp.screenBrightness = value ?: BRIGHTNESS_NONE
        runCatching { windowManager.updateViewLayout(view, lp) }
            .onFailure { AppLog.w("updateViewLayout failed", it) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        val lp = layoutParams ?: return
        val view = composeView ?: return
        lp.flags = if (enabled) {
            lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    fun hide() {
        if (!isShowing && composeView == null) return
        hiding = true
        isShowing = false
        displayListener?.let { listener ->
            runCatching { displayManager?.unregisterDisplayListener(listener) }
        }
        displayListener = null

        val view = composeView
        composeView = null
        layoutParams = null

        lifecycleOwner?.onPause()
        lifecycleOwner?.onStop()
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { AppLog.w("removeViewImmediate failed", it) }
        }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        AppLog.i("Overlay hidden")
    }

    private fun registerDisplayListener() {
        val manager = displayManager ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                mainHandler.post {
                    val view = composeView ?: return@post
                    if (!isShowing) return@post
                    hideSystemBars(view)
                    view.requestLayout()
                }
            }
        }
        runCatching { manager.registerDisplayListener(listener, mainHandler) }
            .onSuccess { displayListener = listener }
    }

    private fun hideSystemBars(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    companion object {
        const val BRIGHTNESS_NONE = -1f
        /** Window brightness while the PIN pad is visible in the dim mode. */
        const val READABLE_WINDOW_BRIGHTNESS = 0.35f
    }
}
