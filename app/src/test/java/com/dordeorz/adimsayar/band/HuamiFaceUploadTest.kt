package com.dordeorz.adimsayar.band

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class HuamiFaceUploadTest {

    private val face = "UIHH".toByteArray() + byteArrayOf(1) + ByteArray(5000) { (it % 251).toByte() }

    private fun reply(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun wholeFileGoesOverInChunks() {
        val control = ArrayList<ByteArray>()
        val config = ArrayList<ByteArray>()
        val data = ByteArrayOutputStream()
        val upload = HuamiFaceUpload(face, packetSize = 244)
        upload.writeControl = { control += it }
        upload.writeConfig = { config += it }
        upload.writeData = { data.write(it) }
        upload.start()
        assertArrayEquals(reply(0xd0), control.last())

        upload.onControl(reply(0x10, 0xd0, 0x01, 0x00, 0x00, 0x08)) // chunk 2048
        val info = control.last()
        assertEquals(0xd2.toByte(), info[0])
        assertEquals(8.toByte(), info[1])
        assertEquals(face.size, (info[2].toInt() and 0xff) or ((info[3].toInt() and 0xff) shl 8))
        assertEquals(0x39.toByte(), config.single()[0])

        upload.onControl(reply(0x10, 0xd2, 0x01))
        assertArrayEquals(reply(0xd3, 0x01), control.last())
        upload.onControl(reply(0x10, 0xd3, 0x01))
        assertEquals(2048, data.size())
        upload.onControl(reply(0x10, 0xd4, 0x00, 0x08, 0x00, 0x00))
        upload.onControl(reply(0x10, 0xd4, 0x00, 0x10, 0x00, 0x00))
        assertArrayEquals(face, data.toByteArray())
        upload.onControl(reply(0x10, 0xd4, face.size and 0xff, face.size shr 8, 0x00, 0x00))
        assertArrayEquals(reply(0xd5), control.last())
        upload.onControl(reply(0x10, 0xd5, 0x01))
        assertArrayEquals(reply(0xd6), control.last())
        assertFalse(upload.done)
        upload.onControl(reply(0x10, 0xd6, 0x01))
        assertTrue(upload.done)
    }

    @Test
    fun fullBandIsReported() {
        val upload = HuamiFaceUpload(face, packetSize = 20)
        upload.onControl(reply(0x10, 0xd3, 0x47))
        assertEquals(HuamiFaceUpload.Failure.NoSpace, upload.failure)
    }

    @Test
    fun onlyMiBand6FacesAreTaken() {
        assertTrue(HuamiFaceUpload.isFace(face))
        assertFalse(HuamiFaceUpload.isFace("HMDIAL".toByteArray() + ByteArray(40)))
        assertFalse(HuamiFaceUpload.isFace(ByteArray(64)))
    }
}
