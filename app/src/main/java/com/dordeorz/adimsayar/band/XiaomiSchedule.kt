package com.dordeorz.adimsayar.band

/**
 * A band alarm. [id] is the band's own (from 1), 0 for a new one. [days] is a bit per weekday,
 * Monday = 1, used when [repeat] is [Repeat.Weekly].
 */
data class BandAlarm(val id: Int, val hour: Int, val minute: Int, val repeat: Repeat, val days: Int, val enabled: Boolean, val smart: Boolean = false) {
    enum class Repeat(val code: Int) { Once(0), Daily(1), Weekly(5) }

    companion object {
        const val WEEKDAYS = 31
    }
}

/** A calendar event for the band; times are epoch seconds. */
class BandCalendarEvent(val title: String, val location: String, val start: Long, val end: Long, val allDay: Boolean, val remindMinutes: Int)

/** Alarm (type 17) and calendar (type 12) commands, following Gadgetbridge's Xiaomi services. */
object XiaomiSchedule {

    const val TYPE = 17
    const val ALARMS_GET = 0
    const val ALARMS_CREATE = 1
    const val ALARMS_EDIT = 2
    const val ALARMS_DELETE = 4
    const val CALENDAR_TYPE = 12
    const val CALENDAR_SET = 1
    private const val SMART = 1
    private const val NORMAL = 2

    fun details(alarm: BandAlarm): ProtoWriter {
        val details = ProtoWriter()
            .message(2, ProtoWriter().uint(1, alarm.hour.toLong()).uint(2, alarm.minute.toLong()))
            .uint(3, alarm.repeat.code.toLong())
        if (alarm.repeat == BandAlarm.Repeat.Weekly) details.uint(4, alarm.days.toLong())
        return details.bool(5, alarm.enabled).uint(7, if (alarm.smart) SMART.toLong() else NORMAL.toLong())
    }

    fun create(alarm: BandAlarm): ProtoWriter = ProtoWriter().message(2, details(alarm))

    fun edit(alarm: BandAlarm): ProtoWriter = ProtoWriter().message(3, ProtoWriter().uint(1, alarm.id.toLong()).message(2, details(alarm)))

    fun delete(ids: List<Int>): ProtoWriter = ProtoWriter().message(5, ProtoWriter().apply { ids.forEach { uint(1, it.toLong()) } })

    /** Alarms from a get answer (Schedule.alarms), with the band's maximum. */
    fun alarms(schedule: ProtoMessage?): Pair<List<BandAlarm>, Int> {
        val alarms = schedule?.message(1) ?: return emptyList<BandAlarm>() to 0
        val list = alarms.messages(1).mapNotNull { a ->
            val id = a.uint(1)?.toInt() ?: return@mapNotNull null
            val d = a.message(2) ?: return@mapNotNull null
            val time = d.message(2)
            val repeat = BandAlarm.Repeat.entries.firstOrNull { it.code.toLong() == d.uint(3) } ?: BandAlarm.Repeat.Once
            BandAlarm(
                id = id,
                hour = time?.uint(1)?.toInt() ?: 0,
                minute = time?.uint(2)?.toInt() ?: 0,
                repeat = repeat,
                days = d.uint(4)?.toInt() ?: 0,
                enabled = d.uint(5) == 1L,
                smart = d.uint(7) == SMART.toLong(),
            )
        }
        return list.sortedBy { it.hour * 60 + it.minute } to (alarms.uint(2)?.toInt() ?: 0)
    }

    /** Calendar.calendarSync with the events, or switched off when [events] is null. */
    fun calendar(events: List<BandCalendarEvent>?): ProtoWriter {
        val sync = ProtoWriter()
        if (events == null) {
            sync.bool(2, true)
        } else {
            events.forEach { e ->
                sync.message(
                    1,
                    ProtoWriter()
                        .string(1, e.title)
                        .string(2, "")
                        .string(3, e.location)
                        .uint(4, e.start)
                        .uint(5, e.end)
                        .bool(6, e.allDay)
                        .uint(7, e.remindMinutes.toLong()),
                )
            }
        }
        return ProtoWriter().message(2, sync)
    }
}
