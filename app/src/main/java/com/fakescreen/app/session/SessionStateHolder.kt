package com.fakescreen.app.session

import com.fakescreen.app.core.ScreenMode
import com.fakescreen.app.data.SessionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/** UI-facing snapshot of the current session; the single source of truth for the Activity. */
data class SessionUiState(
    val active: Boolean = false,
    val serviceRunning: Boolean = false,
    val sessionId: String? = null,
    val mode: ScreenMode = ScreenMode.PURE_BLACK,
    val startedAt: Long = 0L,
    val brightnessSummary: String = "",
    val globalBrightnessApplied: Boolean = false,
    val stopping: Boolean = false,
)

class SessionStateHolder {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _state.update { it.copy(serviceRunning = running) }
    }

    fun setActive(record: SessionRecord) {
        _state.update {
            it.copy(
                active = true,
                serviceRunning = true,
                sessionId = record.id,
                mode = record.mode,
                startedAt = record.startedAt,
                brightnessSummary = describe(record),
                globalBrightnessApplied = record.globalBrightnessApplied,
                stopping = false,
            )
        }
    }

    fun setStopping() {
        _state.update { it.copy(stopping = true) }
    }

    fun reset() {
        _state.update {
            SessionUiState(serviceRunning = it.serviceRunning)
        }
    }

    fun postError(message: String) {
        _lastError.value = message
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun describe(record: SessionRecord): String {
        val parts = mutableListOf<String>()
        record.appliedWindowBrightness?.let {
            parts += "窗口亮度 ${(it * 100f).roundToInt()}%"
        }
        if (record.globalBrightnessApplied) {
            parts += "系统亮度已调低 → ${record.appliedGlobalBrightness}/255"
        } else {
            parts += "系统亮度未修改"
        }
        return parts.joinToString(" · ")
    }
}
