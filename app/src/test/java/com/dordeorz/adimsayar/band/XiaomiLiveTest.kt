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

    @Test
    fun quickReplies() {
        val sent = ArrayList<ByteArray>()
        val events = ArrayList<BandEvent>()
        val s = plainSession(sent, events)
        assertTrue(sent.any { ProtoMessage.parse(it)!!.let { c -> c.uint(1) == 7L && c.uint(2) == 9L } })
        var got: Triple<List<String>, Int, Int>? = null
        s.onReplies = { list, min, max -> got = Triple(list, min, max) }
        val canned = ProtoWriter().uint(1, 3).string(2, "Sonra ararım").string(2, "Toplantıdayım").uint(3, 8)
        s.onCommand(ProtoWriter().uint(1, 7).uint(2, 9).message(9, ProtoWriter().message(9, canned)).toByteArray())
        assertEquals(Triple(listOf("Sonra ararım", "Toplantıdayım"), 3, 8), got)

        s.setReplies(listOf("Bir", "", "İki"), 3, 8)
        val set = ProtoMessage.parse(sent.last())!!
        assertEquals(12L, set.uint(2))
        assertEquals(listOf("Bir", "İki", "-"), set.message(9)!!.message(9)!!.strings(2))

        val reply = ProtoWriter().uint(1, 1).string(2, "Sonra ararım").uint(3, 1).string(4, "+905551112233")
        s.onCommand(ProtoWriter().uint(1, 7).uint(2, 13).message(9, ProtoWriter().message(12, reply)).toByteArray())
        val event = events.last() as BandEvent.CallReply
        assertEquals("+905551112233", event.number)
        assertEquals("Sonra ararım", event.message)

        s.replySent(true)
        val ack = ProtoMessage.parse(sent.last())!!
        assertEquals(14L, ack.uint(2))
        assertEquals(0L, ack.message(9)!!.uint(13))
    }

    @Test
    fun weatherGoesOutOnce() {
        val sent = ArrayList<ByteArray>()
        val s = plainSession(sent, ArrayList())
        s.onCommand(ProtoWriter().uint(1, 10).uint(2, 5).message(12, ProtoWriter().message(4, ProtoWriter())).toByteArray())
        val report = BandWeatherReport(
            "İstanbul", "2026-10-26T01:00:00+03:00", 7, 14, 80, 3, 200, 2, 1012f,
            listOf(BandWeatherReport.Day(11, 17, 7, "2026-10-26T07:20:00+03:00", "2026-10-26T18:10:00+03:00")),
            listOf(BandWeatherReport.Hour(14, 7, 3, 200)),
        )
        sent.clear()
        s.sendWeather(report)
        assertEquals(listOf(7L, 0L, 1L, 2L), sent.map { ProtoMessage.parse(it)!!.uint(2) })
        val current = ProtoMessage.parse(sent[1])!!.message(12)!!.message(1)!!
        assertEquals(7L, current.uint(2))
        assertEquals("İstanbul", current.message(1)!!.string(3))
        assertEquals(report.key, current.message(1)!!.string(4))
        sent.clear()
        s.sendWeather(report)
        assertEquals(listOf(0L, 1L, 2L), sent.map { ProtoMessage.parse(it)!!.uint(2) })
    }

    @Test
    fun weatherCodesAndWind() {
        assertEquals(0, XiaomiWeather.fromWmo(0))
        assertEquals(2, XiaomiWeather.fromWmo(3))
        assertEquals(4, XiaomiWeather.fromWmo(95))
        assertEquals(0, XiaomiWeather.beaufort(0.5))
        assertEquals(3, XiaomiWeather.beaufort(15.0))
        assertEquals(12, XiaomiWeather.beaufort(130.0))
    }

    @Test
    fun alarmsAndCalendar() {
        val sent = ArrayList<ByteArray>()
        val s = plainSession(sent, ArrayList())
        assertTrue(sent.any { ProtoMessage.parse(it)!!.let { c -> c.uint(1) == 17L && c.uint(2) == 0L } })
        var got: List<BandAlarm> = emptyList()
        s.onAlarms = { list, _ -> got = list }
        val details = XiaomiSchedule.details(BandAlarm(0, 6, 30, BandAlarm.Repeat.Weekly, BandAlarm.WEEKDAYS, true))
        val alarms = ProtoWriter().message(1, ProtoWriter().uint(1, 2).message(2, details)).uint(2, 10)
        s.onCommand(ProtoWriter().uint(1, 17).uint(2, 0).message(19, ProtoWriter().message(1, alarms)).toByteArray())
        assertEquals(listOf(BandAlarm(2, 6, 30, BandAlarm.Repeat.Weekly, 31, true)), got)

        sent.clear()
        s.saveAlarm(got[0].copy(enabled = false))
        val edit = ProtoMessage.parse(sent[0])!!
        assertEquals(2L, edit.uint(2))
        assertEquals(2L, edit.message(19)!!.message(3)!!.uint(1))
        assertEquals(0L, edit.message(19)!!.message(3)!!.message(2)!!.uint(5))
        assertEquals(0L, ProtoMessage.parse(sent[1])!!.uint(2))

        s.sendCalendar(listOf(BandCalendarEvent("Maç", "Saha", 100L, 200L, false, 15)))
        val cal = ProtoMessage.parse(sent.last())!!
        assertEquals(12L, cal.uint(1))
        val event = cal.message(14)!!.message(2)!!.message(1)!!
        assertEquals("Maç", event.string(1))
        assertEquals(15L, event.uint(7))
        s.sendCalendar(null)
        assertEquals(1L, ProtoMessage.parse(sent.last())!!.message(14)!!.message(2)!!.uint(2))
    }
}
