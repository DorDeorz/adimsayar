package com.dordeorz.adimsayar.band

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiLiveTest {

    private val phone = XiaomiPhoneInfo(33, "Test", "TR", false)

    private fun plainSession(sent: MutableList<ByteArray>, events: MutableList<BandEvent>): XiaomiSession {
        val s = XiaomiSession(null, "user", phone)
        s.send = { bytes, _ -> sent += bytes }
        s.onEvent = { events += it }
        s.startPlain()
        s.onCommand(ProtoWriter().uint(1, 1).uint(2, 5).message(3, ProtoWriter().uint(8, 1)).toByteArray())
        assertTrue(s.authenticated)
        return s
    }

    @Test
    fun notificationGoesOutAsCommand7() {
        val sent = ArrayList<ByteArray>()
        val s = plainSession(sent, ArrayList())
        s.sendNotification(BandNotice(3, "com.whatsapp", "WhatsApp", "Ali", "Selam", 0L, "0|com.whatsapp|1|null|10"))
        val cmd = ProtoMessage.parse(sent.last())!!
        assertEquals(7L, cmd.uint(1))
        assertEquals(0L, cmd.uint(2))
        val n3 = cmd.message(9)!!.message(3)!!.message(1)!!
        assertEquals("com.whatsapp", n3.string(1))
        assertEquals("WhatsApp", n3.string(2))
        assertEquals("Ali", n3.string(3))
        assertEquals("Selam", n3.string(5))
        assertEquals(3L, n3.uint(7))
        assertEquals("0|com.whatsapp|1|null|10", n3.string(12))
    }

    @Test
    fun callAndMusic() {
        val sent = ArrayList<ByteArray>()
        val s = plainSession(sent, ArrayList())
        s.sendCall("Annem", "+90 555")
        val call = ProtoMessage.parse(sent.last())!!.message(9)!!.message(3)!!.message(1)!!
        assertEquals("phone", call.string(1))
        assertEquals(1L, call.uint(8))
        s.endCall()
        val end = ProtoMessage.parse(sent.last())!!
        assertEquals(1L, end.uint(2))
        assertEquals("phone", end.message(9)!!.message(4)!!.message(1)!!.string(2))
        s.sendMusic(BandMusic(1, 40, "Şarkı", "Sanatçı", 10, 200))
        val music = ProtoMessage.parse(sent.last())!!
        assertEquals(18L, music.uint(1))
        val info = music.message(20)!!.message(1)!!
        assertEquals(1L, info.uint(1))
        assertEquals("Şarkı", info.string(4))
        assertEquals(200L, info.uint(7))
    }

    @Test
    fun bandEventsAreReported() {
        val events = ArrayList<BandEvent>()
        plainSession(ArrayList(), events).run {
            onCommand(ProtoWriter().uint(1, 18).uint(2, 2).message(20, ProtoWriter().message(2, ProtoWriter().uint(1, 4))).toByteArray())
            onCommand(ProtoWriter().uint(1, 7).uint(2, 2).toByteArray())
            val dismiss = ProtoWriter().message(1, ProtoWriter().uint(1, 5)).message(1, ProtoWriter().uint(1, 6))
            onCommand(ProtoWriter().uint(1, 7).uint(2, 1).message(9, ProtoWriter().message(4, dismiss)).toByteArray())
        }
        assertEquals(BandEvent.NEXT, (events[0] as BandEvent.MusicButton).button)
        assertEquals(BandEvent.RejectCall, events[1])
        assertEquals(listOf(5, 6), (events[2] as BandEvent.Dismiss).ids)
    }

    @Test
    fun batteryIsAskedAndRead() {
        val sent = ArrayList<ByteArray>()
        val s = plainSession(sent, ArrayList())
        assertTrue(sent.any { ProtoMessage.parse(it)!!.let { c -> c.uint(1) == 2L && c.uint(2) == 78L } })
        val levels = ArrayList<Pair<Int, Boolean?>>()
        s.onBattery = { level, charging -> levels += level to charging }
        val battery = ProtoWriter().uint(1, 64).uint(2, 2)
        s.onCommand(ProtoWriter().uint(1, 2).uint(2, 1).message(4, ProtoWriter().message(2, ProtoWriter().message(1, battery))).toByteArray())
        val state = ProtoWriter().bool(1, true).uint(2, 15).bool(3, true).bool(4, false)
        s.onCommand(ProtoWriter().uint(1, 2).uint(2, 78).message(4, ProtoWriter().message(48, state)).toByteArray())
        assertEquals(listOf(64 to false, 15 to true), levels)
        // A device state without a level asks for the battery instead.
        sent.clear()
        s.onCommand(ProtoWriter().uint(1, 2).uint(2, 78).message(4, ProtoWriter().message(48, ProtoWriter().bool(1, false))).toByteArray())
        val ask = ProtoMessage.parse(sent.single())!!
        assertEquals(2L, ask.uint(1))
        assertEquals(1L, ask.uint(2))
    }

    @Test
    fun findPhoneAndFindBand() {
        val sent = ArrayList<ByteArray>()
        val events = ArrayList<BandEvent>()
        val s = plainSession(sent, events)
        s.onCommand(ProtoWriter().uint(1, 2).uint(2, 17).message(4, ProtoWriter().uint(5, 0)).toByteArray())
        s.onCommand(ProtoWriter().uint(1, 2).uint(2, 17).message(4, ProtoWriter().uint(5, 1)).toByteArray())
        s.onCommand(ProtoWriter().uint(1, 2).uint(2, 18).message(4, ProtoWriter().uint(5, 1)).toByteArray())
        assertTrue((events[0] as BandEvent.FindPhone).start)
        assertTrue(!(events[1] as BandEvent.FindPhone).start)
        assertEquals(BandEvent.BandFound, events[2])
        s.findBand(true)
        val find = ProtoMessage.parse(sent.last())!!
        assertEquals(18L, find.uint(2))
        assertEquals(0L, find.message(4)!!.uint(5))
        s.findPhoneStopped()
        val stop = ProtoMessage.parse(sent.last())!!
        assertEquals(17L, stop.uint(2))
        assertEquals(1L, stop.message(4)!!.uint(5))
    }
}
