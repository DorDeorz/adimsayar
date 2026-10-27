package com.dordeorz.adimsayar.data

import com.dordeorz.adimsayar.band.XiaomiWatchface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnFacesTest {

    @Test
    fun idIsReplaced() {
        val bytes = ByteArray(400)
        bytes[0] = 0x5A
        bytes[1] = 0xA5.toByte()
        "167210065".toByteArray().copyInto(bytes, 0x28)
        "FitWren".toByteArray().copyInto(bytes, 0x68)
        val patched = OwnFaces.withId(bytes, "266290003")!!
        assertEquals("266290003", XiaomiWatchface.parse(patched)!!.id)
        assertEquals("167210065", XiaomiWatchface.parse(bytes)!!.id)
        assertNull(OwnFaces.withId(ByteArray(10), "1"))
    }

    @Test
    fun previewSizeTellsTheDevice() {
        val bytes = ByteArray(400)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0x20, 0x100)
            .putShort(0x104, 122)
            .putShort(0x106, 310)
        assertEquals(122 to 310, XiaomiWatchface.previewSize(bytes))
        assertNull(XiaomiWatchface.previewSize(ByteArray(400)))
    }
}
