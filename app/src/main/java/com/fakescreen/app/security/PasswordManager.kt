package com.fakescreen.app.security

import android.content.Context
import com.fakescreen.app.core.AppConstants
import com.fakescreen.app.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.max

/** Result of a PIN verification attempt. */
sealed interface VerifyResult {
    data object Success : VerifyResult
    data object NotConfigured : VerifyResult
    data object InvalidFormat : VerifyResult
    data class Wrong(
        val failures: Int,
        val lockMillis: Long,
        val privacyLock: Boolean,
    ) : VerifyResult

    data class Locked(
        val remainingMillis: Long,
        val failures: Int,
    ) : VerifyResult
}

/**
 * Stores and verifies the FakeScreen PIN.
 *
 * Security properties:
 *  - only a PBKDF2-HMAC-SHA256 hash is stored (never the PIN);
 *  - the whole record is AES/GCM encrypted with a hardware backed Keystore key;
 *  - lockout state is persisted inside the same encrypted record, so clearing
 *    app-visible settings cannot reset the attempt counter;
 *  - comparisons are constant time.
 */
class PasswordManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher()
    private val mutex = Mutex()

    suspend fun hasPin(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { readRecord() != null }
    }

    suspend fun pinLength(): Int = withContext(Dispatchers.IO) {
        mutex.withLock { readRecord()?.pinLength ?: AppConstants.PIN_MIN_LENGTH }
    }

    suspend fun failedAttempts(): Int = withContext(Dispatchers.IO) {
        mutex.withLock { readRecord()?.failures ?: 0 }
    }

    suspend fun lockRemainingMillis(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = readRecord() ?: return@withLock 0L
            (record.lockedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }

    /** First time setup. Refuses to overwrite an existing PIN. */
    suspend fun setInitialPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFormat(pin)) return@withContext false
        mutex.withLock {
            if (readRecord() != null) return@withLock false
            writeRecord(newRecord(pin))
            true
        }
    }

    suspend fun verify(pin: String): VerifyResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = readRecord() ?: return@withLock VerifyResult.NotConfigured
            val now = System.currentTimeMillis()
            if (record.lockedUntil > now) {
                return@withLock VerifyResult.Locked(record.lockedUntil - now, record.failures)
            }
            if (isValidFormat(pin) && matches(pin, record)) {
                writeRecord(record.copy(failures = 0, lockedUntil = 0L))
                VerifyResult.Success
            } else {
                val failures = record.failures + 1
                val lockMillis = LockoutPolicy.lockMillisForFailures(failures)
                val lockedUntil = if (lockMillis > 0) now + lockMillis else 0L
                writeRecord(record.copy(failures = failures, lockedUntil = lockedUntil))
                VerifyResult.Wrong(
                    failures = failures,
                    lockMillis = lockMillis,
                    privacyLock = LockoutPolicy.shouldTriggerPrivacyLock(failures),
                )
            }
        }
    }

    /**
     * Changes the PIN after verifying the current one. Applies exactly the same
     * lockout policy as the overlay keypad.
     */
    suspend fun changePin(currentPin: String, newPin: String): VerifyResult = withContext(Dispatchers.IO) {
        if (!isValidFormat(newPin)) {
            return@withContext VerifyResult.InvalidFormat
        }
        mutex.withLock {
            val record = readRecord() ?: return@withLock VerifyResult.NotConfigured
            val now = System.currentTimeMillis()
            if (record.lockedUntil > now) {
                return@withLock VerifyResult.Locked(record.lockedUntil - now, record.failures)
            }
            if (!isValidFormat(currentPin) || !matches(currentPin, record)) {
                val failures = record.failures + 1
                val lockMillis = LockoutPolicy.lockMillisForFailures(failures)
                val lockedUntil = if (lockMillis > 0) now + lockMillis else 0L
                writeRecord(record.copy(failures = failures, lockedUntil = lockedUntil))
                return@withLock VerifyResult.Wrong(
                    failures = failures,
                    lockMillis = lockMillis,
                    privacyLock = LockoutPolicy.shouldTriggerPrivacyLock(failures),
                )
            }
            writeRecord(newRecord(newPin))
            VerifyResult.Success
        }
    }

    /** Used by tests / developer tooling only; not wired to any UI. */
    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit().remove(KEY_RECORD).commit()
            cipher.deleteKey()
        }
    }

    fun isValidFormat(pin: String): Boolean =
        pin.length in AppConstants.PIN_MIN_LENGTH..AppConstants.PIN_MAX_LENGTH &&
            pin.all { it in '0'..'9' }

    private fun matches(pin: String, record: PinRecord): Boolean {
        val candidate = PinHasher.derive(pin.toCharArray(), record.salt, record.iterations)
        return try {
            MessageDigest.isEqual(candidate, record.hash)
        } finally {
            candidate.fill(0)
        }
    }

    private fun newRecord(pin: String): PinRecord {
        val salt = PinHasher.randomSalt()
        val hash = PinHasher.derive(pin.toCharArray(), salt, PinHasher.ITERATIONS)
        return PinRecord(
            salt = salt,
            hash = hash,
            iterations = PinHasher.ITERATIONS,
            pinLength = pin.length,
            failures = 0,
            lockedUntil = 0L,
        )
    }

    private fun readRecord(): PinRecord? {
        val encrypted = prefs.getString(KEY_RECORD, null) ?: return null
        val plain = cipher.decrypt(encrypted)
        if (plain == null) {
            AppLog.w("PIN record exists but cannot be decrypted; treating as not configured")
            return null
        }
        return try {
            val json = JSONObject(String(plain, Charsets.UTF_8))
            PinRecord(
                salt = android.util.Base64.decode(json.getString("salt"), android.util.Base64.NO_WRAP),
                hash = android.util.Base64.decode(json.getString("hash"), android.util.Base64.NO_WRAP),
                iterations = json.getInt("iterations"),
                pinLength = json.getInt("pinLength"),
                failures = json.optInt("failures", 0),
                lockedUntil = json.optLong("lockedUntil", 0L),
            )
        } catch (t: Throwable) {
            AppLog.e("Corrupt PIN record", t)
            null
        } finally {
            plain.fill(0)
        }
    }

    private fun writeRecord(record: PinRecord) {
        val json = JSONObject().apply {
            put("v", 1)
            put("salt", android.util.Base64.encodeToString(record.salt, android.util.Base64.NO_WRAP))
            put("hash", android.util.Base64.encodeToString(record.hash, android.util.Base64.NO_WRAP))
            put("iterations", record.iterations)
            put("pinLength", record.pinLength)
            put("failures", record.failures)
            put("lockedUntil", record.lockedUntil)
        }
        val encrypted = cipher.encrypt(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_RECORD, encrypted).commit()
    }

    private data class PinRecord(
        val salt: ByteArray,
        val hash: ByteArray,
        val iterations: Int,
        val pinLength: Int,
        val failures: Int,
        val lockedUntil: Long,
    )

    companion object {
        private const val PREFS_NAME = "fakescreen_pin_store"
        private const val KEY_RECORD = "pin_record_v1"

        fun attemptsUntilLock(failures: Int): Int = max(0, LockoutPolicy.PRIVACY_LOCK_THRESHOLD - failures)
    }
}
