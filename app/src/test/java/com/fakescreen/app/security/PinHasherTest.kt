package com.fakescreen.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun derivationIsDeterministic() {
        val salt = ByteArray(16) { it.toByte() }
        val first = PinHasher.derive("1234".toCharArray(), salt, 1_000)
        val second = PinHasher.derive("1234".toCharArray(), salt, 1_000)
        assertArrayEquals(first, second)
    }

    @Test
    fun differentPinsProduceDifferentHashes() {
        val salt = ByteArray(16) { 7 }
        val first = PinHasher.derive("1234".toCharArray(), salt, 1_000)
        val second = PinHasher.derive("1235".toCharArray(), salt, 1_000)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun differentSaltsProduceDifferentHashes() {
        val first = PinHasher.derive("0000".toCharArray(), ByteArray(16) { 1 }, 1_000)
        val second = PinHasher.derive("0000".toCharArray(), ByteArray(16) { 2 }, 1_000)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun hashLengthIs256Bits() {
        val hash = PinHasher.derive("9876".toCharArray(), ByteArray(16), 1_000)
        assertTrue(hash.size == 32)
    }

    @Test
    fun callerArrayIsNotWiped() {
        val pin = "4321".toCharArray()
        PinHasher.derive(pin, ByteArray(16), 1_000)
        assertArrayEquals("4321".toCharArray(), pin)
    }
}
