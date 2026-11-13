package com.fakescreen.app.data

import com.fakescreen.app.core.ScreenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRecordTest {

    @Test
    fun jsonRoundTripPreservesEverything() {
        val original = SessionRecord(
            id = "abc",
            mode = ScreenMode.DIM,
            startedAt = 123456789L,
            originalBrightness = 200,
            originalBrightnessMode = 1,
            appliedWindowBrightness = 0.01f,
            globalBrightnessApplied = true,
            appliedGlobalBrightness = 4,
            appliedGlobalMode = 0,
        )
        val restored = SessionRecord.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun unknownWindowBrightnessRoundTripsAsNull() {
        val original = SessionRecord.newSession(ScreenMode.PURE_BLACK, -1, -1)
        val restored = SessionRecord.fromJson(original.toJson())
        assertNotNull(restored)
        assertNull(restored!!.appliedWindowBrightness)
        assertEquals(-1, restored.originalBrightness)
    }

    @Test
    fun corruptJsonIsRejected() {
        assertNull(SessionRecord.fromJson("not json"))
        assertNull(SessionRecord.fromJson(null))
    }

    @Test
    fun ageIsNeverNegative() {
        val record = SessionRecord.newSession(ScreenMode.PURE_BLACK, 100, 0)
        assertEquals(0L, record.ageMillis(now = record.startedAt - 5_000))
    }
}
