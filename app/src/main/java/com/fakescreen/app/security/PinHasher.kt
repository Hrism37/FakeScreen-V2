package com.fakescreen.app.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom

/**
 * Password based key derivation for the FakeScreen PIN.
 *
 * PBKDF2-HMAC-SHA256 with a per-install random salt. Only the derived hash is
 * stored, and the stored record itself is encrypted with a non-exportable
 * Android Keystore key (see [KeystoreCipher]).
 */
object PinHasher {

    /** OWASP 2023 recommends 600k for PBKDF2-HMAC-SHA256; 210k keeps unlock < 0.5s on tablets. */
    const val ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val HASH_BITS = 256

    private val secureRandom = SecureRandom()

    fun randomSalt(size: Int = SALT_BYTES): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    /**
     * Derives the PIN hash. The caller's array is copied, so the caller stays in
     * control of wiping its own copy.
     */
    fun derive(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val workingCopy = pin.copyOf()
        val spec = PBEKeySpec(workingCopy, salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            workingCopy.fill('\u0000')
        }
    }
}
