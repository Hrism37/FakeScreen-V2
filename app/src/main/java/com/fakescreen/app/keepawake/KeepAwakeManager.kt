package com.fakescreen.app.keepawake

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import com.fakescreen.app.core.AppLog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optional "keep the display awake" helpers.
 *
 * The primary mechanism is a window flag (FLAG_KEEP_SCREEN_ON) set by
 * [com.fakescreen.app.overlay.OverlayManager]; no wake lock is needed for the
 * display itself. The experimental silent-audio mode is opt-in, clearly marked
 * in the UI, and is stopped the moment the session ends.
 */
class KeepAwakeManager(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var silentFile: File? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var started = false

    fun start(experimentalAudio: Boolean) {
        started = true
        if (!experimentalAudio) return
        startSilentAudio()
    }

    fun stop() {
        started = false
        runCatching {
            player?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }.onFailure { AppLog.w("Stopping silent audio failed", it) }
        player = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        silentFile?.let { runCatching { it.delete() } }
        silentFile = null
    }

    private fun startSilentAudio() {
        if (started.not() || player != null) return
        try {
            val file = File(appContext.cacheDir, "fakescreen_silence.wav")
            writeSilentWav(file)
            silentFile = file

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            // Keeps the CPU awake while the (silent) media stream is "playing".
            runCatching {
                mp.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
            }.onFailure { AppLog.w("setWakeMode failed", it) }
            mp.prepare()
            mp.start()
            player = mp
            AppLog.i("Experimental silent-audio keep-awake started")
        } catch (t: Throwable) {
            AppLog.w("Experimental silent-audio keep-awake unavailable", t)
            stop()
        }
    }

    private fun writeSilentWav(file: File) {
        val sampleRate = 8_000
        val channels = 1
        val bitsPerSample = 16
        val dataSize = sampleRate * channels * bitsPerSample / 8
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * bitsPerSample / 8)
        buffer.putShort((channels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        file.writeBytes(buffer.array())
    }
}
