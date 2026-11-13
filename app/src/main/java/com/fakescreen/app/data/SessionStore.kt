package com.fakescreen.app.data

import android.content.Context
import android.content.SharedPreferences
import com.fakescreen.app.core.AppLog

/**
 * Crash-safe session persistence.
 *
 * SharedPreferences#commit is deliberately used instead of DataStore: the
 * write must be on disk *before* brightness changes or the overlay appears.
 * DataStore's asynchronous writes would leave a window where a process kill
 * loses the only copy of the original brightness.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): SessionRecord? {
        val raw = prefs.getString(KEY_RECORD, null) ?: return null
        val record = SessionRecord.fromJson(raw)
        if (record == null) {
            AppLog.w("Corrupt session record, clearing it")
            prefs.edit().remove(KEY_RECORD).commit()
        }
        return record
    }

    @Synchronized
    fun save(record: SessionRecord): Boolean =
        prefs.edit().putString(KEY_RECORD, record.toJson()).commit()

    @Synchronized
    fun update(transform: (SessionRecord) -> SessionRecord): SessionRecord? {
        val current = load() ?: return null
        val next = transform(current)
        save(next)
        return next
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_RECORD).commit()
    }

    @Synchronized
    fun hasRecord(): Boolean = prefs.contains(KEY_RECORD)

    @Synchronized
    fun markRecoveryAttempt(record: SessionRecord): SessionRecord =
        record.copy(recoveryAttempts = record.recoveryAttempts + 1).also { save(it) }

    companion object {
        private const val PREFS_NAME = "fakescreen_session_store"
        private const val KEY_RECORD = "active_session"
    }
}
