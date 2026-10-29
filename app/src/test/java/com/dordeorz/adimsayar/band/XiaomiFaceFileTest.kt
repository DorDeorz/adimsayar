package com.dordeorz.adimsayar.band

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class XiaomiFaceFileTest {

    private val compiled = File("src/main/assets/designer/template.bin").readBytes()

    @Test
    fun unchangedFileIsWrittenTheSame() {
        val face = XiaomiFaceFile.parse(compiled)!!
        assertEquals(1, face.images)
        assertEquals(10, face.lists)
        assertArrayEquals(compiled, face.bytes())
    }

    @Test
    fun picturesSurviveBeingWrittenAgain() {
        val face = XiaomiFaceFile.parse(compiled)!!
        val frames = face.list(0)
        val background = face.image(0)
        face.setList(0, frames)
        face.setImage(0, background)
        val again = XiaomiFaceFile.parse(face.bytes())!!
        assertArrayEquals(frames[3].pixels, again.list(0)[3].pixels)
        assertArrayEquals(background.pixels, again.image(0).pixels)
        assertNotNull(XiaomiWatchface.parse(face.bytes()))
    }

    @Test
    fun widgetsMove() {
        val face = XiaomiFaceFile.parse(compiled)!!
        assertEquals(13, face.widgets)
        face.moveWidget(1, 40)
        val moved = face.bytes()
        // hour: record of widget 1 at 0x350, y 34 -> 74
        assertEquals(74, (moved[0x356].toInt() and 0xff) or (moved[0x357].toInt() shl 8))
        assertEquals(compiled.size, moved.size)
    }

    @Test
    fun runLengthCodingRoundTrips() {
        val raw = ByteArray(4 * 300) { i -> if (i in 400..479) (i % 7).toByte() else 9 }
        val coded = XiaomiFaceFile.rleEncode(raw, 4)
        assertArrayEquals(raw, XiaomiFaceFile.rleDecode(coded, 0, coded.size))
    }

    @Test
    fun facesWithAnAlwaysOnScreenAreLeftAlone() {
        assertNull(XiaomiFaceFile.parse(File("src/main/assets/faces/fitwren_dal_gece.bin").readBytes()))
    }

    @Test
    fun otherFilesAreRefused() {
        assertNull(XiaomiFaceFile.parse(ByteArray(64)))
    }
}
