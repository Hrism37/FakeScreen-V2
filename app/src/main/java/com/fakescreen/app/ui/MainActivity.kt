package com.fakescreen.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.fakescreen.app.ui.theme.FakeScreenTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Protect the PIN entry from screen capture / screen recording.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            FakeScreenTheme {
                FakeScreenRoot(vm = viewModel, activity = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        viewModel.onShizukuPermissionResult(requestCode, permissions, grantResults)
    }
}
